package pl.football.seeder.random;

import pl.football.seeder.entity.Coach;
import pl.football.seeder.entity.Injury;
import pl.football.seeder.entity.Player;
import pl.football.seeder.entity.Referee;
import pl.football.seeder.entity.Team;
import pl.football.seeder.entity.TeamSquad;

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

/** Teams, players, coaches and referees, plus the plans that do not depend on results: injuries and coaching changes. */
final class People {

    static final String GK = "goalkeeper";
    static final String DF = "defender";
    static final String MF = "midfielder";
    static final String FW = "forward";

    private static final Map<String, Integer> CLUB_SQUAD = Map.of(GK, 3, DF, 8, MF, 8, FW, 6);
    private static final Map<String, Integer> NATIONAL_SQUAD = Map.of(GK, 3, DF, 8, MF, 8, FW, 7);
    private static final List<String> POSITIONS = List.of(GK, DF, MF, FW);
    private static final Map<String, Double> HEIGHT = Map.of(GK, 190.0, DF, 184.0, MF, 178.0, FW, 181.0);
    private static final Map<Integer, Double> QUALITY_BY_TIER = Map.of(1, 1.2, 2, 0.5, 3, -0.2);

    private static final List<LocalDate> MID_SEASON_CHANGES = List.of(LocalDate.of(2024, 11, 4),
            LocalDate.of(2025, 2, 3), LocalDate.of(2025, 11, 3), LocalDate.of(2026, 2, 2), LocalDate.of(2026, 10, 12));
    private static final List<LocalDate> SUMMER_CHANGES = List.of(LocalDate.of(2025, 7, 1), LocalDate.of(2026, 7, 1));

    final List<TeamInfo> clubs = new ArrayList<>();
    /** Clubs by their division in clubs.csv (laliga, segunda, europe, ...). */
    final Map<String, List<TeamInfo>> divisions = new HashMap<>();
    final Map<String, TeamInfo> nations = new LinkedHashMap<>();
    final List<PlayerInfo> players = new ArrayList<>();
    /** Referee pools by key: a country for its league and cup, "UEFA" and "Copa". */
    final Map<String, List<Referee>> referees = new HashMap<>();

    private final List<Coach> coaches = new ArrayList<>();
    private final Rng rng;
    private final Catalog catalog;
    private final Dictionaries dict;
    private final Names names;

    People(Rng rng, Catalog catalog, Dictionaries dict, Names names) {
        this.rng = rng;
        this.catalog = catalog;
        this.dict = dict;
        this.names = names;
    }

    // ---------------------------------------------------------------- teams and players

    void createClubs() {
        for (Catalog.ClubDef def : catalog.clubs) {
            Team team = new Team(def.name(), "club", dict.region(def.country()));
            team.setShortName(def.shortName());
            team.setFoundedYear((short) def.founded());
            TeamInfo club = new TeamInfo(team, def.country(), def.tier(), dict.stadium(def.stadium(), def.city()),
                    false);
            clubs.add(club);
            divisions.computeIfAbsent(def.division(), d -> new ArrayList<>()).add(club);
            for (String position : POSITIONS) {
                for (int i = 0; i < CLUB_SQUAD.get(position); i++) {
                    PlayerInfo player = newPlayer(nationFor(def.country()), position, QUALITY_BY_TIER.get(def.tier()),
                            true);
                    player.clubs[0] = club;
                    player.owners[0] = club;
                }
            }
        }
    }

    /** The best players of each nation from the clubs, topped up with new players who have no club in the catalog. */
    void createNationalTeams(List<String> nationNames) {
        for (String name : nationNames) {
            Catalog.NationDef def = catalog.nation(name);
            Team team = new Team(name, "national_team", dict.region(name));
            team.setShortName(def.code());
            team.setFoundedYear((short) def.founded());
            TeamInfo nation = new TeamInfo(team, name, 0, null, true);
            List<PlayerInfo> squad = new ArrayList<>();
            for (String position : POSITIONS) {
                List<PlayerInfo> best = players.stream()
                        .filter(p -> p.nation.equals(name) && p.position.equals(position) && p.clubs[0] != null)
                        .sorted(Comparator.comparingDouble((PlayerInfo p) -> p.quality).reversed())
                        .limit(NATIONAL_SQUAD.get(position)).toList();
                squad.addAll(best);
                for (int i = best.size(); i < NATIONAL_SQUAD.get(position); i++) {
                    squad.add(newPlayer(name, position, 0.2, false));
                }
            }
            nation.rosters.add(squad);
            nation.shirts.add(numbers(squad, null, null));
            nations.put(name, nation);
        }
    }

    private PlayerInfo newPlayer(String nation, String position, double qualityMean, boolean wellKnown) {
        Names.Person person = names.male(catalog.nation(nation).culture());
        LocalDate birth = rng.dateBetween(SeedTime.HISTORY_START.minusYears(35), SeedTime.HISTORY_START.minusYears(18));
        Player player = new Player(person.firstName(), person.lastName(), birth, dict.region(nation));
        player.setPrimaryPosition(position);
        // Lesser-known players without a club in the catalog sometimes have no height or foot recorded.
        if (wellKnown || rng.chance(0.7)) {
            double height = Math.max(165, Math.min(203, rng.gaussian(HEIGHT.get(position), 5)));
            player.setHeightCm((short) Math.round(height));
            player.setFoot(dict.feet.get(rng.weighted(List.of("right", "left", "both"),
                    f -> switch (f) {
                        case "right" -> 77;
                        case "left" -> 20;
                        default -> 3;
                    })));
        }
        PlayerInfo info = new PlayerInfo(player, position, rng.gaussian(qualityMean, 0.6), nation);
        players.add(info);
        return info;
    }

    /** 70% are from the club's own country, the rest from anywhere, mostly Europe. */
    private String nationFor(String country) {
        if (rng.chance(0.7)) {
            return country;
        }
        List<Catalog.NationDef> pool = rng.chance(0.5)
                ? catalog.nations.stream().filter(n -> n.confederation().equals("UEFA")).toList()
                : catalog.nations;
        return rng.pick(pool).name();
    }

    /** Called after the transfer windows, when it is known who plays where in every season. */
    void buildRostersAndShirts() {
        for (TeamInfo club : clubs) {
            for (int s = 0; s < SeedTime.SEASONS.size(); s++) {
                club.rosters.add(new ArrayList<>());
            }
        }
        for (PlayerInfo player : players) {
            for (int s = 0; s < SeedTime.SEASONS.size(); s++) {
                if (player.clubs[s] != null) {
                    player.clubs[s].rosters.get(s).add(player);
                }
            }
        }
        for (TeamInfo club : clubs) {
            for (int s = 0; s < SeedTime.SEASONS.size(); s++) {
                club.shirts.add(numbers(club.roster(s), s == 0 ? null : club.shirts.get(s - 1),
                        s == 0 ? null : club.roster(s - 1)));
            }
        }
    }

    /** A player keeps last season's number at the same club when he can; goalkeepers prefer 1, 13 and 25. */
    private static Map<PlayerInfo, Short> numbers(List<PlayerInfo> roster, Map<PlayerInfo, Short> previous,
                                                  List<PlayerInfo> previousRoster) {
        Map<PlayerInfo, Short> numbers = new HashMap<>();
        Set<Short> used = new HashSet<>();
        if (previous != null) {
            for (PlayerInfo player : roster) {
                Short number = previousRoster.contains(player) ? previous.get(player) : null;
                if (number != null && used.add(number)) {
                    numbers.put(player, number);
                }
            }
        }
        for (PlayerInfo player : roster) {
            if (numbers.containsKey(player)) {
                continue;
            }
            List<Integer> wanted = new ArrayList<>(player.position.equals(GK) ? List.of(1, 13, 25) : List.of());
            for (int n = 2; n <= 99; n++) {
                wanted.add(n);
            }
            for (int n : wanted) {
                if (used.add((short) n)) {
                    numbers.put(player, (short) n);
                    break;
                }
            }
        }
        return numbers;
    }

    // ---------------------------------------------------------------- plans that do not depend on results

    /** Each season has on average 0.35 injuries per player; injuries of one player never overlap. */
    void planInjuries() {
        for (PlayerInfo player : players) {
            LocalDate cursor = SeedTime.HISTORY_START;
            seasons:
            for (int year : SeedTime.SEASONS) {
                LocalDate to = LocalDate.of(year + 1, 6, 30);
                to = to.isAfter(SeedTime.TODAY) ? SeedTime.TODAY : to;
                for (int i = rng.poisson(0.35); i > 0 && !cursor.isAfter(to); i--) {
                    LocalDate start = rng.dateBetween(cursor, to);
                    Catalog.InjuryDef def = rng.weighted(catalog.injuries, Catalog.InjuryDef::weight);
                    LocalDate end = start.plusDays(rng.between(def.minDays(), def.maxDays()));
                    boolean ongoing = !end.isBefore(SeedTime.TODAY);
                    player.injuries.add(new PlayerInfo.InjurySpan(start, ongoing ? null : end, def.description()));
                    if (ongoing) {
                        break seasons;
                    }
                    cursor = end.plusDays(rng.between(1, 30));
                }
            }
        }
    }

    /** Coaching changes on fixed dates. A dismissed coach waits in a pool and may take another job later. */
    void planCoaches() {
        List<TeamInfo> teams = new ArrayList<>(clubs);
        teams.addAll(nations.values());
        for (TeamInfo team : teams) {
            team.coaches.add(new TeamInfo.CoachStint(newCoach(rng.chance(0.7) ? team.country : null),
                    SeedTime.HISTORY_START));
        }
        List<Coach> spare = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            spare.add(newCoach(null));
        }
        List<LocalDate> dates = new ArrayList<>(MID_SEASON_CHANGES);
        dates.addAll(SUMMER_CHANGES);
        dates.sort(Comparator.naturalOrder());
        for (LocalDate date : dates) {
            boolean summer = SUMMER_CHANGES.contains(date);
            for (TeamInfo team : rng.shuffled(teams)) {
                double chance = team.national ? (summer ? 0.15 : 0) : (summer ? 0.20 : 0.06);
                if (rng.chance(chance)) {
                    Coach outgoing = team.coaches.getLast().coach();
                    team.coaches.add(new TeamInfo.CoachStint(spare.removeFirst(), date));
                    spare.addLast(outgoing);
                }
            }
        }
    }

    private Coach newCoach(String country) {
        String nation = country != null ? country : rng.pick(catalog.nations).name();
        Names.Person person = names.male(catalog.nation(nation).culture());
        Coach coach = new Coach(person.firstName(), person.lastName(), dict.region(nation));
        coach.setBirthDate(rng.dateBetween(LocalDate.of(1960, 1, 1), LocalDate.of(1988, 12, 31)));
        coaches.add(coach);
        return coach;
    }

    /** One pool per country for its league and cup, one for the Champions League and one for Copa América. */
    void createReferees() {
        referees.put("Spain", newReferees(20, List.of("Spain")));
        referees.put("Italy", newReferees(20, List.of("Italy")));
        referees.put("Poland", newReferees(18, List.of("Poland")));
        referees.put("UEFA", newReferees(24, catalog.nations.stream()
                .filter(n -> n.confederation().equals("UEFA") && !List.of("Spain", "Italy", "Poland").contains(n.name()))
                .map(Catalog.NationDef::name).toList()));
        referees.put("Copa", newReferees(16, catalog.nations.stream()
                .filter(n -> n.confederation().equals("CONMEBOL") || n.confederation().equals("CONCACAF"))
                .map(Catalog.NationDef::name).toList()));
    }

    private List<Referee> newReferees(int count, List<String> countries) {
        List<Referee> pool = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            String nation = rng.pick(countries);
            Names.Person person = names.male(catalog.nation(nation).culture());
            Referee referee = new Referee(person.firstName(), person.lastName(), dict.region(nation));
            referee.setBirthDate(rng.dateBetween(LocalDate.of(1974, 1, 1), LocalDate.of(1995, 12, 31)));
            pool.add(referee);
        }
        return pool;
    }

    // ---------------------------------------------------------------- persistence

    /** Coaches go first because Team.coach_id points at them; the last stint is the coach at "now". */
    void persist(Db db, Editors editors) {
        LocalDateTime at = SeedTime.MASTER_DATA_AT;
        for (Coach coach : coaches) {
            db.save(coach, editors.masterData(), at);
        }
        List<TeamInfo> teams = new ArrayList<>(clubs);
        teams.addAll(nations.values());
        for (TeamInfo team : teams) {
            team.team.setCoach(team.coaches.getLast().coach());
            db.save(team.team, editors.masterData(), at);
        }
        for (PlayerInfo player : players) {
            // Players without a club in the catalog were added when the national squads were picked.
            LocalDateTime created = player.clubs[0] != null ? at : LocalDateTime.of(2024, 6, 5, 10, 0);
            db.save(player.player, editors.masterData(), created);
        }
        for (List<Referee> pool : referees.values()) {
            for (Referee referee : pool) {
                db.save(referee, editors.masterData(), at);
            }
        }
    }

    /** Injuries, and the squads at "now": the current club, and the last squad of each national team. */
    void persistInjuriesAndSquads(Db db, Editors editors) {
        for (PlayerInfo player : players) {
            for (PlayerInfo.InjurySpan span : player.injuries) {
                Injury injury = new Injury(player.player, span.start(), span.description());
                injury.setEndDate(span.end());
                LocalDateTime created = SeedTime.notAfterNow(span.start().plusDays(1).atTime(11, 0));
                if (span.end() != null) {
                    injury.setUpdatedAt(SeedTime.notAfterNow(span.end().plusDays(1).atTime(11, 0)));
                }
                db.save(injury, editors.injuries(), created);
            }
        }
        db.endOfBatch();
        int current = SeedTime.CURRENT_SEASON;
        for (TeamInfo club : clubs) {
            for (PlayerInfo player : club.roster(current)) {
                LocalDate joined = player.joined[current];
                LocalDateTime created = joined != null ? joined.atTime(12, 0) : SeedTime.MASTER_DATA_AT;
                db.save(new TeamSquad(club.team, player.player), editors.masterData(), created);
            }
        }
        for (TeamInfo nation : nations.values()) {
            for (PlayerInfo player : nation.roster(0)) {
                db.save(new TeamSquad(nation.team, player.player), editors.masterData(),
                        LocalDateTime.of(2024, 6, 10, 10, 0));
            }
        }
    }
}
