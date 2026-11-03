package pl.football.seeder.random;

import pl.football.seeder.entity.Appearance;
import pl.football.seeder.entity.Editor;
import pl.football.seeder.entity.Match;
import pl.football.seeder.entity.MatchEvent;
import pl.football.seeder.entity.Referee;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Stage;

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

import static pl.football.seeder.random.People.DF;
import static pl.football.seeder.random.People.FW;
import static pl.football.seeder.random.People.GK;
import static pl.football.seeder.random.People.MF;

/**
 * Plays and saves one match. First the plan: how many goals, cards and substitutions each side gets
 * and when. Then the plan is acted out in time order, so every event names a player who is on the
 * pitch at that moment. A match still running at "now" keeps only what happened so far.
 */
final class MatchEngine {

    /** bench: substitutes named in the squad; substitutions: allowed in the whole match. */
    record Rules(int bench, int substitutions) {
    }

    /**
     * decisive: the match must produce a winner (knockout match, second leg, final). firstLeg: goals
     * of this match's home and away team in the first leg, or null.
     */
    record Fixture(Stage stage, TeamInfo home, TeamInfo away, Stadium stadium, Referee referee, LocalDateTime kickoff,
                   int season, Rules rules, boolean decisive, boolean extraTime, int[] firstLeg,
                   Editors.Staff staff, String title, LocalDateTime createdAt, String forcedStatus) {
    }

    /** winner is set only for a finished decisive match. */
    record Result(boolean finished, int homeGoals, int awayGoals, TeamInfo winner) {
        static final Result NOT_PLAYED = new Result(false, 0, 0, null);
    }

    private enum Kind { GOAL, OWN_GOAL, PENALTY_GOAL, PENALTY_MISSED, CANCELLED_GOAL, YELLOW, RED, SUBSTITUTION }

    private record Moment(String period, int minute, Integer stoppage, LocalDateTime at) {
    }

    /** side: 0 home, 1 away. For goals it is the side that benefits, also for an own goal. */
    private record Plan(Moment moment, Kind kind, int side) {
    }

    /** side: the team of the player who did it. */
    private record Event(Moment moment, String type, int side, PlayerInfo player, boolean cancelled,
                         String description) {
    }

    private static final List<List<Integer>> FORMATIONS = List.of(List.of(4, 4, 2), List.of(4, 3, 3),
            List.of(3, 5, 2), List.of(4, 5, 1), List.of(5, 3, 2));

    // Who is likely to do what, by the role a player has on the pitch.
    private static final Map<String, Double> SCORER = Map.of(FW, 5.0, MF, 1.8, DF, 0.55, GK, 0.01);
    private static final Map<String, Double> ASSISTANT = Map.of(MF, 3.0, FW, 2.0, DF, 1.0, GK, 0.05);
    private static final Map<String, Double> OWN_GOAL = Map.of(DF, 3.0, MF, 1.0, GK, 0.5, FW, 0.3);
    private static final Map<String, Double> FOULER = Map.of(DF, 4.0, MF, 1.5, GK, 1.0, FW, 0.3);
    private static final Map<String, Double> BOOKED = Map.of(DF, 3.0, MF, 3.0, FW, 1.5, GK, 0.3);

    private final Rng rng;
    private final Db db;
    private final Dictionaries dict;
    private final Editors editors;

    MatchEngine(Rng rng, Db db, Dictionaries dict, Editors editors) {
        this.rng = rng;
        this.db = db;
        this.dict = dict;
        this.editors = editors;
    }

    Result play(Fixture f) {
        if (f.forcedStatus() == null && !f.kickoff().isAfter(SeedTime.NOW)) {
            return new Course(f).run();
        }
        // Future, postponed and cancelled matches have no line-ups and no events.
        String status = f.forcedStatus() != null ? f.forcedStatus() : "scheduled";
        Match match = new Match(f.stage(), f.home().team, f.away().team, f.stadium(), f.referee(), f.kickoff(), status);
        if (f.forcedStatus() != null) {
            match.setUpdatedAt(f.kickoff().minusHours(rng.between(2, 48)));
        }
        db.save(match, rng.pick(f.staff().matchEditors()), f.createdAt());
        return Result.NOT_PLAYED;
    }

    /** One team in one match. */
    private static final class Side {
        final TeamInfo team;
        final List<PlayerInfo> starters = new ArrayList<>();
        final List<PlayerInfo> substitutes = new ArrayList<>();
        final Map<PlayerInfo, String> roles = new LinkedHashMap<>();
        final List<PlayerInfo> onPitch = new ArrayList<>();
        final List<PlayerInfo> bench = new ArrayList<>();
        final Set<PlayerInfo> booked = new HashSet<>();
        int substitutionsLeft;

        Side(TeamInfo team) {
            this.team = team;
        }
    }

    private final class Course {
        final Fixture f;
        final LocalDate date;
        final Side[] sides = new Side[2];
        final List<Plan> plans = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        LocalDateTime h1Start, h1End, h2Start, h2End, et1Start, et1End, et2Start, et2End, finishedAt;
        int stoppage1, stoppage2, stoppageEt1, stoppageEt2;
        Integer homePenalties, awayPenalties;

        Course(Fixture f) {
            this.f = f;
            date = f.kickoff().toLocalDate();
            sides[0] = lineup(f.home());
            sides[1] = lineup(f.away());
        }

        Result run() {
            planRegularTime();
            boolean extraTime = false;
            boolean shootout = false;
            if (f.decisive() && tied(plannedGoals())) {
                if (f.extraTime()) {
                    planExtraTime();
                    extraTime = true;
                }
                shootout = tied(plannedGoals());
            }
            plans.sort(Comparator.comparing(p -> p.moment().at()));
            plans.forEach(this::act);
            finishedAt = extraTime ? et2End : h2End;
            if (shootout) {
                finishedAt = shootout(finishedAt.plusMinutes(5), extraTime ? 120 : 90);
            }
            return save();
        }

        // ------------------------------------------------------------ line-ups

        /** The eleven with the best form on the day, in a random formation, plus a bench with a goalkeeper. */
        Side lineup(TeamInfo team) {
            Side side = new Side(team);
            List<PlayerInfo> squad = team.roster(f.season());
            List<PlayerInfo> fit = squad.stream().filter(p -> !p.injuredOn(date)).toList();
            Map<PlayerInfo, Double> form = new HashMap<>();
            (fit.size() >= 14 ? fit : squad).forEach(p -> form.put(p, p.quality + rng.gaussian(0, 0.5)));
            List<PlayerInfo> ranked = form.keySet().stream()
                    .sorted(Comparator.comparingDouble((PlayerInfo p) -> form.get(p)).reversed()).toList();

            List<Integer> shape = rng.pick(FORMATIONS);
            List<String> roles = new ArrayList<>(List.of(GK));
            roles.addAll(java.util.Collections.nCopies(shape.get(0), DF));
            roles.addAll(java.util.Collections.nCopies(shape.get(1), MF));
            roles.addAll(java.util.Collections.nCopies(shape.get(2), FW));
            for (String role : roles) {
                // The best player of that position; if there is none, the best other outfield player.
                PlayerInfo player = ranked.stream().filter(p -> !side.roles.containsKey(p) && p.position.equals(role))
                        .findFirst()
                        .orElseGet(() -> ranked.stream().filter(p -> !side.roles.containsKey(p) && !p.position.equals(GK))
                                .findFirst().orElseThrow());
                side.starters.add(player);
                side.roles.put(player, role);
            }
            List<PlayerInfo> rest = ranked.stream().filter(p -> !side.roles.containsKey(p)).toList();
            rest.stream().filter(p -> p.position.equals(GK)).findFirst().ifPresent(side.substitutes::add);
            rest.stream().filter(p -> !side.substitutes.contains(p)).limit(f.rules().bench() - side.substitutes.size())
                    .forEach(side.substitutes::add);
            side.onPitch.addAll(side.starters);
            side.bench.addAll(side.substitutes);
            side.substitutionsLeft = f.rules().substitutions();
            return side;
        }

        // ------------------------------------------------------------ the plan

        void planRegularTime() {
            h1Start = f.kickoff();
            stoppage1 = rng.between(1, 5);
            h1End = h1Start.plusMinutes(45 + stoppage1);
            h2Start = h1End.plusMinutes(15);
            stoppage2 = rng.between(2, 8);
            h2End = h2Start.plusMinutes(45 + stoppage2);

            double[] rates = goalRates();
            for (int s = 0; s < 2; s++) {
                for (int i = rng.poisson(rates[s]); i > 0; i--) {
                    plans.add(new Plan(moment(half()), rng.chance(0.04) ? Kind.OWN_GOAL : Kind.GOAL, s));
                }
                if (rng.chance(0.17)) {
                    plans.add(new Plan(moment(half()), rng.chance(0.8) ? Kind.PENALTY_GOAL : Kind.PENALTY_MISSED, s));
                }
                for (int i = rng.poisson(1.9); i > 0; i--) {
                    plans.add(new Plan(moment(half()), Kind.YELLOW, s));
                }
                if (rng.chance(0.03)) {
                    plans.add(new Plan(moment(half()), Kind.RED, s));
                }
                planSubstitutions(s);
            }
            if (rng.chance(0.05)) {
                plans.add(new Plan(moment(half()), Kind.CANCELLED_GOAL, rng.between(0, 1)));
            }
        }

        /** 3-5 changes in 1-3 stoppages of the second half, sometimes the first one at half-time. */
        void planSubstitutions(int s) {
            int count = rng.between(3, Math.min(5, f.rules().substitutions()));
            int windows = rng.between(1, 3);
            Set<Integer> minutes = new HashSet<>();
            if (rng.chance(0.3)) {
                minutes.add(46);
            }
            while (minutes.size() < windows) {
                minutes.add(rng.between(55, 88));
            }
            List<Integer> sorted = minutes.stream().sorted().toList();
            for (int i = 0; i < count; i++) {
                int minute = sorted.get(i % sorted.size());
                plans.add(new Plan(new Moment("second_half", minute, null, h2Start.plusMinutes(minute - 46)),
                        Kind.SUBSTITUTION, s));
            }
        }

        void planExtraTime() {
            et1Start = h2End.plusMinutes(5);
            stoppageEt1 = rng.between(0, 2);
            et1End = et1Start.plusMinutes(15 + stoppageEt1);
            et2Start = et1End.plusMinutes(2);
            stoppageEt2 = rng.between(0, 3);
            et2End = et2Start.plusMinutes(15 + stoppageEt2);

            double[] rates = goalRates();
            for (int s = 0; s < 2; s++) {
                for (int i = rng.poisson(rates[s] * 0.3); i > 0; i--) {
                    plans.add(new Plan(moment(extraHalf()), Kind.GOAL, s));
                }
                for (int i = rng.poisson(0.4); i > 0; i--) {
                    plans.add(new Plan(moment(extraHalf()), Kind.YELLOW, s));
                }
                if (rng.chance(0.5)) {
                    int minute = rng.chance(0.5) ? 91 : 106;
                    LocalDateTime start = minute == 91 ? et1Start : et2Start;
                    plans.add(new Plan(new Moment(minute == 91 ? "extra_time_1" : "extra_time_2", minute, null, start),
                            Kind.SUBSTITUTION, s));
                }
            }
        }

        /** Average goals: the stronger side scores more, and a home side at its own ground a little more. */
        double[] goalRates() {
            double diff = f.home().strength(f.season()) - f.away().strength(f.season());
            boolean neutral = f.stadium() != f.home().stadium;
            return new double[]{(neutral ? 1.25 : 1.45) * Math.exp(0.5 * diff),
                    (neutral ? 1.25 : 1.05) * Math.exp(-0.5 * diff)};
        }

        String half() {
            return rng.chance(0.47) ? "first_half" : "second_half";
        }

        String extraHalf() {
            return rng.chance(0.5) ? "extra_time_1" : "extra_time_2";
        }

        /** A random moment in a period; sometimes in its added time, written as e.g. 45+2. */
        Moment moment(String period) {
            int from;
            int to;
            int stoppage;
            LocalDateTime start;
            switch (period) {
                case "first_half" -> { from = 1; to = 45; stoppage = stoppage1; start = h1Start; }
                case "second_half" -> { from = 46; to = 90; stoppage = stoppage2; start = h2Start; }
                case "extra_time_1" -> { from = 91; to = 105; stoppage = stoppageEt1; start = et1Start; }
                default -> { from = 106; to = 120; stoppage = stoppageEt2; start = et2Start; }
            }
            int seconds = rng.between(0, 59);
            if (stoppage > 0 && rng.chance(0.08)) {
                int added = rng.between(1, stoppage);
                return new Moment(period, to, added, start.plusMinutes(to - from + added).plusSeconds(seconds));
            }
            int minute = rng.between(from, to);
            return new Moment(period, minute, null, start.plusMinutes(minute - from).plusSeconds(seconds));
        }

        int[] plannedGoals() {
            int[] goals = new int[2];
            for (Plan plan : plans) {
                if (plan.kind() == Kind.GOAL || plan.kind() == Kind.OWN_GOAL || plan.kind() == Kind.PENALTY_GOAL) {
                    goals[plan.side()]++;
                }
            }
            return goals;
        }

        /** Level on the day, or on aggregate in a second leg. */
        boolean tied(int[] goals) {
            int[] leg = f.firstLeg() != null ? f.firstLeg() : new int[2];
            return goals[0] + leg[0] == goals[1] + leg[1];
        }

        // ------------------------------------------------------------ acting out the plan

        void act(Plan plan) {
            int s = plan.side();
            int other = 1 - s;
            Moment m = plan.moment();
            switch (plan.kind()) {
                case GOAL -> {
                    PlayerInfo scorer = pick(s, SCORER);
                    add(m, "goal", s, scorer, false, null);
                    List<PlayerInfo> mates = sides[s].onPitch.stream().filter(p -> p != scorer).toList();
                    if (!mates.isEmpty() && rng.chance(0.7)) {
                        add(m, "assist", s, rng.weighted(mates, p -> ASSISTANT.get(role(s, p))), false, null);
                    }
                }
                case CANCELLED_GOAL -> add(m, "goal", s, pick(s, SCORER), true, "Disallowed by VAR for offside");
                case OWN_GOAL -> add(m, "own_goal", other, pick(other, OWN_GOAL), false, null);
                case PENALTY_GOAL, PENALTY_MISSED -> {
                    add(m, "foul", other, pick(other, FOULER), false, "Foul in the penalty area");
                    add(m, plan.kind() == Kind.PENALTY_GOAL ? "penalty_goal" : "penalty_missed", s, pick(s, SCORER),
                            false, null);
                }
                case YELLOW -> {
                    PlayerInfo player = pick(s, BOOKED);
                    add(m, "yellow_card", s, player, false, null);
                    if (!sides[s].booked.add(player)) {
                        add(m, "red_card", s, player, false, "Second yellow card");
                        sides[s].onPitch.remove(player);
                    }
                }
                case RED -> {
                    PlayerInfo player = pick(s, BOOKED);
                    add(m, "red_card", s, player, false, "Serious foul play");
                    sides[s].onPitch.remove(player);
                }
                case SUBSTITUTION -> substitute(s, m);
            }
        }

        /** A substituted player does not come back, and a sent-off player is not replaced. */
        void substitute(int s, Moment m) {
            Side side = sides[s];
            List<PlayerInfo> off = side.onPitch.stream().filter(p -> !role(s, p).equals(GK)).toList();
            List<PlayerInfo> on = side.bench.stream().filter(p -> !p.position.equals(GK)).toList();
            if (side.substitutionsLeft == 0 || off.isEmpty() || on.isEmpty()) {
                return;
            }
            PlayerInfo leaving = rng.pick(off);
            PlayerInfo coming = rng.pick(on);
            side.onPitch.remove(leaving);
            side.onPitch.add(coming);
            side.bench.remove(coming);
            side.roles.put(coming, coming.position);
            side.substitutionsLeft--;
            add(m, "substitution_off", s, leaving, false, null);
            add(m, "substitution_on", s, coming, false, null);
        }

        /** Five kicks each, then sudden death; it stops as soon as the result is certain. Returns the end time. */
        LocalDateTime shootout(LocalDateTime start, int minute) {
            List<List<PlayerInfo>> takers = new ArrayList<>();
            for (int s = 0; s < 2; s++) {
                int side = s;
                takers.add(sides[s].onPitch.stream()
                        .sorted(Comparator.comparing((PlayerInfo p) -> role(side, p).equals(GK))
                                .thenComparing(Comparator.comparingDouble((PlayerInfo p) -> p.quality).reversed()))
                        .toList());
            }
            int[] scored = new int[2];
            int[] taken = new int[2];
            int kick = 0;
            kicks:
            for (int round = 0; ; round++) {
                for (int s = 0; s < 2; s++) {
                    PlayerInfo taker = takers.get(s).get(taken[s] % takers.get(s).size());
                    boolean goal = rng.chance(0.76);
                    Moment m = new Moment("penalties", minute, null, start.plusMinutes(kick).plusSeconds(rng.between(0, 59)));
                    events.add(new Event(m, goal ? "penalty_goal" : "penalty_missed", s, taker, false, null));
                    kick++;
                    taken[s]++;
                    scored[s] += goal ? 1 : 0;
                    if (round < 5 && (scored[0] > scored[1] + 5 - taken[1] || scored[1] > scored[0] + 5 - taken[0])) {
                        break kicks;
                    }
                }
                if (round >= 4 && scored[0] != scored[1]) {
                    break;
                }
            }
            homePenalties = scored[0];
            awayPenalties = scored[1];
            return start.plusMinutes(kick + 2);
        }

        PlayerInfo pick(int s, Map<String, Double> weights) {
            return rng.weighted(sides[s].onPitch, p -> weights.get(role(s, p)));
        }

        String role(int s, PlayerInfo player) {
            return sides[s].roles.get(player);
        }

        void add(Moment m, String type, int side, PlayerInfo player, boolean cancelled, String description) {
            events.add(new Event(m, type, side, player, cancelled, description));
        }

        // ------------------------------------------------------------ saving

        Result save() {
            boolean finished = !finishedAt.isAfter(SeedTime.NOW);
            List<Event> visible = events.stream().filter(e -> !e.moment().at().isAfter(SeedTime.NOW)).toList();
            int[] goals = new int[2];
            for (Event e : visible) {
                if (!e.cancelled() && !e.moment().period().equals("penalties")) {
                    switch (e.type()) {
                        case "goal", "penalty_goal" -> goals[e.side()]++;
                        case "own_goal" -> goals[1 - e.side()]++;
                        default -> { }
                    }
                }
            }

            Match match = new Match(f.stage(), f.home().team, f.away().team, f.stadium(), f.referee(), f.kickoff(),
                    finished ? "finished" : "in_progress");
            setIfPast(match::setFirstHalfStart, h1Start);
            setIfPast(match::setFirstHalfEnd, h1End);
            setIfPast(match::setSecondHalfStart, h2Start);
            setIfPast(match::setSecondHalfEnd, h2End);
            setIfPast(match::setExtraTimeFirstHalfStart, et1Start);
            setIfPast(match::setExtraTimeFirstHalfEnd, et1End);
            setIfPast(match::setExtraTimeSecondHalfStart, et2Start);
            setIfPast(match::setExtraTimeSecondHalfEnd, et2End);
            match.setHomeGoals((short) goals[0]);
            match.setAwayGoals((short) goals[1]);
            TeamInfo winner = null;
            if (finished) {
                match.setFinishedAt(finishedAt);
                if (homePenalties != null) {
                    match.setHomePenalties(homePenalties.shortValue());
                    match.setAwayPenalties(awayPenalties.shortValue());
                }
                if (f.decisive()) {
                    winner = winner(goals);
                    match.setAdvancingSide(winner == f.home() ? "home" : "away");
                }
                match.setUpdatedAt(finishedAt.plusMinutes(rng.between(5, 40)));
            } else {
                match.setMatchPeriod(periodAt(SeedTime.NOW));
                match.setUpdatedAt(SeedTime.NOW);
            }

            // About 1% of finished matches are edited by a guest trusted with that match only.
            boolean guest = finished && rng.chance(0.01);
            Editor editor = guest ? editors.guest() : rng.pick(f.staff().matchEditors());
            db.save(match, editor, f.createdAt());
            if (guest) {
                LocalDateTime grantedAt = f.kickoff().minusDays(rng.between(1, 4)).withHour(10).withMinute(0);
                editors.grantMatch(editor, match, f.title() + ": " + f.home().name() + " v " + f.away().name(),
                        f.staff().administrator(), grantedAt.isAfter(f.createdAt()) ? grantedAt : f.createdAt().plusHours(1));
            }

            LocalDateTime lineupsAt = f.kickoff().minusHours(1);
            Map<PlayerInfo, Appearance> appearances = new HashMap<>();
            for (int s = 0; s < 2; s++) {
                Side side = sides[s];
                for (PlayerInfo player : side.starters) {
                    appearances.put(player, appearance(match, side, player, true, side.roles.get(player), editor, lineupsAt));
                }
                for (PlayerInfo player : side.substitutes) {
                    appearances.put(player, appearance(match, side, player, false, player.position, editor, lineupsAt));
                }
                Appearance coach = new Appearance(match, side.team.team, false);
                coach.setCoach(side.team.coachAt(date));
                db.save(coach, editor, lineupsAt);
            }

            // An event is recorded a few seconds after it happens, but never after the match is over.
            LocalDateTime recordedLimit = finished ? finishedAt : SeedTime.NOW;
            int sequence = 1;
            for (Event e : visible) {
                MatchEvent row = new MatchEvent(appearances.get(e.player()), dict.eventTypes.get(e.type()),
                        e.moment().period(), (short) e.moment().minute(), sequence++, e.cancelled());
                if (e.moment().stoppage() != null) {
                    row.setStoppageMinute(e.moment().stoppage().shortValue());
                }
                row.setDescription(e.description());
                LocalDateTime recordedAt = e.moment().at().plusSeconds(rng.between(10, 90));
                recordedAt = recordedAt.isAfter(recordedLimit) ? recordedLimit : recordedAt;
                row.setRecordedAt(recordedAt);
                db.save(row, editor, recordedAt);
            }
            return new Result(finished, goals[0], goals[1], winner);
        }

        Appearance appearance(Match match, Side side, PlayerInfo player, boolean starting, String position,
                              Editor editor, LocalDateTime at) {
            Appearance appearance = new Appearance(match, side.team.team, starting);
            appearance.setPlayer(player.player);
            appearance.setShirtNumber(side.team.shirt(f.season(), player));
            appearance.setPosition(position);
            return db.save(appearance, editor, at);
        }

        /** The winner on aggregate, or by penalties when level. */
        TeamInfo winner(int[] goals) {
            int[] leg = f.firstLeg() != null ? f.firstLeg() : new int[2];
            int home = goals[0] + leg[0];
            int away = goals[1] + leg[1];
            if (home != away) {
                return home > away ? f.home() : f.away();
            }
            return homePenalties > awayPenalties ? f.home() : f.away();
        }

        String periodAt(LocalDateTime time) {
            if (time.isBefore(h1End)) {
                return "first_half";
            }
            if (time.isBefore(h2Start)) {
                return "half_time";
            }
            if (time.isBefore(h2End)) {
                return "second_half";
            }
            if (et1End != null && time.isBefore(et1End)) {
                return "extra_time_1";
            }
            if (et2End != null && time.isBefore(et2End)) {
                return "extra_time_2";
            }
            return "penalties";
        }

        void setIfPast(Consumer<LocalDateTime> setter, LocalDateTime time) {
            if (time != null && !time.isAfter(SeedTime.NOW)) {
                setter.accept(time);
            }
        }
    }
}
