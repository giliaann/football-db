package pl.football.seeder.random;

import pl.football.seeder.entity.Competition;
import pl.football.seeder.entity.Edition;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiPredicate;

/**
 * Knows how each format is played. It creates Competition, Edition and Stage rows, decides who takes
 * part, and queues fixtures on the {@link Simulation}. When a round finishes it draws the next one.
 */
final class EditionPlanner {

    private static final List<String> GROUP_LETTERS = List.of("A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L");
    private static final List<String> LEAGUES = List.of("laliga", "seriea", "ekstraklasa");
    private static final Map<String, List<String>> LEAGUE_DIVISIONS = Map.of(
            "laliga", List.of("laliga", "segunda"),
            "seriea", List.of("seriea", "serieb"),
            "ekstraklasa", List.of("ekstraklasa", "iliga"));
    private static final Map<String, String> CUP_OF_LEAGUE = Map.of("laliga", "copa", "seriea", "coppa", "ekstraklasa", "puchar");
    private static final Map<String, String[]> CUP_FINAL_VENUES = Map.of(
            "copa", new String[]{"Estadio de La Cartuja", "Seville"},
            "coppa", new String[]{"Stadio Olimpico", "Rome"},
            "puchar", new String[]{"PGE Narodowy", "Warsaw"});

    private static final List<LocalTime> SATURDAY_TIMES = times("15:00", "18:00", "20:45");
    private static final List<LocalTime> SUNDAY_TIMES = times("12:30", "15:00", "18:00", "20:45");
    private static final List<LocalTime> EVENING_TIMES = times("20:45");
    private static final List<LocalTime> MIDWEEK_TIMES = times("19:00", "21:00");
    private static final List<LocalTime> UEFA_TIMES = times("18:45", "21:00");
    private static final List<LocalTime> TOURNAMENT_TIMES = times("12:00", "14:00", "16:00", "18:00", "20:00", "22:00");
    private static final List<Integer> CUP_DAYS = List.of(2, 3, 1);

    /** One match per league is forced to kick off so that it is live at the reference moment. */
    private static final Map<String, LocalTime> LIVE_KICKOFF = Map.of(
            "laliga", LocalTime.of(18, 0), "seriea", LocalTime.of(17, 35), "ekstraklasa", LocalTime.of(16, 50));

    private final Rng rng;
    private final Catalog catalog;
    private final MasterData master;
    private final CareerSimulator careers;
    private final EditorDirectory editors;
    private final Db db;
    private final Simulation simulation;
    private final MatchSimulator matches;
    private final Schedule schedule = new Schedule();

    private final Map<String, Competition> competitions = new HashMap<>();
    private final Map<String, List<TeamState>> leagueMembers = new HashMap<>();
    private final Map<String, List<TeamState>> lowerDivisions = new HashMap<>();
    private final Map<String, EditionRun> lastLeagueRun = new HashMap<>();
    private final Map<TeamState, List<PlayerState>> lastNationalSquads = new LinkedHashMap<>();
    final List<EditionRun> runs = new ArrayList<>();

    private List<TeamState> uclThirdPlaced = List.of();

    EditionPlanner(Rng rng, Catalog catalog, MasterData master, CareerSimulator careers, EditorDirectory editors,
                   Db db, Simulation simulation, MatchSimulator matches) {
        this.rng = rng;
        this.catalog = catalog;
        this.master = master;
        this.careers = careers;
        this.editors = editors;
        this.db = db;
        this.simulation = simulation;
        this.matches = matches;
    }

    private static List<LocalTime> times(String... values) {
        return java.util.Arrays.stream(values).map(LocalTime::parse).toList();
    }

    Map<TeamState, List<PlayerState>> lastNationalSquads() {
        return lastNationalSquads;
    }

    /** Queues the start of every season and tournament. Each one is planned when its day arrives. */
    void queueEverything() {
        for (Catalog.CompetitionDef def : catalog.competitions) {
            competitions.put(def.key(), db.save(
                    new Competition(def.name(), def.format(), def.level(), master.region(def.region())),
                    editors.masterDataEditor(), SeedTime.MASTER_DATA_AT));
        }
        for (String league : LEAGUES) {
            List<String> divisions = LEAGUE_DIVISIONS.get(league);
            leagueMembers.put(league, careers.clubs.stream().filter(c -> c.division.equals(divisions.get(0))).toList());
            lowerDivisions.put(league, careers.clubs.stream().filter(c -> c.division.equals(divisions.get(1))).toList());
        }
        for (int year : SeedTime.SEASONS) {
            simulation.at(LocalDate.of(year, 7, 1).atStartOfDay(), () -> startSeason(year));
        }
        for (Catalog.EditionDef def : catalog.editions) {
            if (def.start() != null) {
                simulation.at(def.start().minusDays(4).atTime(0, 1), () -> startTournament(def));
            }
        }
    }

    // ---------------------------------------------------------------- club season

    private void startSeason(int year) {
        SeasonCalendar calendar = new SeasonCalendar(year);
        String season = SeedTime.seasonName(year);
        LEAGUES.forEach(this::applyPromotionAndRelegation);

        List<TeamState> ucl = new ArrayList<>();
        List<TeamState> uel = new ArrayList<>();
        chooseUefaEntrants(calendar, ucl, uel);
        Set<TeamState> uefaClubs = new HashSet<>(ucl);
        uefaClubs.addAll(uel);

        SeasonCalendar.CupWeeks cupWeeks = calendar.cupWeeks();
        Set<LocalDate> cupWeekSet = Set.of(cupWeeks.round32(), cupWeeks.round16(), cupWeeks.quarterFinals(),
                cupWeeks.semiFinalFirst(), cupWeeks.semiFinalSecond(), cupWeeks.fin());
        for (String league : LEAGUES) {
            String[] venue = CUP_FINAL_VENUES.get(CUP_OF_LEAGUE.get(league));
            schedule.reserveStadium(master.stadium(venue[0], venue[1]), cupWeeks.fin().plusDays(2));
        }
        reserveFinal("ucl", season);
        reserveFinal("uel", season);

        for (String league : LEAGUES) {
            startLeague(league, year, season, calendar, cupWeekSet, uefaClubs);
        }
        for (String league : LEAGUES) {
            startCup(CUP_OF_LEAGUE.get(league), league, season, cupWeeks);
        }
        startUefa("ucl", season, calendar, ucl);
        startUefa("uel", season, calendar, uel);
    }

    private void reserveFinal(String key, String season) {
        Catalog.EditionDef def = catalog.edition(key, season);
        if (!def.finalStadium().isEmpty()) {
            schedule.reserveStadium(master.stadium(def.finalStadium(), def.finalCity()), def.end());
        }
    }

    /** The bottom three go down and three clubs picked from the division below come up. */
    private void applyPromotionAndRelegation(String league) {
        EditionRun last = lastLeagueRun.get(league);
        if (last == null) {
            return;
        }
        List<TeamState> ranking = last.stages.getFirst().ranking();
        List<TeamState> relegated = ranking.subList(ranking.size() - 3, ranking.size());
        List<TeamState> lower = new ArrayList<>(lowerDivisions.get(league));
        List<TeamState> promoted = rng.sample(lower, 3);
        lower.removeAll(promoted);
        lower.addAll(relegated);
        List<TeamState> members = new ArrayList<>(leagueMembers.get(league));
        members.removeAll(relegated);
        members.addAll(promoted);
        leagueMembers.put(league, members);
        lowerDivisions.put(league, lower);
    }

    /** The league table decides the Spanish, Italian and Polish places; the rest of Europe is drawn. */
    private void chooseUefaEntrants(SeasonCalendar calendar, List<TeamState> ucl, List<TeamState> uel) {
        takeFromLeague("laliga", 4, 3, ucl, uel);
        takeFromLeague("seriea", 4, 3, ucl, uel);
        takeFromLeague("ekstraklasa", 1, 1, ucl, uel);
        int size = calendar.leaguePhaseFormat ? 36 : 32;
        // Sorted by tier, so the stronger clubs tend to reach the Champions League.
        List<TeamState> pool = new ArrayList<>(rng.shuffled(careers.clubs.stream().filter(c -> c.division.equals("europe")).toList()));
        pool.sort(Comparator.comparingInt(c -> c.tier));
        ucl.addAll(rng.sample(pool.subList(0, size), size - ucl.size()));
        pool.removeAll(ucl);
        uel.addAll(rng.sample(pool, size - uel.size()));
    }

    private void takeFromLeague(String league, int uclPlaces, int uelPlaces, List<TeamState> ucl, List<TeamState> uel) {
        EditionRun last = lastLeagueRun.get(league);
        List<TeamState> order;
        if (last != null) {
            order = new ArrayList<>(last.stages.getFirst().ranking());
            order.retainAll(leagueMembers.get(league));
        } else {
            order = new ArrayList<>(rng.shuffled(leagueMembers.get(league)));
            order.sort(Comparator.comparingInt(c -> c.tier));
        }
        ucl.addAll(order.subList(0, uclPlaces));
        uel.addAll(order.subList(uclPlaces, uclPlaces + uelPlaces));
    }

    private EditionRun newRun(String key, String season, LocalDate start, LocalDate end, String rules) {
        Catalog.CompetitionDef def = catalog.competitionByKey.get(key);
        Edition edition = new Edition(competitions.get(key), season, start, (short) def.substitutionLimit());
        boolean finished = !end.isAfter(SeedTime.TODAY);
        edition.setEndDate(finished ? end : null);
        edition.setAdvancementRules(rules);
        db.save(edition, editors.masterDataEditor(), start.minusDays(45).atTime(10, 0));
        EditorDirectory.EditionStaff staff = editors.staffFor(edition, competitions.get(key), finished ? end : null);
        EditionRun run = new EditionRun(def, edition, staff, end);
        runs.add(run);
        return run;
    }

    private EditionRun.StageRun newStage(EditionRun run, String name, String type) {
        short order = (short) (run.stages.size() + 1);
        Stage stage = new Stage(run.edition, name, type, order);
        db.save(stage, run.staff.administrator(), run.edition.getStartDate().minusDays(14).atTime(10, 0));
        EditionRun.StageRun stageRun = new EditionRun.StageRun(stage);
        run.stages.add(stageRun);
        return stageRun;
    }

    // ---------------------------------------------------------------- league

    private record Placed(Pairings.Pair pair, LocalDateTime kickoff) {
    }

    private void startLeague(String key, int year, String season, SeasonCalendar calendar, Set<LocalDate> cupWeeks,
                             Set<TeamState> uefaClubs) {
        List<TeamState> teams = rng.shuffled(leagueMembers.get(key));
        List<List<Pairings.Pair>> rounds = Pairings.doubleRoundRobin(teams);
        List<SeasonCalendar.RoundSlot> slots = calendar.leagueRounds(key, rounds.size(), cupWeeks);

        List<Placed> placed = new ArrayList<>();
        for (int r = 0; r < rounds.size(); r++) {
            SeasonCalendar.RoundSlot slot = slots.get(r);
            boolean first = true;
            for (Pairings.Pair pair : rng.shuffled(rounds.get(r))) {
                LocalDateTime kickoff;
                if (first && !slot.midweek() && slot.week().plusDays(6).equals(SeedTime.TODAY)) {
                    kickoff = SeedTime.TODAY.atTime(LIVE_KICKOFF.get(key));
                    schedule.book(pair.home(), pair.away(), pair.home().stadium, SeedTime.TODAY);
                } else {
                    kickoff = placeLeagueMatch(pair, slot, uefaClubs);
                }
                first = false;
                placed.add(new Placed(pair, kickoff));
            }
        }
        placed.sort(Comparator.comparing(Placed::kickoff));
        LocalDate start = placed.getFirst().kickoff().toLocalDate();
        LocalDate end = placed.getLast().kickoff().toLocalDate();
        EditionRun run = newRun(key, season, start, end, catalog.competitionByKey.get(key).rules());
        EditionRun.StageRun stage = newStage(run, "Regular season", "league");
        teams.forEach(team -> run.enrolInStage(stage, team, rng));
        lastLeagueRun.put(key, run);

        // Only the running season has matches left unplayed, so that older tables stay complete.
        List<Placed> candidates = year != SeedTime.SEASONS.getLast() ? List.of() : placed.stream().filter(p -> {
            LocalDate date = p.kickoff().toLocalDate();
            return !date.isBefore(LocalDate.of(2026, 9, 15)) && date.isBefore(LocalDate.of(2026, 10, 26));
        }).toList();
        Placed postponed = candidates.isEmpty() ? null : rng.pick(candidates);
        Placed cancelled = key.equals("ekstraklasa") && candidates.size() > 1
                ? rng.pick(candidates.stream().filter(p -> p != postponed).toList()) : null;

        Fixture.Round noNextRound = new Fixture.Round(() -> {
        });
        for (Placed item : placed) {
            noNextRound.add();
            Fixture fixture = new Fixture(run, stage, item.pair().home(), item.pair().away(), item.pair().home().stadium,
                    item.kickoff(), false, noNextRound, null, 1);
            fixture.forcedStatus = item == postponed ? "postponed" : item == cancelled ? "cancelled" : null;
            queue(fixture);
        }
    }

    private LocalDateTime placeLeagueMatch(Pairings.Pair pair, SeasonCalendar.RoundSlot slot, Set<TeamState> uefaClubs) {
        boolean european = uefaClubs.contains(pair.home()) || uefaClubs.contains(pair.away());
        LocalDate week = slot.week();
        List<LocalDate> days = new ArrayList<>();
        if (slot.midweek()) {
            days.addAll(rng.shuffled(List.of(week.plusDays(1), week.plusDays(2), week.plusDays(3))));
        } else {
            days.addAll(rng.shuffled(List.of(week.plusDays(5), week.plusDays(6))));
            // Friday and Monday games are for clubs with no European match next to them.
            if (!european) {
                List<LocalDate> edges = rng.shuffled(List.of(week.plusDays(4), week.plusDays(7)));
                if (rng.chance(0.3)) {
                    days.addFirst(edges.get(0));
                    days.addLast(edges.get(1));
                } else {
                    days.addAll(edges);
                }
            }
        }
        for (LocalDate day : days) {
            if (fits(pair.home(), pair.away(), pair.home().stadium, day)) {
                schedule.book(pair.home(), pair.away(), pair.home().stadium, day);
                return day.atTime(rng.pick(timesFor(day)));
            }
        }
        throw new IllegalStateException("No date for " + pair.home() + " v " + pair.away() + " in week " + week);
    }

    private static List<LocalTime> timesFor(LocalDate day) {
        return switch (day.getDayOfWeek()) {
            case SATURDAY -> SATURDAY_TIMES;
            case SUNDAY -> SUNDAY_TIMES;
            case FRIDAY, MONDAY -> EVENING_TIMES;
            default -> MIDWEEK_TIMES;
        };
    }

    private boolean fits(TeamState home, TeamState away, Stadium stadium, LocalDate day) {
        return schedule.teamCanPlay(home, day) && schedule.teamCanPlay(away, day) && schedule.stadiumFree(stadium, day);
    }

    // ---------------------------------------------------------------- domestic cup

    private void startCup(String key, String league, String season, SeasonCalendar.CupWeeks weeks) {
        List<TeamState> entrants = new ArrayList<>(leagueMembers.get(league));
        entrants.addAll(rng.sample(lowerDivisions.get(league), 32 - entrants.size()));
        Set<TeamState> topFlight = new HashSet<>(leagueMembers.get(league));

        LocalDate end = weeks.fin().plusDays(2);
        EditionRun run = newRun(key, season, weeks.round32().plusDays(1), end, catalog.competitionByKey.get(key).rules());
        entrants.forEach(team -> run.enrol(team, rng));
        String[] venue = CUP_FINAL_VENUES.get(key);
        CupPlan plan = new CupPlan(run,
                List.of(newStage(run, "Round of 32", "knockout"), newStage(run, "Round of 16", "knockout"),
                        newStage(run, "Quarter-finals", "knockout"), newStage(run, "Semi-finals", "knockout"),
                        newStage(run, "Final", "knockout")),
                List.of(weeks.round32(), weeks.round16(), weeks.quarterFinals(), weeks.semiFinalFirst()),
                weeks.semiFinalSecond(), topFlight, !key.equals("puchar"), end, master.stadium(venue[0], venue[1]));
        cupRound(plan, 0, entrants);
    }

    private record CupPlan(EditionRun run, List<EditionRun.StageRun> stages, List<LocalDate> weeks, LocalDate secondSemiWeek,
                           Set<TeamState> topFlight, boolean twoLeggedSemis, LocalDate finalDate, Stadium finalVenue) {
    }

    private void cupRound(CupPlan plan, int index, List<TeamState> teams) {
        EditionRun run = plan.run();
        EditionRun.StageRun stage = plan.stages().get(index);
        if (index == 4) {
            playNeutralFinal(run, stage, teams, plan.finalVenue(), plan.finalDate());
            return;
        }
        List<TeamState> winners = new ArrayList<>();
        Fixture.Round round = new Fixture.Round(() -> cupRound(plan, index + 1, winners));
        boolean twoLegs = index == 3 && plan.twoLeggedSemis();
        boolean lowerClubHosts = run.competition.key().equals("copa");
        for (Pairings.Pair drawn : Pairings.draw(teams, rng, (a, b) -> false)) {
            Pairings.Pair pair = lowerClubHosts && plan.topFlight().contains(drawn.home()) && !plan.topFlight().contains(drawn.away())
                    ? new Pairings.Pair(drawn.away(), drawn.home()) : drawn;
            Fixture.Tie tie = new Fixture.Tie(pair.home(), pair.away(), twoLegs ? 2 : 1, true);
            tie.onDecided = t -> winners.add(t.winner);
            scheduleClubLeg(run, stage, pair.home(), pair.away(), plan.weeks().get(index), CUP_DAYS, MIDWEEK_TIMES, round, tie, 1);
            if (twoLegs) {
                scheduleClubLeg(run, stage, pair.away(), pair.home(), plan.secondSemiWeek(), CUP_DAYS, MIDWEEK_TIMES, round, tie, 2);
            }
        }
    }

    private void playNeutralFinal(EditionRun run, EditionRun.StageRun stage, List<TeamState> finalists, Stadium venue, LocalDate date) {
        Fixture.Tie tie = new Fixture.Tie(finalists.get(0), finalists.get(1), 1, true);
        tie.onDecided = t -> {
            run.finalPositions.put(t.winner, 1);
            run.finalPositions.put(t.loser, 2);
        };
        Fixture.Round round = new Fixture.Round(() -> {
        });
        round.add();
        schedule.book(finalists.get(0), finalists.get(1), venue, date);
        queue(new Fixture(run, stage, finalists.get(0), finalists.get(1), venue, date.atTime(21, 0), true, round, tie, 1));
    }

    /** Places one leg in the week, trying the days in order. A shared ground gets used twice only as a last resort. */
    private void scheduleClubLeg(EditionRun run, EditionRun.StageRun stage, TeamState home, TeamState away,
                                 LocalDate week, List<Integer> dayOffsets, List<LocalTime> times, Fixture.Round round,
                                 Fixture.Tie tie, int leg) {
        for (int offset : dayOffsets) {
            LocalDate day = week.plusDays(offset);
            if (fits(home, away, home.stadium, day)) {
                addClubFixture(run, stage, home, away, day.atTime(rng.pick(times)), round, tie, leg);
                return;
            }
        }
        for (int offset : dayOffsets) {
            LocalDate day = week.plusDays(offset);
            if (schedule.teamCanPlay(home, day) && schedule.teamCanPlay(away, day)) {
                // Two different kick-off slots keep uk_match_stadium_scheduled_at intact.
                LocalTime time = times.getLast();
                addClubFixture(run, stage, home, away, day.atTime(time).minusMinutes(rng.between(5, 40)), round, tie, leg);
                return;
            }
        }
        throw new IllegalStateException("No date for " + home + " v " + away + " in week " + week + " (" + run.title + ")");
    }

    private void addClubFixture(EditionRun run, EditionRun.StageRun stage, TeamState home, TeamState away,
                                LocalDateTime kickoff, Fixture.Round round, Fixture.Tie tie, int leg) {
        schedule.book(home, away, home.stadium, kickoff.toLocalDate());
        round.add();
        queue(new Fixture(run, stage, home, away, home.stadium, kickoff, false, round, tie, leg));
    }

    // ---------------------------------------------------------------- UEFA

    private record KnockoutStages(EditionRun.StageRun playOff, EditionRun.StageRun round16, EditionRun.StageRun quarter,
                                  EditionRun.StageRun semi, EditionRun.StageRun fin) {
    }

    private record UefaPlan(EditionRun run, KnockoutStages ko, SeasonCalendar calendar, Catalog.EditionDef def, List<Integer> days) {
    }

    private void startUefa(String key, String season, SeasonCalendar calendar, List<TeamState> entrants) {
        Catalog.EditionDef def = catalog.edition(key, season);
        List<LocalDate> weeks = calendar.uefaFirstStageWeeks();
        EditionRun run = newRun(key, season, weeks.getFirst().plusDays(1), def.end(), def.rules());
        List<Integer> days = key.equals("ucl") ? List.of(1, 2, 3) : List.of(3, 2, 1);

        if (calendar.leaguePhaseFormat) {
            EditionRun.StageRun phase = newStage(run, "League phase", "league");
            entrants.forEach(team -> run.enrolInStage(phase, team, rng));
            UefaPlan plan = new UefaPlan(run, uefaKnockoutStages(run, true), calendar, def, days);
            Fixture.Round round = new Fixture.Round(() -> afterLeaguePhase(plan, phase));
            List<List<Pairings.Pair>> rounds = Pairings.leaguePhase(entrants, rng);
            for (int i = 0; i < rounds.size(); i++) {
                for (Pairings.Pair pair : rounds.get(i)) {
                    scheduleClubLeg(run, phase, pair.home(), pair.away(), weeks.get(i), days, UEFA_TIMES, round, null, 1);
                }
            }
        } else {
            List<List<TeamState>> groups = Pairings.groups(entrants, 4, rng);
            List<EditionRun.StageRun> groupStages = new ArrayList<>();
            for (int g = 0; g < groups.size(); g++) {
                EditionRun.StageRun stage = newStage(run, "Group " + GROUP_LETTERS.get(g), "group");
                groupStages.add(stage);
                groups.get(g).forEach(team -> run.enrolInStage(stage, team, rng));
            }
            UefaPlan plan = new UefaPlan(run, uefaKnockoutStages(run, key.equals("uel")), calendar, def, days);
            Fixture.Round round = new Fixture.Round(() -> afterUefaGroups(plan, groupStages));
            for (int g = 0; g < groups.size(); g++) {
                List<List<Pairings.Pair>> rounds = Pairings.doubleRoundRobin(groups.get(g));
                for (int i = 0; i < rounds.size(); i++) {
                    for (Pairings.Pair pair : rounds.get(i)) {
                        scheduleClubLeg(run, groupStages.get(g), pair.home(), pair.away(), weeks.get(i), days, UEFA_TIMES, round, null, 1);
                    }
                }
            }
        }
    }

    private KnockoutStages uefaKnockoutStages(EditionRun run, boolean withPlayOff) {
        return new KnockoutStages(withPlayOff ? newStage(run, "Knockout phase play-offs", "knockout") : null,
                newStage(run, "Round of 16", "knockout"), newStage(run, "Quarter-finals", "knockout"),
                newStage(run, "Semi-finals", "knockout"), newStage(run, "Final", "knockout"));
    }

    private void afterLeaguePhase(UefaPlan plan, EditionRun.StageRun phase) {
        List<TeamState> ranking = phase.ranking();
        List<TeamState> direct = ranking.subList(0, 8);
        List<TeamState> playOffWinners = new ArrayList<>();
        Fixture.Round round = new Fixture.Round(() -> uefaRound16(plan,
                Pairings.seededDraw(direct, playOffWinners, rng, compatriots()), plan.calendar().uefaRound16Weeks()));
        twoLeggedRound(plan, plan.ko().playOff(),
                Pairings.seededDraw(ranking.subList(8, 16), ranking.subList(16, 24), rng, compatriots()),
                plan.calendar().uefaPlayOffWeeks(), round, playOffWinners);
    }

    private void afterUefaGroups(UefaPlan plan, List<EditionRun.StageRun> groups) {
        List<TeamState> winners = new ArrayList<>();
        List<TeamState> runnersUp = new ArrayList<>();
        List<TeamState> thirds = new ArrayList<>();
        for (EditionRun.StageRun group : groups) {
            List<TeamState> ranking = group.ranking();
            winners.add(ranking.get(0));
            runnersUp.add(ranking.get(1));
            thirds.add(ranking.get(2));
        }
        SeasonCalendar calendar = plan.calendar();
        if (plan.run().competition.key().equals("ucl")) {
            uclThirdPlaced = thirds;
            uefaRound16(plan, Pairings.seededDraw(winners, runnersUp, rng, compatriots()),
                    List.of(calendar.uefaPlayOffWeeks().getFirst(), calendar.uefaRound16Weeks().getFirst()));
            return;
        }
        // Waits for the winter break, so that the Champions League groups have certainly finished.
        simulation.at(calendar.uefaPlayOffWeeks().getFirst().minusDays(21).atTime(12, 0), () -> {
            uclThirdPlaced.forEach(team -> plan.run().enrol(team, rng));
            List<TeamState> playOffWinners = new ArrayList<>();
            Fixture.Round round = new Fixture.Round(() -> uefaRound16(plan,
                    Pairings.seededDraw(winners, playOffWinners, rng, compatriots()), calendar.uefaRound16Weeks()));
            twoLeggedRound(plan, plan.ko().playOff(), Pairings.seededDraw(runnersUp, uclThirdPlaced, rng, compatriots()),
                    calendar.uefaPlayOffWeeks(), round, playOffWinners);
        });
    }

    private void uefaRound16(UefaPlan plan, List<Pairings.Pair> pairs, List<LocalDate> weeks) {
        SeasonCalendar calendar = plan.calendar();
        List<TeamState> winners = new ArrayList<>();
        Fixture.Round round = new Fixture.Round(() -> {
            List<TeamState> quarterWinners = new ArrayList<>();
            Fixture.Round quarter = new Fixture.Round(() -> {
                List<TeamState> semiWinners = new ArrayList<>();
                Fixture.Round semi = new Fixture.Round(() -> playNeutralFinal(plan.run(), plan.ko().fin(), semiWinners,
                        master.stadium(plan.def().finalStadium(), plan.def().finalCity()), plan.def().end()));
                twoLeggedRound(plan, plan.ko().semi(), Pairings.draw(quarterWinners, rng, (a, b) -> false),
                        calendar.uefaSemiFinalWeeks(), semi, semiWinners);
            });
            twoLeggedRound(plan, plan.ko().quarter(), Pairings.draw(winners, rng, (a, b) -> false),
                    calendar.uefaQuarterFinalWeeks(), quarter, quarterWinners);
        });
        twoLeggedRound(plan, plan.ko().round16(), pairs, weeks, round, winners);
    }

    /** The named home team of each pair hosts the deciding second leg. */
    private void twoLeggedRound(UefaPlan plan, EditionRun.StageRun stage, List<Pairings.Pair> pairs, List<LocalDate> weeks,
                                Fixture.Round round, List<TeamState> winners) {
        for (Pairings.Pair pair : pairs) {
            Fixture.Tie tie = new Fixture.Tie(pair.away(), pair.home(), 2, true);
            tie.onDecided = t -> winners.add(t.winner);
            scheduleClubLeg(plan.run(), stage, pair.away(), pair.home(), weeks.get(0), plan.days(), UEFA_TIMES, round, tie, 1);
            scheduleClubLeg(plan.run(), stage, pair.home(), pair.away(), weeks.get(1), plan.days(), UEFA_TIMES, round, tie, 2);
        }
    }

    private static BiPredicate<TeamState, TeamState> compatriots() {
        return (a, b) -> a.country.equals(b.country);
    }

    // ---------------------------------------------------------------- tournaments

    private record TournamentShape(int groups, int bestThirds, List<String> knockoutRounds, boolean thirdPlace,
                                   boolean extraTimeOnlyInFinal) {
        /** Groups playing on the same day offset each round, which gives their teams equal rest. */
        int daySpread() {
            return Math.max(2, (int) Math.ceil(groups / 3.0));
        }

        int groupRoundGap() {
            return daySpread() + 3;
        }

        int lastGroupDay() {
            return 2 * groupRoundGap() + daySpread() - 1;
        }
    }

    private record TournamentPlan(EditionRun run, Catalog.EditionDef def, TournamentShape shape, List<Stadium> venues,
                                  List<EditionRun.StageRun> rounds, EditionRun.StageRun third, EditionRun.StageRun fin) {
    }

    private static TournamentShape shapeOf(String spec) {
        return switch (spec) {
            case "WC48" -> new TournamentShape(12, 8, List.of("Round of 32", "Round of 16", "Quarter-finals", "Semi-finals"), true, false);
            case "T24_THIRD" -> new TournamentShape(6, 4, List.of("Round of 16", "Quarter-finals", "Semi-finals"), true, false);
            case "T24_NO_THIRD" -> new TournamentShape(6, 4, List.of("Round of 16", "Quarter-finals", "Semi-finals"), false, false);
            case "T16" -> new TournamentShape(4, 0, List.of("Quarter-finals", "Semi-finals"), true, true);
            default -> throw new IllegalArgumentException(spec);
        };
    }

    private void startTournament(Catalog.EditionDef def) {
        TournamentShape shape = shapeOf(def.spec());
        EditionRun run = newRun(def.key(), def.season(), def.start(), def.end(), def.rules());
        List<TeamState> teams = chooseTournamentTeams(def, shape);
        List<Stadium> venues = catalog.stadiums.stream().filter(s -> s.tag().equals(def.stadiumTag()))
                .map(s -> master.stadium(s.name(), s.city())).toList();

        teams.forEach(team -> callUpSquad(run, team, def));
        List<List<TeamState>> groups = drawTournamentGroups(teams, def, shape);
        List<EditionRun.StageRun> groupStages = new ArrayList<>();
        for (int g = 0; g < groups.size(); g++) {
            EditionRun.StageRun stage = newStage(run, "Group " + GROUP_LETTERS.get(g), "group");
            groupStages.add(stage);
            groups.get(g).forEach(team -> run.enrolInStage(stage, team, rng));
        }
        List<EditionRun.StageRun> rounds = new ArrayList<>();
        for (String name : shape.knockoutRounds()) {
            rounds.add(newStage(run, name, "knockout"));
        }
        EditionRun.StageRun third = shape.thirdPlace() ? newStage(run, "Third-place play-off", "knockout") : null;
        EditionRun.StageRun fin = newStage(run, "Final", "knockout");
        TournamentPlan plan = new TournamentPlan(run, def, shape, venues, rounds, third, fin);

        int spread = shape.daySpread();
        Fixture.Round round = new Fixture.Round(() -> tournamentKnockout(plan, groupStages));
        for (int g = 0; g < groups.size(); g++) {
            List<List<Pairings.Pair>> matchdays = Pairings.singleRoundRobin(groups.get(g));
            for (int r = 0; r < matchdays.size(); r++) {
                LocalDate day = def.start().plusDays((long) r * shape.groupRoundGap() + g % spread);
                int slot = (g / spread) * 2;
                for (Pairings.Pair pair : matchdays.get(r)) {
                    addTournamentFixture(plan, groupStages.get(g), pair.home(), pair.away(), day, null, round, null, slot++);
                }
            }
        }
    }

    private List<TeamState> chooseTournamentTeams(Catalog.EditionDef def, TournamentShape shape) {
        List<TeamState> teams = new ArrayList<>();
        def.hosts().forEach(host -> teams.add(careers.nations.get(host)));
        int size = shape.groups() * 4;
        Map<String, Integer> quota = switch (def.key()) {
            case "wc" -> Map.of("UEFA", 16, "CAF", 9, "AFC", 9, "CONMEBOL", 7, "CONCACAF", 3, "OFC", 1);
            case "copaam" -> Map.of("CONMEBOL", 10, "CONCACAF", 5);
            case "afcon" -> Map.of("CAF", size - 1);
            case "asia" -> Map.of("AFC", size - 1);
            default -> Map.of("UEFA", size - 1);
        };
        for (String confederation : List.of("UEFA", "CAF", "AFC", "CONMEBOL", "CONCACAF", "OFC")) {
            List<TeamState> pool = catalog.nations.stream()
                    .filter(n -> n.confederation().equals(confederation) && !def.hosts().contains(n.name()))
                    .map(n -> careers.nations.get(n.name())).toList();
            teams.addAll(rng.sample(pool, quota.getOrDefault(confederation, 0)));
        }
        if (teams.size() != size) {
            throw new IllegalStateException(def.key() + " " + def.season() + " has " + teams.size() + " teams, needs " + size);
        }
        return teams;
    }

    /** A national squad of 26, chosen by ability among the fit players of that nation. */
    private void callUpSquad(EditionRun run, TeamState team, Catalog.EditionDef def) {
        LocalDate date = def.start();
        LocalDate until = def.end();
        Map<String, Integer> shape = Map.of(CareerSimulator.GK, 3, CareerSimulator.DF, 8, CareerSimulator.MF, 8, CareerSimulator.FW, 7);
        List<PlayerState> squad = new ArrayList<>();
        for (String position : List.of(CareerSimulator.GK, CareerSimulator.DF, CareerSimulator.MF, CareerSimulator.FW)) {
            List<PlayerState> options = new ArrayList<>();
            Map<PlayerState, Double> score = new HashMap<>();
            for (PlayerState p : careers.nationPool(team.country)) {
                if (p.position.equals(position) && !p.isRetired(until) && !date.isBefore(p.joinedHistory)
                        && !p.isInjured(date) && p.ageOn(date) >= 17) {
                    options.add(p);
                    score.put(p, p.quality + rng.gaussian(0, 0.5));
                }
            }
            options.sort(Comparator.comparingDouble(p -> -score.get(p)));
            squad.addAll(options.subList(0, Math.min(shape.get(position), options.size())));
        }
        for (PlayerState player : squad) {
            player.callUps.add(new PlayerState.CallUp(date.minusDays(3), until));
        }
        team.shirts.clear();
        run.squads.put(team, squad);
        lastNationalSquads.put(team, squad);
    }

    /** Hosts head the first groups; the others are drawn into the remaining places. */
    private List<List<TeamState>> drawTournamentGroups(List<TeamState> teams, Catalog.EditionDef def, TournamentShape shape) {
        int hostCount = def.hosts().size();
        List<TeamState> rest = rng.shuffled(teams.subList(hostCount, teams.size()));
        List<List<TeamState>> groups = new ArrayList<>();
        for (int g = 0; g < shape.groups(); g++) {
            groups.add(new ArrayList<>());
        }
        for (int i = 0; i < hostCount; i++) {
            groups.get(i).add(teams.get(i));
        }
        int next = 0;
        for (List<TeamState> group : groups) {
            while (group.size() < 4) {
                group.add(rest.get(next++));
            }
        }
        return groups;
    }

    private void tournamentKnockout(TournamentPlan plan, List<EditionRun.StageRun> groups) {
        List<TeamState> winners = new ArrayList<>();
        List<TeamState> runnersUp = new ArrayList<>();
        List<TeamState> thirds = new ArrayList<>();
        Map<TeamState, Integer> groupOf = new HashMap<>();
        Map<TeamState, EditionRun.Stats> lines = new HashMap<>();
        for (int g = 0; g < groups.size(); g++) {
            List<TeamState> ranking = groups.get(g).ranking();
            winners.add(ranking.get(0));
            runnersUp.add(ranking.get(1));
            thirds.add(ranking.get(2));
            for (TeamState team : ranking) {
                groupOf.put(team, g);
                lines.put(team, groups.get(g).table.get(team));
            }
        }
        thirds.sort(Comparator.<TeamState>comparingInt(t -> -lines.get(t).points)
                .thenComparingInt(t -> -lines.get(t).difference())
                .thenComparingInt(t -> -lines.get(t).goalsFor)
                .thenComparing(t -> t.name));
        List<TeamState> qualified = new ArrayList<>(winners);
        qualified.addAll(runnersUp);
        qualified.addAll(thirds.subList(0, plan.shape().bestThirds()));

        int half = qualified.size() / 2;
        List<Pairings.Pair> bracket = Pairings.seededDraw(qualified.subList(0, half), qualified.subList(half, qualified.size()),
                rng, (a, b) -> groupOf.get(a).equals(groupOf.get(b)));
        tournamentRound(plan, 0, bracket, plan.shape().lastGroupDay() + 2);
    }

    /** One knockout round in bracket order: winners of neighbouring ties meet in the next round. */
    private void tournamentRound(TournamentPlan plan, int index, List<Pairings.Pair> pairs, int startDay) {
        int spread = Math.max(1, Math.min(4, (int) Math.ceil(pairs.size() / 2.0)));
        TeamState[] winners = new TeamState[pairs.size()];
        TeamState[] losers = new TeamState[pairs.size()];
        boolean lastRound = index == plan.rounds().size() - 1;
        Fixture.Round round = new Fixture.Round(() -> {
            if (lastRound) {
                tournamentFinals(plan, winners, losers);
                return;
            }
            List<Pairings.Pair> next = new ArrayList<>();
            for (int i = 0; i + 1 < winners.length; i += 2) {
                next.add(new Pairings.Pair(winners[i], winners[i + 1]));
            }
            tournamentRound(plan, index + 1, next, startDay + spread + 1);
        });
        for (int i = 0; i < pairs.size(); i++) {
            final int slot = i;
            Pairings.Pair pair = pairs.get(i);
            Fixture.Tie tie = new Fixture.Tie(pair.home(), pair.away(), 1, !plan.shape().extraTimeOnlyInFinal());
            tie.onDecided = t -> {
                winners[slot] = t.winner;
                losers[slot] = t.loser;
            };
            LocalDate day = plan.def().start().plusDays(startDay + i % spread);
            addTournamentFixture(plan, plan.rounds().get(index), pair.home(), pair.away(), day, null, round, tie, 2 + i / spread);
        }
    }

    private void tournamentFinals(TournamentPlan plan, TeamState[] finalists, TeamState[] semiLosers) {
        EditionRun run = plan.run();
        Catalog.EditionDef def = plan.def();
        if (plan.third() != null) {
            Fixture.Tie tie = new Fixture.Tie(semiLosers[0], semiLosers[1], 1, !plan.shape().extraTimeOnlyInFinal());
            tie.onDecided = t -> {
                run.finalPositions.put(t.winner, 3);
                run.finalPositions.put(t.loser, 4);
            };
            addTournamentFixture(plan, plan.third(), semiLosers[0], semiLosers[1], def.end().minusDays(1), null,
                    new Fixture.Round(() -> {
                    }), tie, 4);
        }
        Fixture.Tie tie = new Fixture.Tie(finalists[0], finalists[1], 1, true);
        tie.onDecided = t -> {
            run.finalPositions.put(t.winner, 1);
            run.finalPositions.put(t.loser, 2);
        };
        addTournamentFixture(plan, plan.fin(), finalists[0], finalists[1], def.end(),
                master.stadium(def.finalStadium(), def.finalCity()), new Fixture.Round(() -> {
                }), tie, 4);
    }

    private void addTournamentFixture(TournamentPlan plan, EditionRun.StageRun stage, TeamState home, TeamState away,
                                      LocalDate day, Stadium fixedVenue, Fixture.Round round, Fixture.Tie tie, int timeSlot) {
        Stadium venue = fixedVenue;
        if (venue == null) {
            List<Stadium> free = plan.venues().stream().filter(v -> schedule.stadiumFree(v, day)).toList();
            venue = rng.pick(free.isEmpty() ? plan.venues() : free);
        }
        LocalTime time = TOURNAMENT_TIMES.get(timeSlot % TOURNAMENT_TIMES.size());
        boolean hostAtHome = plan.def().hosts().contains(home.country);
        schedule.book(home, away, venue, day);
        round.add();
        queue(new Fixture(plan.run(), stage, home, away, venue, day.atTime(time), !hostAtHome, round, tie, 1));
    }

    private void queue(Fixture fixture) {
        simulation.at(fixture.kickoff, () -> matches.play(fixture));
    }
}
