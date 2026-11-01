package pl.football.seeder.random;

import pl.football.seeder.entity.Appearance;
import pl.football.seeder.entity.Editor;
import pl.football.seeder.entity.Match;
import pl.football.seeder.entity.MatchEvent;
import pl.football.seeder.entity.Referee;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Plays one match: lineups, a minute-by-minute course of events, and what it changes afterwards.
 * Everything is worked out in memory first, because Match, Appearance and MatchEvent rows are inserted
 * as soon as they are saved. A player can only act while on the pitch, which is tracked as events pass.
 */
final class MatchSimulator {

    private static final String[] PERIODS = {"first_half", "second_half", "extra_time_1", "extra_time_2", "penalties"};
    private static final int SHOOTOUT = 4;
    private static final int[] FIRST_MINUTE = {1, 46, 91, 106};
    private static final int[] LAST_MINUTE = {45, 90, 105, 120};
    private static final int MIN_PLAYERS_ON_PITCH = 8;
    private static final int MIN_FIT_PLAYERS = 14;

    private static final List<int[]> FORMATIONS = List.of(new int[]{4, 3, 3}, new int[]{4, 2, 4}, new int[]{4, 4, 2},
            new int[]{3, 5, 2}, new int[]{3, 4, 3}, new int[]{5, 3, 2}, new int[]{4, 5, 1});

    private static final List<String> FOULS = List.of("Late challenge", "Reckless tackle", "Holding an opponent",
            "Tactical foul to stop a counter-attack", "Pulling an opponent's shirt", "Dangerous play", "Deliberate handball");
    private static final List<String> RED_CARD_REASONS = List.of("Serious foul play",
            "Denying an obvious goal-scoring opportunity", "Violent conduct");
    private static final List<String> DISALLOWED = List.of("Goal disallowed by VAR: offside",
            "Goal disallowed by VAR: foul in the build-up", "Goal disallowed by VAR: handball",
            "Goal disallowed: offside");
    private static final List<String> MISSES = List.of("Penalty saved by the goalkeeper", "Penalty hit the post",
            "Penalty shot over the crossbar");

    /** Declaration order settles events planned for the same minute: changes first. */
    private enum Kind {SUBSTITUTION, GOAL, DISALLOWED_GOAL, MISSED_PENALTY, YELLOW, RED, INJURY}

    private record Plan(int period, int minute, Integer stoppage, Kind kind, int side, int count) {
    }

    /** One team on the day. */
    private static final class Side {
        final TeamState team;
        final Map<PlayerState, String> roles = new LinkedHashMap<>();
        final Map<PlayerState, Short> shirts = new HashMap<>();
        final List<PlayerState> starters = new ArrayList<>();
        final List<PlayerState> bench = new ArrayList<>();
        final List<PlayerState> onPitch = new ArrayList<>();
        final Set<PlayerState> booked = new HashSet<>();
        int substitutions;
        int windows;

        Side(TeamState team) {
            this.team = team;
        }

        /** Three stoppages in regular time; half-time and extra time do not use one up. */
        boolean canUseWindow(boolean atHalfTime, boolean extraTime) {
            return atHalfTime || extraTime || windows < 3;
        }
    }

    /** An event worked out before anything is saved. {@code scoringSide}: 0 home, 1 away, -1 no goal. */
    private record Pending(Side side, PlayerState player, String type, int period, int minute, Integer stoppage,
                           LocalDateTime at, boolean cancelled, String description, int scoringSide) {
    }

    private final Rng rng;
    private final Catalog catalog;
    private final MasterData master;
    private final CareerSimulator careers;
    private final Officials officials;
    private final EditorDirectory editors;
    private final Db db;

    MatchSimulator(Rng rng, Catalog catalog, MasterData master, CareerSimulator careers, Officials officials,
                   EditorDirectory editors, Db db) {
        this.rng = rng;
        this.catalog = catalog;
        this.master = master;
        this.careers = careers;
        this.officials = officials;
        this.editors = editors;
        this.db = db;
    }

    void play(Fixture fixture) {
        Referee referee = officials.appoint(fixture);
        Editor editor = rng.pick(fixture.run.staff.matchEditors());
        if (fixture.forcedStatus != null) {
            saveUnplayed(fixture, referee, editor, fixture.forcedStatus);
        } else if (fixture.kickoff.isAfter(SeedTime.NOW)) {
            saveUnplayed(fixture, referee, editor, "scheduled");
        } else {
            new Course(fixture, referee, editor).play();
        }
    }

    private void saveUnplayed(Fixture fixture, Referee referee, Editor editor, String status) {
        Match match = new Match(fixture.stage.stage, fixture.home.team, fixture.away.team, fixture.stadium, referee,
                fixture.kickoff, status);
        LocalDateTime createdAt = fixture.kickoff.minusDays(21);
        db.save(match, editor, createdAt.isAfter(SeedTime.NOW) ? SeedTime.NOW.minusDays(1) : createdAt);
    }

    // ---------------------------------------------------------------- one match

    private final class Course {
        private final Fixture fixture;
        private final Referee referee;
        private final Editor editor;
        private final LocalDate date;
        private final Side home;
        private final Side away;
        private final Side[] sides;
        private final List<Pending> events = new ArrayList<>();
        /** Card and injury consequences, applied only if the match is over by the reference moment. */
        private final List<Runnable> aftermath = new ArrayList<>();
        private final LocalDateTime[] periodStart = new LocalDateTime[4];
        private final LocalDateTime[] periodEnd = new LocalDateTime[4];
        private final int substitutionLimit;
        private final int regularLimit;
        private LocalDateTime lastEventAt;
        private LocalDateTime finishedAt;
        private int homeShootout;
        private int awayShootout;
        private boolean extraTime;

        Course(Fixture fixture, Referee referee, Editor editor) {
            this.fixture = fixture;
            this.referee = referee;
            this.editor = editor;
            date = fixture.kickoff.toLocalDate();
            home = new Side(fixture.home);
            away = new Side(fixture.away);
            sides = new Side[]{home, away};
            lastEventAt = fixture.kickoff;
            substitutionLimit = fixture.run.edition.getSubstitutionLimit();
            // A sixth change, where the edition allows one, is only for extra time.
            regularLimit = Math.min(substitutionLimit, 5);
        }

        void play() {
            pickSheet(home);
            pickSheet(away);
            drawCourseOfPlay();
            persist();
        }

        // ------------------------------------------------------------ lineups

        private void pickSheet(Side side) {
            List<PlayerState> pool = side.team.national
                    ? new ArrayList<>(fixture.run.squads.get(side.team))
                    : side.team.membersAt(date);
            List<PlayerState> fit = new ArrayList<>();
            for (PlayerState player : pool) {
                if (serveSuspension(player)) {
                    continue;
                }
                if (player.isFit(date) && (side.team.national || !player.isCalledUp(date))) {
                    fit.add(player);
                }
            }
            topUp(side, fit);

            Map<PlayerState, Double> score = new HashMap<>();
            fit.forEach(p -> score.put(p, p.quality + rng.gaussian(0, 0.7)));
            fit.sort(Comparator.comparingDouble(p -> -score.get(p)));

            int[] shape = rng.pick(FORMATIONS);
            take(side, fit, CareerSimulator.GK, 1, true);
            take(side, fit, CareerSimulator.DF, shape[0], true);
            take(side, fit, CareerSimulator.MF, shape[1], true);
            take(side, fit, CareerSimulator.FW, shape[2], true);
            for (PlayerState player : new ArrayList<>(fit)) {
                if (side.starters.size() == 11) {
                    break;
                }
                if (!player.position.equals(CareerSimulator.GK)) {
                    addToSheet(side, player, roleForShortfall(side), true);
                    fit.remove(player);
                }
            }
            take(side, fit, CareerSimulator.GK, 1, false);
            int benchSize = fixture.run.competition.bench();
            for (PlayerState player : new ArrayList<>(fit)) {
                if (side.bench.size() >= benchSize) {
                    break;
                }
                addToSheet(side, player, player.position, false);
                fit.remove(player);
            }
            side.onPitch.addAll(side.starters);
            assignShirts(side);
            side.roles.keySet().forEach(player -> player.lastMatch = date);
        }

        /** True if the player sits this match out, which also counts one match off the ban. */
        private boolean serveSuspension(PlayerState player) {
            Map<PlayerState, Integer> suspensions = fixture.run.suspensions;
            Integer matchesLeft = suspensions.get(player);
            if (matchesLeft == null) {
                return false;
            }
            if (matchesLeft <= 1) {
                suspensions.remove(player);
            } else {
                suspensions.put(player, matchesLeft - 1);
            }
            return true;
        }

        /** Squads are big enough that this is rare: injuries and bans coming together. */
        private void topUp(Side side, List<PlayerState> fit) {
            if (fit.stream().noneMatch(p -> p.position.equals(CareerSimulator.GK))) {
                fit.add(emergency(side, CareerSimulator.GK));
            }
            while (fit.size() < MIN_FIT_PLAYERS) {
                fit.add(emergency(side, rng.pick(List.of(CareerSimulator.DF, CareerSimulator.MF, CareerSimulator.FW))));
            }
        }

        private PlayerState emergency(Side side, String position) {
            Editor recordedBy = editors.masterDataEditor();
            if (side.team.national) {
                PlayerState player = careers.emergencyCallUp(side.team, position, date, db, recordedBy);
                fixture.run.squads.get(side.team).add(player);
                player.callUps.add(new PlayerState.CallUp(date, fixture.run.plannedEnd));
                return player;
            }
            return careers.promoteFromAcademy(side.team, position, date, db, recordedBy);
        }

        private void take(Side side, List<PlayerState> fit, String position, int count, boolean starting) {
            int taken = 0;
            for (PlayerState player : new ArrayList<>(fit)) {
                if (taken < count && player.position.equals(position)) {
                    addToSheet(side, player, position, starting);
                    fit.remove(player);
                    taken++;
                }
            }
        }

        private void addToSheet(Side side, PlayerState player, String role, boolean starting) {
            side.roles.put(player, role);
            (starting ? side.starters : side.bench).add(player);
        }

        private String roleForShortfall(Side side) {
            long defenders = side.roles.values().stream().filter(CareerSimulator.DF::equals).count();
            return defenders < 3 ? CareerSimulator.DF : CareerSimulator.MF;
        }

        /** A shirt stays with its player while he is at the team, and nobody there can take it meanwhile. */
        private void assignShirts(Side side) {
            List<PlayerState> colleagues = side.team.national ? fixture.run.squads.get(side.team) : side.team.membersAt(date);
            Set<PlayerState> current = new HashSet<>(colleagues);
            Set<Short> taken = new HashSet<>();
            side.team.shirts.forEach((player, shirt) -> {
                if (current.contains(player)) {
                    taken.add(shirt);
                }
            });
            Set<Short> usedToday = new HashSet<>();
            for (PlayerState player : side.roles.keySet()) {
                Short shirt = side.team.shirts.get(player);
                if (shirt == null || usedToday.contains(shirt)) {
                    shirt = freeShirt(player, taken, usedToday);
                    side.team.shirts.put(player, shirt);
                    taken.add(shirt);
                }
                usedToday.add(shirt);
                side.shirts.put(player, shirt);
            }
        }

        private Short freeShirt(PlayerState player, Set<Short> taken, Set<Short> usedToday) {
            boolean keeper = player.position.equals(CareerSimulator.GK);
            for (short number = 1; number < 100; number++) {
                boolean suitsPosition = keeper ? number == 1 || number >= 12 : number != 1;
                if (suitsPosition && !taken.contains(number) && !usedToday.contains(number)) {
                    return number;
                }
            }
            throw new IllegalStateException("No shirt left at " + fixture.home);
        }

        // ------------------------------------------------------------ the course of play

        private void drawCourseOfPlay() {
            double homeForm = fixture.run.form.get(home.team);
            double awayForm = fixture.run.form.get(away.team);
            double homeRate = (fixture.neutral ? 1.32 : 1.55) * homeForm / Math.sqrt(awayForm);
            double awayRate = (fixture.neutral ? 1.32 : 1.15) * awayForm / Math.sqrt(homeForm);
            int homeGoals = rng.poisson(homeRate);
            int awayGoals = rng.poisson(awayRate);

            int firstAdded = rng.between(1, 5);
            int secondAdded = rng.between(2, 8);
            periodStart[0] = fixture.kickoff;
            periodEnd[0] = fixture.kickoff.plusMinutes(45L + firstAdded);
            periodStart[1] = periodEnd[0].plusMinutes(15);
            periodEnd[1] = periodStart[1].plusMinutes(45L + secondAdded);

            List<Plan> plans = new ArrayList<>();
            for (int i = 0; i < homeGoals; i++) {
                plans.add(timedInRegularTime(Kind.GOAL, 0, 0.45, firstAdded, secondAdded));
            }
            for (int i = 0; i < awayGoals; i++) {
                plans.add(timedInRegularTime(Kind.GOAL, 1, 0.45, firstAdded, secondAdded));
            }
            for (int s = 0; s < 2; s++) {
                planDiscipline(plans, s, firstAdded, secondAdded);
                planSubstitutions(plans, s);
            }

            boolean level = false;
            if (fixture.decidesTie()) {
                boolean homeIsFirst = fixture.home == fixture.tie.first;
                int first = fixture.tie.firstGoals + (homeIsFirst ? homeGoals : awayGoals);
                int second = fixture.tie.secondGoals + (homeIsFirst ? awayGoals : homeGoals);
                level = first == second;
            }
            int extraHome = 0;
            int extraAway = 0;
            if (level && fixture.tie.extraTime) {
                extraTime = true;
                extraHome = rng.poisson(homeRate * 0.26);
                extraAway = rng.poisson(awayRate * 0.26);
                planExtraTime(plans, extraHome, extraAway);
            }

            plans.sort(Comparator.comparingInt(Plan::period).thenComparingInt(Plan::minute)
                    .thenComparingInt(p -> p.stoppage() == null ? 0 : p.stoppage())
                    .thenComparingInt(p -> p.kind().ordinal()));
            plans.forEach(this::carryOut);

            finishedAt = extraTime ? periodEnd[3] : periodEnd[1];
            if (level && extraHome == extraAway) {
                finishedAt = playShootout(finishedAt.plusMinutes(5));
            }
        }

        private Plan timedInRegularTime(Kind kind, int side, double firstHalfChance, int firstAdded, int secondAdded) {
            int period = rng.chance(firstHalfChance) ? 0 : 1;
            int added = period == 0 ? firstAdded : secondAdded;
            // About one event in twenty falls in added time.
            if (rng.chance(0.05)) {
                return new Plan(period, LAST_MINUTE[period], rng.between(1, added), kind, side, 1);
            }
            return new Plan(period, rng.between(FIRST_MINUTE[period], LAST_MINUTE[period]), null, kind, side, 1);
        }

        private Plan inExtraTime(Kind kind, int side) {
            int period = rng.chance(0.5) ? 2 : 3;
            return new Plan(period, rng.between(FIRST_MINUTE[period], LAST_MINUTE[period]), null, kind, side, 1);
        }

        private void planDiscipline(List<Plan> plans, int s, int firstAdded, int secondAdded) {
            for (int i = rng.poisson(1.9); i > 0; i--) {
                plans.add(timedInRegularTime(Kind.YELLOW, s, 0.38, firstAdded, secondAdded));
            }
            if (rng.chance(0.035)) {
                plans.add(timedInRegularTime(Kind.RED, s, 0.3, firstAdded, secondAdded));
            }
            if (rng.chance(0.09)) {
                plans.add(timedInRegularTime(Kind.DISALLOWED_GOAL, s, 0.5, firstAdded, secondAdded));
            }
            if (rng.chance(0.035)) {
                plans.add(timedInRegularTime(Kind.MISSED_PENALTY, s, 0.5, firstAdded, secondAdded));
            }
            if (rng.chance(0.07)) {
                plans.add(timedInRegularTime(Kind.INJURY, s, 0.4, firstAdded, secondAdded));
            }
        }

        /** Most teams use most of their changes, over up to three stoppages plus half-time. */
        private void planSubstitutions(List<Plan> plans, int s) {
            int wanted = Math.min(regularLimit, rng.weighted(List.of(2, 3, 4, 5), n -> n == 2 ? 1.0 : n == 3 ? 3.0 : 4.0));
            if (rng.chance(0.3)) {
                plans.add(new Plan(1, 46, null, Kind.SUBSTITUTION, s, 1));
                wanted--;
            }
            int windows = Math.min(wanted, rng.between(1, 3));
            int minute = rng.between(55, 66);
            for (int w = 0; w < windows; w++) {
                int count = w == windows - 1 ? wanted : rng.between(1, wanted - (windows - w - 1));
                plans.add(new Plan(1, Math.min(minute, 89), null, Kind.SUBSTITUTION, s, count));
                wanted -= count;
                minute += rng.between(8, 14);
            }
        }

        private void planExtraTime(List<Plan> plans, int extraHome, int extraAway) {
            periodStart[2] = periodEnd[1].plusMinutes(5);
            periodEnd[2] = periodStart[2].plusMinutes(15L + rng.between(0, 2));
            periodStart[3] = periodEnd[2].plusMinutes(1);
            periodEnd[3] = periodStart[3].plusMinutes(15L + rng.between(0, 3));
            for (int i = 0; i < extraHome; i++) {
                plans.add(inExtraTime(Kind.GOAL, 0));
            }
            for (int i = 0; i < extraAway; i++) {
                plans.add(inExtraTime(Kind.GOAL, 1));
            }
            for (int s = 0; s < 2; s++) {
                for (int i = rng.poisson(0.6); i > 0; i--) {
                    plans.add(inExtraTime(Kind.YELLOW, s));
                }
                plans.add(new Plan(2, rng.between(91, 100), null, Kind.SUBSTITUTION, s, rng.between(1, 2)));
            }
        }

        private void carryOut(Plan plan) {
            Side side = sides[plan.side()];
            Side other = sides[1 - plan.side()];
            switch (plan.kind()) {
                case GOAL -> carryOutGoal(plan, side, other);
                case DISALLOWED_GOAL -> add(side, pickAttacker(side), "goal", plan, true, rng.pick(DISALLOWED), -1);
                case MISSED_PENALTY -> {
                    add(other, pickDefender(other), "foul", plan, false, "Foul in the penalty area", -1);
                    add(side, pickPenaltyTaker(side), "penalty_missed", plan, false, rng.pick(MISSES), -1);
                }
                case YELLOW -> carryOutYellow(plan, side);
                case RED -> carryOutRed(plan, side);
                case SUBSTITUTION -> carryOutSubstitutions(plan, side);
                case INJURY -> carryOutInjury(plan, side);
            }
        }

        private void carryOutGoal(Plan plan, Side side, Side other) {
            int sideIndex = side == home ? 0 : 1;
            int roll = rng.between(1, 100);
            if (roll <= 3) {
                add(other, pickDefender(other), "own_goal", plan, false, null, sideIndex);
            } else if (roll <= 13) {
                add(other, pickDefender(other), "foul", plan, false, "Foul in the penalty area", -1);
                add(side, pickPenaltyTaker(side), "penalty_goal", plan, false, null, sideIndex);
            } else {
                PlayerState scorer = pickAttacker(side);
                add(side, scorer, "goal", plan, false, null, sideIndex);
                List<PlayerState> others = side.onPitch.stream().filter(p -> p != scorer).toList();
                if (rng.chance(0.72)) {
                    add(side, rng.weighted(others, p -> role(side, p).equals(CareerSimulator.GK) ? 0.05 : 1.0),
                            "assist", plan, false, null, -1);
                }
            }
        }

        private void carryOutYellow(Plan plan, Side side) {
            // Booked players tread carefully, and a team already short keeps its men on the pitch.
            boolean protectBooked = side.onPitch.size() <= MIN_PLAYERS_ON_PITCH || rng.chance(0.85);
            List<PlayerState> options = protectBooked
                    ? side.onPitch.stream().filter(p -> !side.booked.contains(p)).toList()
                    : side.onPitch;
            if (options.isEmpty()) {
                return;
            }
            PlayerState player = rng.weighted(options, p -> switch (role(side, p)) {
                case CareerSimulator.DF -> 1.4;
                case CareerSimulator.MF -> 1.2;
                case CareerSimulator.FW -> 0.7;
                default -> 0.15;
            });
            add(side, player, "foul", plan, false, rng.pick(FOULS), -1);
            add(side, player, "yellow_card", plan, false, null, -1);
            if (side.booked.add(player)) {
                aftermath.add(() -> countYellowCard(player));
            } else {
                add(side, player, "red_card", plan, false, "Second yellow card", -1);
                sendOff(side, player, plan, 1);
            }
        }

        private void carryOutRed(Plan plan, Side side) {
            if (side.onPitch.size() > MIN_PLAYERS_ON_PITCH) {
                PlayerState player = rng.pick(side.onPitch);
                add(side, player, "red_card", plan, false, rng.pick(RED_CARD_REASONS), -1);
                sendOff(side, player, plan, rng.chance(0.25) ? 3 : 1);
            }
        }

        private void sendOff(Side side, PlayerState player, Plan plan, int matches) {
            boolean keeper = role(side, player).equals(CareerSimulator.GK);
            side.onPitch.remove(player);
            aftermath.add(() -> fixture.run.suspensions.merge(player, matches, Integer::sum));
            // A replacement keeper comes on at the cost of an outfield player and a substitution.
            if (keeper && side.substitutions < limitAt(plan) && side.canUseWindow(false, plan.period() >= 2)) {
                PlayerState reserve = benchKeeper(side);
                PlayerState out = pickSubstituteOff(side);
                if (reserve != null && out != null) {
                    substitute(side, out, reserve, plan, "Replacement goalkeeper after a red card");
                    useWindow(side, plan);
                }
            }
        }

        private void carryOutSubstitutions(Plan plan, Side side) {
            boolean halfTime = plan.period() == 1 && plan.minute() == 46;
            if (!side.canUseWindow(halfTime, plan.period() >= 2)) {
                return;
            }
            int made = 0;
            for (int i = 0; i < plan.count() && side.substitutions < limitAt(plan); i++) {
                PlayerState out = pickSubstituteOff(side);
                PlayerState in = pickSubstituteOn(side, out);
                if (out == null || in == null) {
                    break;
                }
                substitute(side, out, in, plan, null);
                made++;
            }
            if (made > 0 && !halfTime) {
                useWindow(side, plan);
            }
        }

        private void useWindow(Side side, Plan plan) {
            if (plan.period() < 2) {
                side.windows++;
            }
        }

        private int limitAt(Plan plan) {
            return plan.period() >= 2 ? substitutionLimit : regularLimit;
        }

        private void substitute(Side side, PlayerState out, PlayerState in, Plan plan, String description) {
            add(side, out, "substitution_off", plan, false, description, -1);
            add(side, in, "substitution_on", plan, false, description, -1);
            side.onPitch.remove(out);
            side.bench.remove(in);
            side.onPitch.add(in);
            side.substitutions++;
        }

        /** A player hurt in the match leaves if a change is available; otherwise the injury never happened. */
        private void carryOutInjury(Plan plan, Side side) {
            if (side.substitutions >= limitAt(plan) || !side.canUseWindow(false, plan.period() >= 2)) {
                return;
            }
            PlayerState hurt = rng.pick(side.onPitch);
            PlayerState in = role(side, hurt).equals(CareerSimulator.GK) ? benchKeeper(side) : pickSubstituteOn(side, hurt);
            if (in == null) {
                return;
            }
            Catalog.InjuryDef def = rng.weighted(catalog.injuries, Catalog.InjuryDef::weight);
            substitute(side, hurt, in, plan, "Injury: " + def.description());
            useWindow(side, plan);
            LocalDate end = date.plusDays(rng.between(def.minDays(), def.maxDays()));
            aftermath.add(() -> hurt.addMatchInjury(new PlayerState.InjuryRecord(date, end, def.description())));
        }

        private PlayerState benchKeeper(Side side) {
            return side.bench.stream().filter(p -> role(side, p).equals(CareerSimulator.GK)).findFirst().orElse(null);
        }

        private PlayerState pickSubstituteOff(Side side) {
            List<PlayerState> outfield = side.onPitch.stream().filter(p -> !role(side, p).equals(CareerSimulator.GK)).toList();
            if (outfield.isEmpty()) {
                return null;
            }
            return rng.weighted(outfield, p -> switch (role(side, p)) {
                case CareerSimulator.FW -> 2.0;
                case CareerSimulator.MF -> 1.8;
                default -> 1.0;
            });
        }

        private PlayerState pickSubstituteOn(Side side, PlayerState out) {
            if (out == null) {
                return null;
            }
            List<PlayerState> outfield = side.bench.stream().filter(p -> !role(side, p).equals(CareerSimulator.GK)).toList();
            List<PlayerState> sameRole = outfield.stream().filter(p -> role(side, p).equals(role(side, out))).toList();
            if (!sameRole.isEmpty()) {
                return rng.pick(sameRole);
            }
            return outfield.isEmpty() ? null : rng.pick(outfield);
        }

        private PlayerState pickAttacker(Side side) {
            return rng.weighted(side.onPitch, p -> switch (role(side, p)) {
                case CareerSimulator.FW -> 5.0;
                case CareerSimulator.MF -> 1.8;
                case CareerSimulator.DF -> 0.55;
                default -> 0.002;
            });
        }

        private PlayerState pickDefender(Side side) {
            return rng.weighted(side.onPitch, p -> switch (role(side, p)) {
                case CareerSimulator.DF -> 5.0;
                case CareerSimulator.MF -> 2.0;
                case CareerSimulator.FW -> 0.6;
                default -> 0.3;
            });
        }

        private PlayerState pickPenaltyTaker(Side side) {
            return rng.weighted(side.onPitch, p -> switch (role(side, p)) {
                case CareerSimulator.FW -> 6.0;
                case CareerSimulator.MF -> 3.0;
                case CareerSimulator.DF -> 0.5;
                default -> 0.01;
            });
        }

        private String role(Side side, PlayerState player) {
            return side.roles.get(player);
        }

        /** The moment on the clock. Events never go backwards, whatever seconds they are given. */
        private LocalDateTime nextMoment(Plan plan) {
            int offset = plan.minute() - FIRST_MINUTE[plan.period()] + (plan.stoppage() == null ? 0 : plan.stoppage());
            LocalDateTime wanted = periodStart[plan.period()].plusMinutes(offset).plusSeconds(rng.between(0, 50));
            LocalDateTime earliest = lastEventAt.plusSeconds(1);
            lastEventAt = wanted.isBefore(earliest) ? earliest : wanted;
            return lastEventAt;
        }

        private void add(Side side, PlayerState player, String type, Plan plan, boolean cancelled, String description, int scoringSide) {
            events.add(new Pending(side, player, type, plan.period(), plan.minute(), plan.stoppage(), nextMoment(plan),
                    cancelled, description, scoringSide));
        }

        /** Five kicks each, then sudden death, stopping as soon as one team cannot be caught. */
        private LocalDateTime playShootout(LocalDateTime start) {
            List<PlayerState> homeTakers = shootoutOrder(home);
            List<PlayerState> awayTakers = shootoutOrder(away);
            int homeKicks = 0;
            int awayKicks = 0;
            int minute = extraTime ? 120 : 90;
            LocalDateTime at = start;
            while (true) {
                boolean homeTurn = homeKicks == awayKicks;
                Side side = homeTurn ? home : away;
                List<PlayerState> takers = homeTurn ? homeTakers : awayTakers;
                PlayerState taker = takers.get((homeTurn ? homeKicks : awayKicks) % takers.size());
                boolean scored = rng.chance(0.76);
                at = at.plusMinutes(1);
                events.add(new Pending(side, taker, scored ? "penalty_goal" : "penalty_missed", SHOOTOUT, minute, null, at,
                        false, scored ? null : rng.pick(MISSES), -1));
                if (homeTurn) {
                    homeKicks++;
                    homeShootout += scored ? 1 : 0;
                } else {
                    awayKicks++;
                    awayShootout += scored ? 1 : 0;
                }
                if (isShootoutDecided(homeKicks, awayKicks)) {
                    return at.plusMinutes(1);
                }
            }
        }

        private boolean isShootoutDecided(int homeKicks, int awayKicks) {
            if (homeKicks < 5 || awayKicks < 5) {
                int homeLeft = 5 - homeKicks;
                int awayLeft = 5 - awayKicks;
                return homeShootout > awayShootout + awayLeft || awayShootout > homeShootout + homeLeft;
            }
            return homeKicks == awayKicks && homeShootout != awayShootout;
        }

        private List<PlayerState> shootoutOrder(Side side) {
            List<PlayerState> order = new ArrayList<>(side.onPitch);
            order.sort(Comparator.comparingInt(p -> switch (role(side, p)) {
                case CareerSimulator.FW -> 0;
                case CareerSimulator.MF -> 1;
                case CareerSimulator.DF -> 2;
                default -> 3;
            }));
            return order;
        }

        // ------------------------------------------------------------ persistence and consequences

        private void persist() {
            boolean finished = !finishedAt.isAfter(SeedTime.NOW);
            List<Pending> visible = events.stream().filter(e -> !e.at().isAfter(SeedTime.NOW)).toList();
            int homeGoals = (int) visible.stream().filter(e -> e.scoringSide() == 0).count();
            int awayGoals = (int) visible.stream().filter(e -> e.scoringSide() == 1).count();

            boolean guest = finished && rng.chance(0.01);
            Editor actingEditor = guest ? editors.pickGuestEditor() : editor;
            Match match = new Match(fixture.stage.stage, fixture.home.team, fixture.away.team, fixture.stadium, referee,
                    fixture.kickoff, finished ? "finished" : "in_progress");
            fillTimes(match, finished);
            match.setHomeGoals((short) homeGoals);
            match.setAwayGoals((short) awayGoals);
            Fixture.Tie tie = fixture.tie;
            if (finished && tie != null) {
                boolean homeIsFirst = fixture.home == tie.first;
                tie.firstGoals += homeIsFirst ? homeGoals : awayGoals;
                tie.secondGoals += homeIsFirst ? awayGoals : homeGoals;
                if (fixture.decidesTie()) {
                    decideTie(match, tie);
                }
            }
            match.setUpdatedAt(finished ? finishedAt.plusMinutes(rng.between(5, 40)) : SeedTime.NOW);
            db.save(match, actingEditor, fixture.kickoff.minusDays(rng.between(14, 30)));
            if (guest) {
                editors.grantMatch(actingEditor, match, fixture.run.title + ": " + fixture.home.name + " v " + fixture.away.name,
                        fixture.run.staff.administrator());
            }

            Map<PlayerState, Appearance> appearances = saveAppearances(match, actingEditor);
            int sequence = 1;
            for (Pending event : visible) {
                MatchEvent row = new MatchEvent(appearances.get(event.player()), master.eventTypes.get(event.type()),
                        PERIODS[event.period()], (short) event.minute(), sequence++, event.cancelled());
                row.setStoppageMinute(event.stoppage() == null ? null : event.stoppage().shortValue());
                row.setRecordedAt(event.at());
                row.setDescription(event.description());
                db.save(row, actingEditor, event.at().plusSeconds(rng.between(5, 60)));
            }

            if (finished) {
                aftermath.forEach(Runnable::run);
                fixture.run.recordResult(fixture.stage, fixture.home, fixture.away, homeGoals, awayGoals);
                if (fixture.decidesTie()) {
                    tie.onDecided.accept(tie);
                }
                fixture.round.matchFinished();
            }
        }

        private Map<PlayerState, Appearance> saveAppearances(Match match, Editor actingEditor) {
            Map<PlayerState, Appearance> appearances = new HashMap<>();
            LocalDateTime lineupsAt = fixture.kickoff.minusHours(1);
            for (Side side : sides) {
                for (PlayerState player : side.roles.keySet()) {
                    Appearance appearance = new Appearance(match, side.team.team, side.starters.contains(player));
                    appearance.setPlayer(player.player);
                    appearance.setShirtNumber(side.shirts.get(player));
                    appearance.setPosition(side.roles.get(player));
                    appearances.put(player, db.save(appearance, actingEditor, lineupsAt));
                }
                Appearance coach = new Appearance(match, side.team.team, false);
                coach.setCoach(side.team.coachAt(date));
                db.save(coach, actingEditor, lineupsAt);
            }
            return appearances;
        }

        private void fillTimes(Match match, boolean finished) {
            match.setFirstHalfStart(periodStart[0]);
            setIfPast(periodEnd[0], match::setFirstHalfEnd);
            setIfPast(periodStart[1], match::setSecondHalfStart);
            setIfPast(periodEnd[1], match::setSecondHalfEnd);
            if (extraTime) {
                setIfPast(periodStart[2], match::setExtraTimeFirstHalfStart);
                setIfPast(periodEnd[2], match::setExtraTimeFirstHalfEnd);
                setIfPast(periodStart[3], match::setExtraTimeSecondHalfStart);
                setIfPast(periodEnd[3], match::setExtraTimeSecondHalfEnd);
            }
            if (finished) {
                match.setFinishedAt(finishedAt);
            } else {
                match.setMatchPeriod(periodAtNow());
            }
        }

        private String periodAtNow() {
            LocalDateTime now = SeedTime.NOW;
            if (now.isBefore(periodEnd[0])) {
                return "first_half";
            }
            if (now.isBefore(periodStart[1])) {
                return "half_time";
            }
            if (now.isBefore(periodEnd[1]) || !extraTime) {
                return now.isBefore(periodEnd[1]) ? "second_half" : "penalties";
            }
            if (now.isBefore(periodEnd[2])) {
                return "extra_time_1";
            }
            return now.isBefore(periodEnd[3]) ? "extra_time_2" : "penalties";
        }

        private void setIfPast(LocalDateTime moment, Consumer<LocalDateTime> setter) {
            if (!moment.isAfter(SeedTime.NOW)) {
                setter.accept(moment);
            }
        }

        private void decideTie(Match match, Fixture.Tie tie) {
            boolean homeIsFirst = fixture.home == tie.first;
            boolean homeThrough;
            if (tie.firstGoals != tie.secondGoals) {
                homeThrough = homeIsFirst == tie.firstGoals > tie.secondGoals;
            } else {
                match.setHomePenalties((short) homeShootout);
                match.setAwayPenalties((short) awayShootout);
                homeThrough = homeShootout > awayShootout;
            }
            match.setAdvancingSide(homeThrough ? "home" : "away");
            tie.winner = homeThrough ? fixture.home : fixture.away;
            tie.loser = homeThrough ? fixture.away : fixture.home;
        }

        private void countYellowCard(PlayerState player) {
            int count = fixture.run.yellowCards.merge(player, 1, Integer::sum);
            if (count >= fixture.run.competition.yellowLimit()) {
                fixture.run.yellowCards.remove(player);
                fixture.run.suspensions.merge(player, 1, Integer::sum);
            }
        }
    }
}
