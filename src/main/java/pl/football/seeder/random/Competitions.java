package pl.football.seeder.random;

import pl.football.seeder.entity.Competition;
import pl.football.seeder.entity.Edition;
import pl.football.seeder.entity.Referee;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static java.time.DayOfWeek.WEDNESDAY;

/**
 * The formats: leagues, domestic cups, the Champions League and Copa América. Editions are played
 * one after another; only the league tables carry over, into promotion, relegation and the next
 * season's Champions League places.
 */
final class Competitions {

    static final List<String> LEAGUES = List.of("laliga", "seriea", "ekstraklasa");
    private static final Map<String, String> CUP = Map.of("laliga", "copa", "seriea", "coppa", "ekstraklasa", "puchar");
    private static final Map<String, String> LOWER = Map.of("laliga", "segunda", "seriea", "serieb",
            "ekstraklasa", "iliga");
    private static final Map<String, String> COUNTRY = Map.of("laliga", "Spain", "seriea", "Italy",
            "ekstraklasa", "Poland");

    /** The real Copa América 2024 groups; only the results are random. */
    static final List<List<String>> COPA_GROUPS = List.of(
            List.of("Argentina", "Peru", "Chile", "Canada"),
            List.of("Mexico", "Ecuador", "Venezuela", "Jamaica"),
            List.of("United States", "Uruguay", "Panama", "Bolivia"),
            List.of("Brazil", "Colombia", "Paraguay", "Costa Rica"));

    private static final List<String> CUP_ROUNDS = List.of("Round of 32", "Round of 16", "Quarter-finals",
            "Semi-finals", "Final");
    /** Week numbers counted from the first league weekend; chosen not to meet the Champions League weeks. */
    private static final List<Integer> CUP_WEEKS = List.of(6, 11, 20, 24, 34);
    private static final List<Integer> UCL_GROUP_WEEKS = List.of(3, 5, 8, 10, 13, 15);
    private static final List<String> UCL_ROUNDS = List.of("Round of 16", "Quarter-finals", "Semi-finals");
    private static final List<List<Integer>> UCL_ROUND_WEEKS = List.of(List.of(26, 28), List.of(31, 33),
            List.of(36, 38));
    private static final int UCL_FINAL_WEEK = 41;

    private static final String CUP_RULES = "Single-elimination from the round of 32 in one-off matches. "
            + "Ties go to extra time and penalties. The final is played at a neutral venue when the country has one.";
    private static final String UCL_RULES = "Eight groups of four, double round-robin. Group winners and runners-up "
            + "reach the round of 16. Two-legged ties with extra time and penalties after the second leg, "
            + "no away goals rule. One-off final at a neutral venue.";
    private static final String COPA_RULES = "Four groups of four, single round-robin. The top two in each group "
            + "reach the quarter-finals. Knockout matches level after 90 minutes go straight to penalties, "
            + "except the final, which has extra time. A third-place match is played.";

    private final Rng rng;
    private final Db db;
    private final Catalog catalog;
    private final Dictionaries dict;
    private final Editors editors;
    private final People people;
    private final MatchEngine engine;
    private final Calendar calendar;
    private final Map<String, Competition> competitions = new HashMap<>();

    Competitions(Rng rng, Db db, Catalog catalog, Dictionaries dict, Editors editors, People people,
                 MatchEngine engine, Calendar calendar) {
        this.rng = rng;
        this.db = db;
        this.catalog = catalog;
        this.dict = dict;
        this.editors = editors;
        this.people = people;
        this.engine = engine;
        this.calendar = calendar;
    }

    void runAll() {
        List<String> keys = new ArrayList<>(LEAGUES);
        LEAGUES.forEach(l -> keys.add(CUP.get(l)));
        keys.addAll(List.of("ucl", "copaam"));
        for (String key : keys) {
            Catalog.CompetitionDef def = catalog.competition(key);
            Competition competition = new Competition(def.name(), def.format(), def.level(), dict.region(def.region()));
            competitions.put(key, db.save(competition, editors.admin, SeedTime.MASTER_DATA_AT));
        }

        copaAmerica();
        // Before the first season there is no table yet, so Champions League places go by squad strength.
        Map<String, List<TeamInfo>> previous = new HashMap<>();
        for (String league : LEAGUES) {
            previous.put(league, people.divisions.get(league).stream()
                    .sorted(Comparator.comparingDouble((TeamInfo t) -> t.strength(0)).reversed()).toList());
        }
        for (int s = 0; s < SeedTime.SEASONS.size(); s++) {
            Map<String, List<TeamInfo>> tables = new HashMap<>();
            for (String league : LEAGUES) {
                tables.put(league, league(league, s));
            }
            for (String league : LEAGUES) {
                cup(league, s);
            }
            championsLeague(s, previous);
            if (s < SeedTime.CURRENT_SEASON) {
                promoteAndRelegate(tables);
            }
            previous = tables;
        }
    }

    // ---------------------------------------------------------------- leagues

    /** Returns the table, best first. */
    private List<TeamInfo> league(String key, int s) {
        int year = SeedTime.SEASONS.get(s);
        boolean current = s == SeedTime.CURRENT_SEASON;
        List<TeamInfo> clubs = people.divisions.get(key);
        List<List<RoundRobin.Pair>> rounds = RoundRobin.twice(rng.shuffled(clubs));
        List<LocalDate> weekends = Calendar.leagueWeekends(year, rounds.size());
        LocalDate start = weekends.getFirst().minusDays(1);
        LocalDate end = current ? null : weekends.getLast().plusDays(2);
        RunningEdition run = open(key, s, start, end, catalog.competition(key).rules(),
                people.referees.get(COUNTRY.get(key)));
        run.enter(clubs);

        LocalDateTime published = start.minusDays(20).atTime(12, 0);
        Stage stage = run.stage("Regular season", "league", published);
        for (int r = 0; r < rounds.size(); r++) {
            List<RoundRobin.Pair> round = rng.shuffled(rounds.get(r));
            for (int i = 0; i < round.size(); i++) {
                RoundRobin.Pair pair = round.get(i);
                Calendar.Slot slot = Calendar.LEAGUE_SLOTS.get(i);
                LocalDateTime kickoff = calendar.kickoff(pair.home().stadium, pair.home(), pair.away(),
                        List.of(weekends.get(r).plusDays(slot.dayOffset())), List.of(slot.time()), 0);
                run.play(stage, pair.home(), pair.away(), pair.home().stadium, kickoff, published,
                        current ? forcedStatus(key, r, i) : null);
            }
            db.endOfBatch();
        }

        List<TeamInfo> table = run.table.ranking(clubs);
        Map<TeamInfo, Integer> positions = new HashMap<>();
        if (!current) {
            for (int i = 0; i < table.size(); i++) {
                positions.put(table.get(i), i + 1);
            }
        }
        run.finish(clubs, positions, end);
        return table;
    }

    /** A few fixed matches of the current season were postponed or one cancelled, so those statuses exist. */
    private static String forcedStatus(String league, int round, int index) {
        if (round == 4 && index == 3) {
            return "postponed";
        }
        if (league.equals("seriea") && round == 6 && index == 4) {
            return "cancelled";
        }
        return null;
    }

    /** The bottom three go down and three random clubs from the division below come up. */
    private void promoteAndRelegate(Map<String, List<TeamInfo>> tables) {
        for (String league : LEAGUES) {
            List<TeamInfo> table = tables.get(league);
            List<TeamInfo> down = table.subList(table.size() - 3, table.size());
            List<TeamInfo> lower = people.divisions.get(LOWER.get(league));
            List<TeamInfo> up = rng.sample(lower, 3);
            people.divisions.get(league).removeAll(down);
            people.divisions.get(league).addAll(up);
            lower.removeAll(up);
            lower.addAll(down);
        }
    }

    // ---------------------------------------------------------------- domestic cups

    /** Each round is drawn after the previous one has ended; a round not yet played ends the edition for now. */
    private void cup(String league, int s) {
        int year = SeedTime.SEASONS.get(s);
        boolean current = s == SeedTime.CURRENT_SEASON;
        List<TeamInfo> entrants = new ArrayList<>(people.divisions.get(league));
        entrants.addAll(rng.sample(people.divisions.get(LOWER.get(league)), 32 - entrants.size()));
        LocalDate start = Calendar.midweek(year, CUP_WEEKS.getFirst(), TUESDAY);
        LocalDate finalDay = Calendar.midweek(year, CUP_WEEKS.getLast(), WEDNESDAY);
        RunningEdition run = open(CUP.get(league), s, start, current ? null : finalDay, CUP_RULES,
                people.referees.get(COUNTRY.get(league)));
        run.enter(entrants);
        Stadium neutral = dict.tagged("FINAL").stream()
                .filter(st -> COUNTRY.get(league).equals(dict.countryOf(st))).findFirst().orElse(null);

        Map<TeamInfo, Integer> positions = new HashMap<>();
        List<TeamInfo> alive = rng.shuffled(entrants);
        LocalDateTime drawnAt = start.minusDays(20).atTime(12, 0);
        for (int k = 0; k < CUP_ROUNDS.size() && !drawnAt.isAfter(SeedTime.NOW); k++) {
            boolean isFinal = k == CUP_ROUNDS.size() - 1;
            Stage stage = run.stage(CUP_ROUNDS.get(k), "knockout", drawnAt);
            int week = CUP_WEEKS.get(k);
            List<LocalDate> days = isFinal ? List.of(finalDay) : List.of(Calendar.midweek(year, week, TUESDAY),
                    Calendar.midweek(year, week, WEDNESDAY), Calendar.midweek(year, week, THURSDAY));
            List<TeamInfo> winners = new ArrayList<>();
            LocalDateTime last = drawnAt;
            for (int i = 0; i + 1 < alive.size(); i += 2) {
                TeamInfo home = alive.get(i);
                TeamInfo away = alive.get(i + 1);
                Stadium stadium = isFinal && neutral != null ? neutral : home.stadium;
                LocalDateTime kickoff = calendar.kickoff(stadium, home, away, days, Calendar.EVENING, i / 2);
                MatchEngine.Result result = run.playDecisive(stage, home, away, stadium, kickoff, drawnAt, true, null);
                if (result.winner() != null) {
                    winners.add(result.winner());
                    if (isFinal) {
                        positions.put(result.winner(), 1);
                        positions.put(result.winner() == home ? away : home, 2);
                    }
                }
                last = kickoff.isAfter(last) ? kickoff : last;
            }
            db.endOfBatch();
            if (winners.size() * 2 < alive.size()) {
                break;
            }
            alive = rng.shuffled(winners);
            drawnAt = last.toLocalDate().plusDays(rng.between(2, 6)).atTime(13, 0);
        }
        run.finish(entrants, positions, current ? null : finalDay);
    }

    // ---------------------------------------------------------------- Champions League

    private void championsLeague(int s, Map<String, List<TeamInfo>> previous) {
        int year = SeedTime.SEASONS.get(s);
        boolean current = s == SeedTime.CURRENT_SEASON;
        List<TeamInfo> entrants = new ArrayList<>();
        for (String league : LEAGUES) {
            entrants.addAll(previous.get(league).subList(0, 4));
        }
        entrants.addAll(rng.sample(people.divisions.get("europe"), 20));
        LocalDate start = Calendar.midweek(year, UCL_GROUP_WEEKS.getFirst(), TUESDAY);
        LocalDate finalDay = Calendar.saturday(year, UCL_FINAL_WEEK);
        RunningEdition run = open("ucl", s, start, current ? null : finalDay, UCL_RULES, people.referees.get("UEFA"));
        run.enter(entrants);

        LocalDateTime drawnAt = start.minusDays(20).atTime(18, 0);
        List<TeamInfo> drawn = rng.shuffled(entrants);
        List<List<TeamInfo>> groups = new ArrayList<>();
        List<Stage> stages = new ArrayList<>();
        List<List<List<RoundRobin.Pair>>> fixtures = new ArrayList<>();
        for (int g = 0; g < 8; g++) {
            List<TeamInfo> group = drawn.subList(4 * g, 4 * g + 4);
            groups.add(group);
            stages.add(run.stage("Group " + (char) ('A' + g), "group", drawnAt));
            fixtures.add(RoundRobin.twice(group));
        }
        boolean groupsOver = true;
        LocalDateTime lastGroupMatch = drawnAt;
        for (int md = 0; md < UCL_GROUP_WEEKS.size(); md++) {
            for (int g = 0; g < 8; g++) {
                LocalDate day = Calendar.midweek(year, UCL_GROUP_WEEKS.get(md), g < 4 ? TUESDAY : WEDNESDAY);
                List<RoundRobin.Pair> pairs = fixtures.get(g).get(md);
                for (int j = 0; j < pairs.size(); j++) {
                    RoundRobin.Pair pair = pairs.get(j);
                    LocalDateTime kickoff = calendar.kickoff(pair.home().stadium, pair.home(), pair.away(),
                            List.of(day), Calendar.EVENING, j);
                    groupsOver &= run.play(stages.get(g), pair.home(), pair.away(), pair.home().stadium, kickoff,
                            drawnAt, null).finished();
                    lastGroupMatch = kickoff.isAfter(lastGroupMatch) ? kickoff : lastGroupMatch;
                }
            }
            db.endOfBatch();
        }

        Map<TeamInfo, Integer> positions = new HashMap<>();
        if (groupsOver) {
            // A group winner plays the second leg at home against the runner-up of the next group.
            List<TeamInfo> ties = new ArrayList<>();
            for (int g = 0; g < 8; g++) {
                ties.add(run.table.ranking(groups.get((g + 1) % 8)).get(1));
                ties.add(run.table.ranking(groups.get(g)).get(0));
            }
            LocalDateTime knockoutDraw = lastGroupMatch.toLocalDate().plusDays(5).atTime(12, 0);
            for (int k = 0; k < UCL_ROUNDS.size(); k++) {
                Stage stage = run.stage(UCL_ROUNDS.get(k), "knockout", knockoutDraw);
                List<TeamInfo> winners = new ArrayList<>();
                LocalDate secondLegs = null;
                for (int i = 0; i + 1 < ties.size(); i += 2) {
                    List<Integer> weeks = UCL_ROUND_WEEKS.get(k);
                    winners.add(twoLegs(run, stage, ties.get(i), ties.get(i + 1), year, weeks, i / 2, knockoutDraw));
                    secondLegs = Calendar.midweek(year, weeks.get(1), WEDNESDAY);
                }
                db.endOfBatch();
                ties = rng.shuffled(winners);
                knockoutDraw = secondLegs.plusDays(2).atTime(12, 0);
            }
            Stage stage = run.stage("Final", "knockout", knockoutDraw);
            Stadium venue = rng.pick(dict.tagged("FINAL"));
            LocalDateTime kickoff = calendar.kickoff(venue, ties.get(0), ties.get(1), List.of(finalDay),
                    List.of(LocalTime.of(21, 0)), 0);
            MatchEngine.Result result = run.playDecisive(stage, ties.get(0), ties.get(1), venue, kickoff, knockoutDraw,
                    true, null);
            positions.put(result.winner(), 1);
            positions.put(result.winner() == ties.get(0) ? ties.get(1) : ties.get(0), 2);
        }
        run.finish(entrants, positions, current ? null : finalDay);
    }

    /** No away goals rule: level on aggregate after the second leg means extra time, then penalties. */
    private TeamInfo twoLegs(RunningEdition run, Stage stage, TeamInfo first, TeamInfo second, int year,
                             List<Integer> weeks, int index, LocalDateTime drawnAt) {
        java.time.DayOfWeek day = index % 2 == 0 ? TUESDAY : WEDNESDAY;
        LocalDateTime firstKickoff = calendar.kickoff(first.stadium, first, second,
                List.of(Calendar.midweek(year, weeks.get(0), day)), Calendar.EVENING, index);
        MatchEngine.Result firstLeg = run.play(stage, first, second, first.stadium, firstKickoff, drawnAt, null);
        LocalDateTime secondKickoff = calendar.kickoff(second.stadium, second, first,
                List.of(Calendar.midweek(year, weeks.get(1), day)), Calendar.EVENING, index);
        return run.playDecisive(stage, second, first, second.stadium, secondKickoff, drawnAt, true,
                new int[]{firstLeg.awayGoals(), firstLeg.homeGoals()}).winner();
    }

    // ---------------------------------------------------------------- Copa América 2024

    private void copaAmerica() {
        LocalDate start = LocalDate.of(2024, 6, 20);
        LocalDate end = LocalDate.of(2024, 7, 14);
        List<List<TeamInfo>> groups = COPA_GROUPS.stream()
                .map(names -> names.stream().map(people.nations::get).toList()).toList();
        List<TeamInfo> teams = groups.stream().flatMap(List::stream).toList();
        RunningEdition run = open("copaam", 0, "2024", start, end, COPA_RULES, people.referees.get("Copa"));
        run.enter(teams);
        List<Stadium> venues = dict.tagged("COPA2024");
        List<LocalTime> times = List.of(LocalTime.of(18, 0), LocalTime.of(21, 0));

        LocalDateTime drawnAt = LocalDateTime.of(2024, 5, 31, 12, 0);
        for (int g = 0; g < groups.size(); g++) {
            Stage stage = run.stage("Group " + (char) ('A' + g), "group", drawnAt);
            List<List<RoundRobin.Pair>> rounds = RoundRobin.once(groups.get(g));
            for (int md = 0; md < rounds.size(); md++) {
                LocalDate day = start.plusDays(4L * md + g);
                List<RoundRobin.Pair> pairs = rounds.get(md);
                for (int j = 0; j < pairs.size(); j++) {
                    RoundRobin.Pair pair = pairs.get(j);
                    Stadium venue = venues.get((md * 8 + g * 2 + j) % venues.size());
                    LocalDateTime kickoff = calendar.kickoff(venue, pair.home(), pair.away(), List.of(day), times, j);
                    run.play(stage, pair.home(), pair.away(), venue, kickoff, drawnAt, null);
                }
            }
        }
        db.endOfBatch();

        // Quarter-finals: A1-B2, B1-A2, C1-D2, D1-C2; the semi-finals pair them in that order.
        List<List<TeamInfo>> tables = groups.stream().map(run.table::ranking).toList();
        List<TeamInfo> quarterFinalists = List.of(tables.get(0).get(0), tables.get(1).get(1), tables.get(1).get(0),
                tables.get(0).get(1), tables.get(2).get(0), tables.get(3).get(1), tables.get(3).get(0),
                tables.get(2).get(1));
        LocalDateTime knockoutDraw = LocalDateTime.of(2024, 7, 2, 12, 0);
        List<TeamInfo> semiFinalists = knockoutRound(run, "Quarter-finals", quarterFinalists,
                List.of(LocalDate.of(2024, 7, 4), LocalDate.of(2024, 7, 5), LocalDate.of(2024, 7, 6),
                        LocalDate.of(2024, 7, 6)), venues, knockoutDraw, false);
        List<TeamInfo> finalists = knockoutRound(run, "Semi-finals", semiFinalists,
                List.of(LocalDate.of(2024, 7, 9), LocalDate.of(2024, 7, 10)), venues, knockoutDraw.plusDays(5), false);
        List<TeamInfo> losers = semiFinalists.stream().filter(t -> !finalists.contains(t)).toList();
        List<TeamInfo> third = knockoutRound(run, "Third place play-off", losers, List.of(LocalDate.of(2024, 7, 13)),
                venues, knockoutDraw.plusDays(9), false);
        List<TeamInfo> champion = knockoutRound(run, "Final", finalists, List.of(end),
                List.of(dict.stadium("Hard Rock Stadium", "Miami Gardens")), knockoutDraw.plusDays(9), true);

        Map<TeamInfo, Integer> positions = new HashMap<>();
        positions.put(champion.getFirst(), 1);
        positions.put(finalists.stream().filter(t -> t != champion.getFirst()).findFirst().orElseThrow(), 2);
        positions.put(third.getFirst(), 3);
        positions.put(losers.stream().filter(t -> t != third.getFirst()).findFirst().orElseThrow(), 4);
        run.finish(teams, positions, end);
    }

    /** One-off matches between consecutive pairs of {@code teams}, on neutral venues. Returns the winners. */
    private List<TeamInfo> knockoutRound(RunningEdition run, String name, List<TeamInfo> teams, List<LocalDate> days,
                                         List<Stadium> venues, LocalDateTime drawnAt, boolean extraTime) {
        Stage stage = run.stage(name, "knockout", drawnAt);
        List<TeamInfo> winners = new ArrayList<>();
        for (int i = 0; i + 1 < teams.size(); i += 2) {
            Stadium venue = rng.pick(venues);
            LocalDateTime kickoff = calendar.kickoff(venue, teams.get(i), teams.get(i + 1), List.of(days.get(i / 2)),
                    List.of(LocalTime.of(21, 0), LocalTime.of(18, 0)), 0);
            winners.add(run.playDecisive(stage, teams.get(i), teams.get(i + 1), venue, kickoff, drawnAt, extraTime, null)
                    .winner());
        }
        db.endOfBatch();
        return winners;
    }

    // ---------------------------------------------------------------- editions

    /** Saves the edition, appoints its staff and returns it ready to be played. */
    private RunningEdition open(String key, int season, LocalDate start, LocalDate end, String rules,
                                List<Referee> referees) {
        return open(key, season, SeedTime.seasonName(season), start, end, rules, referees);
    }

    private RunningEdition open(String key, int season, String seasonName, LocalDate start, LocalDate end,
                                String rules, List<Referee> referees) {
        Catalog.CompetitionDef def = catalog.competition(key);
        Edition edition = new Edition(competitions.get(key), seasonName, start, (short) def.substitutionLimit());
        edition.setEndDate(end);
        edition.setAdvancementRules(rules);
        db.save(edition, editors.admin, start.minusDays(45).atTime(10, 0));
        String title = def.name() + " " + seasonName;
        Editors.Staff staff = editors.staffFor(edition, title, start, end);
        return new RunningEdition(edition, title, season, staff, new MatchEngine.Rules(def.bench(),
                def.substitutionLimit()), referees, rng, db, engine, calendar);
    }
}
