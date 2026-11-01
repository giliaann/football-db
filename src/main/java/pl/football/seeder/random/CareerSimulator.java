package pl.football.seeder.random;

import pl.football.seeder.entity.Coach;
import pl.football.seeder.entity.Currency;
import pl.football.seeder.entity.Editor;
import pl.football.seeder.entity.Foot;
import pl.football.seeder.entity.Injury;
import pl.football.seeder.entity.Player;
import pl.football.seeder.entity.Team;
import pl.football.seeder.entity.TeamSquad;
import pl.football.seeder.entity.Transfer;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Decides who plays where and when, before any match is simulated. Careers are kept as stints in
 * memory; the Transfer rows and the final TeamSquad are derived from them, which is why a player in
 * an old lineup who is no longer in that squad always has an explanation: a transfer or a retirement.
 */
final class CareerSimulator {

    static final String GK = "goalkeeper";
    static final String DF = "defender";
    static final String MF = "midfielder";
    static final String FW = "forward";

    private static final Map<String, Integer> SQUAD_SHAPE = Map.of(GK, 3, DF, 8, MF, 8, FW, 6);
    private static final Map<String, Integer> MIN_AT_POSITION = Map.of(GK, 2, DF, 5, MF, 5, FW, 3);
    private static final Map<String, Integer> MAX_AT_POSITION = Map.of(GK, 4, DF, 12, MF, 12, FW, 9);
    private static final Map<String, Integer> NATIONAL_POOL_SHAPE = Map.of(GK, 4, DF, 10, MF, 10, FW, 8);
    private static final List<String> POSITIONS = List.of(GK, DF, MF, FW);

    private static final int MIN_SQUAD = 22;
    private static final int MAX_SQUAD = 30;

    /** Share of each country's clubs' squads who are locals. */
    private static final Map<String, Double> DOMESTIC_SHARE = Map.of("Spain", 0.62, "Italy", 0.42, "Poland", 0.60,
            "England", 0.35, "Germany", 0.45, "France", 0.45, "Portugal", 0.40, "Turkey", 0.50);

    private final Rng rng;
    private final Catalog catalog;
    private final MasterData master;
    private final NameFactory names;

    final List<TeamState> clubs = new ArrayList<>();
    final Map<String, TeamState> clubByName = new LinkedHashMap<>();
    final Map<String, TeamState> nations = new LinkedHashMap<>();
    final List<PlayerState> players = new ArrayList<>();
    final List<Coach> coaches = new ArrayList<>();

    private final Map<String, List<PlayerState>> playersByNation = new LinkedHashMap<>();
    private final List<PendingTransfer> transfers = new ArrayList<>();
    private List<Catalog.NationDef> uefaNations;

    private record PendingTransfer(PlayerState player, TeamState from, TeamState to, String type, LocalDate date,
                                   LocalDate loanEnd, BigDecimal amount, Currency currency) {
    }

    CareerSimulator(Rng rng, Catalog catalog, MasterData master, NameFactory names) {
        this.rng = rng;
        this.catalog = catalog;
        this.master = master;
        this.names = names;
    }

    // ---------------------------------------------------------------- setup

    void simulate() {
        uefaNations = catalog.nations.stream().filter(n -> n.confederation().equals("UEFA")).toList();
        createTeams();
        createSquads();
        topUpNationalPools();
        runTransferWindows();
        planTrainingInjuries();
        assignCoaches();
    }

    private void createTeams() {
        for (Catalog.ClubDef def : catalog.clubs) {
            Team team = new Team(def.name(), "club", master.region(def.country()));
            team.setShortName(def.shortName());
            team.setFoundedYear((short) def.founded());
            TeamState state = new TeamState(team, def.country(), def.tier(),
                    master.stadium(def.stadium(), def.city()), false, def.division());
            clubs.add(state);
            clubByName.put(def.name(), state);
        }
        for (Catalog.NationDef def : catalog.nations) {
            Team team = new Team(def.name(), "national_team", master.region(def.name()));
            team.setShortName(def.code());
            team.setFoundedYear((short) def.founded());
            nations.put(def.name(), new TeamState(team, def.name(), 2, null, true, null));
            playersByNation.put(def.name(), new ArrayList<>());
        }
    }

    private void createSquads() {
        LocalDate start = SeedTime.HISTORY_START;
        for (TeamState club : clubs) {
            for (String position : POSITIONS) {
                for (int i = 0; i < SQUAD_SHAPE.get(position); i++) {
                    String nation = nationalityFor(club);
                    PlayerState player = newPlayer(position, nation, club.tier,
                            rng.birthDate(start, 17, 35), start);
                    addStint(player, club, start, null, false);
                    club.roster.add(player);
                }
            }
        }
    }

    private String nationalityFor(TeamState club) {
        double domestic = DOMESTIC_SHARE.getOrDefault(club.country, 0.65);
        if (rng.chance(domestic)) {
            return club.country;
        }
        double roll = rng.random.nextDouble();
        String confederation = roll < 0.55 ? "UEFA" : roll < 0.72 ? "CONMEBOL" : roll < 0.90 ? "CAF" : roll < 0.97 ? "AFC" : "CONCACAF";
        List<Catalog.NationDef> pool = catalog.nations.stream()
                .filter(n -> n.confederation().equals(confederation) && !n.name().equals(club.country)).toList();
        return rng.pick(pool).name();
    }

    /** Every nation can always name a squad. Those short of players get men from clubs outside the catalog. */
    private void topUpNationalPools() {
        for (Catalog.NationDef nation : catalog.nations) {
            for (String position : POSITIONS) {
                long have = playersByNation.get(nation.name()).stream().filter(p -> p.position.equals(position)).count();
                for (long i = have; i < NATIONAL_POOL_SHAPE.get(position); i++) {
                    newPlayer(position, nation.name(), 2, rng.birthDate(SeedTime.HISTORY_START, 18, 34), SeedTime.HISTORY_START);
                }
            }
        }
    }

    private PlayerState newPlayer(String position, String nation, int tier, LocalDate birthDate, LocalDate joined) {
        NameFactory.Person person = names.male(catalog.nationByName.get(nation).culture());
        Player player = new Player(person.firstName(), person.lastName(), birthDate, master.region(nation));
        player.setPrimaryPosition(position);
        player.setHeightCm((short) heightFor(position));
        player.setFoot(randomFoot());
        double quality = rng.gaussian(switch (tier) {
            case 1 -> 2.0;
            case 2 -> 1.0;
            default -> 0.0;
        }, 0.6);
        PlayerState state = new PlayerState(player, position, nation, quality, joined);
        players.add(state);
        playersByNation.get(nation).add(state);
        return state;
    }

    private int heightFor(String position) {
        double mean = switch (position) {
            case GK -> 190;
            case DF -> 184;
            case FW -> 180;
            default -> 177;
        };
        return (int) Math.round(Math.max(163, Math.min(203, rng.gaussian(mean, 5))));
    }

    private Foot randomFoot() {
        int roll = rng.between(1, 100);
        return roll <= 77 ? master.right : roll <= 97 ? master.left : master.both;
    }

    private void addStint(PlayerState player, TeamState club, LocalDate from, LocalDate to, boolean loan) {
        player.stints.add(new PlayerState.Stint(club, from, to, loan));
        if (!club.members.contains(player)) {
            club.members.add(player);
        }
    }

    // ---------------------------------------------------------------- transfer windows

    private void runTransferWindows() {
        for (int year : SeedTime.SEASONS) {
            returnLoans(LocalDate.of(year, 6, 30));
            if (year > SeedTime.SEASONS.getFirst()) {
                retirePlayers(year);
            }
            moveWindow(LocalDate.of(year, 7, 1), LocalDate.of(year, 8, 31), 0.17);
            if (year < SeedTime.SEASONS.getLast()) {
                moveWindow(LocalDate.of(year + 1, 1, 1), LocalDate.of(year + 1, 1, 31), 0.04);
            }
        }
    }

    private void returnLoans(LocalDate endOfSeason) {
        for (PlayerState player : players) {
            if (player.loanParent != null && !player.currentStint().to.isAfter(endOfSeason)) {
                TeamState borrower = player.currentStint().club;
                TeamState parent = player.loanParent;
                borrower.roster.remove(player);
                parent.roster.add(player);
                addStint(player, parent, player.currentStint().to.plusDays(1), null, false);
                player.loanParent = null;
            }
        }
    }

    private void retirePlayers(int year) {
        LocalDate lastDay = LocalDate.of(year, 7, 31);
        LocalDate seasonEnd = LocalDate.of(year, 6, 30);
        for (PlayerState player : new ArrayList<>(players)) {
            if (player.retiredOn != null || player.joinedHistory.getYear() == year) {
                continue;
            }
            int age = player.ageOn(seasonEnd);
            if (age < 34 || !(age >= 40 || rng.chance((age - 33) * 0.18))) {
                continue;
            }
            player.retiredOn = lastDay;
            PlayerState.Stint stint = player.currentStint();
            LocalDate joined = LocalDate.of(year, 7, 1);
            int tier = 2;
            TeamState club = stint == null ? null : stint.club;
            if (club != null) {
                stint.to = lastDay;
                club.roster.remove(player);
                tier = club.tier;
            }
            // The academy fills the place, keeping the squad's shape and the nation's pool intact.
            PlayerState youth = newPlayer(player.position, player.nation, tier, rng.birthDate(joined, 17, 19), joined);
            if (club != null) {
                addStint(youth, club, joined, null, false);
                club.roster.add(youth);
            }
        }
    }

    private void moveWindow(LocalDate from, LocalDate to, double share) {
        List<PlayerState> candidates = new ArrayList<>();
        for (TeamState club : clubs) {
            for (PlayerState player : club.roster) {
                if (player.loanParent == null) {
                    candidates.add(player);
                }
            }
        }
        for (PlayerState player : rng.sample(candidates, (int) (candidates.size() * share))) {
            TeamState source = player.currentStint().club;
            if (!canLose(source, player)) {
                continue;
            }
            TeamState target = chooseDestination(player, source);
            if (target != null) {
                move(player, source, target, rng.dateBetween(from, to));
            }
        }
    }

    private boolean canLose(TeamState club, PlayerState player) {
        return club.roster.size() > MIN_SQUAD && club.rosterCount(player.position) > MIN_AT_POSITION.get(player.position);
    }

    private boolean canTake(TeamState club, PlayerState player) {
        return club.roster.size() < MAX_SQUAD && club.rosterCount(player.position) < MAX_AT_POSITION.get(player.position);
    }

    /** Mostly within the country, and towards clubs of a level that suits the player's ability. */
    private TeamState chooseDestination(PlayerState player, TeamState source) {
        boolean domestic = rng.chance(0.55);
        List<TeamState> options = clubs.stream()
                .filter(c -> c != source && canTake(c, player))
                .filter(c -> !domestic || c.country.equals(source.country))
                .toList();
        if (options.isEmpty()) {
            return null;
        }
        return rng.weighted(options, c -> Math.exp(-Math.abs((3 - c.tier) - player.quality)));
    }

    private void move(PlayerState player, TeamState source, TeamState target, LocalDate date) {
        int age = player.ageOn(date);
        boolean summer = date.getMonth() != Month.JANUARY;
        String type;
        if (rng.chance(age <= 23 ? 0.32 : 0.08)) {
            type = "loan";
        } else if (rng.chance(age >= 31 ? 0.55 : 0.12)) {
            type = "free";
        } else {
            type = "permanent";
        }

        LocalDate loanEnd = null;
        BigDecimal amount = null;
        Currency currency = null;
        if (type.equals("loan")) {
            int endYear = summer ? date.getYear() + 1 : date.getYear();
            loanEnd = LocalDate.of(endYear, 6, 30);
            if (rng.chance(0.15)) {
                currency = master.currencyFor(target.country);
                amount = fee(target, age, 0.1, currency);
            }
        } else if (type.equals("permanent")) {
            currency = master.currencyFor(target.country);
            amount = fee(target, age, 1.0, currency);
        }

        player.currentStint().to = date.minusDays(1);
        source.roster.remove(player);
        target.roster.add(player);
        addStint(player, target, date, loanEnd, type.equals("loan"));
        player.loanParent = type.equals("loan") ? source : null;
        transfers.add(new PendingTransfer(player, source, target, type, date, loanEnd, amount, currency));
    }

    private BigDecimal fee(TeamState buyer, int age, double scale, Currency currency) {
        double typicalMillions = switch (buyer.tier) {
            case 1 -> 18;
            case 2 -> 4;
            default -> 0.6;
        };
        double ageFactor = age <= 24 ? 1.4 : age <= 28 ? 1.0 : age <= 31 ? 0.5 : 0.2;
        double euros = typicalMillions * 1_000_000 * ageFactor * scale * Math.exp(rng.gaussian(0, 0.7));
        double inCurrency = Math.max(50_000, euros * master.unitsPerEuro(currency));
        double rounded = Math.round(inCurrency / 10_000) * 10_000.0;
        return BigDecimal.valueOf(rounded).setScale(2, RoundingMode.HALF_UP);
    }

    // ---------------------------------------------------------------- injuries and coaches

    /** Injuries outside matches. Ones suffered in a match are added by MatchSimulator. */
    private void planTrainingInjuries() {
        for (PlayerState player : players) {
            LocalDate cursor = SeedTime.HISTORY_START;
            for (int year : SeedTime.SEASONS) {
                int count = rng.poisson(0.3);
                // A long injury can run into the next season, so the search continues from its end.
                LocalDate seasonStart = LocalDate.of(year, 7, 1);
                cursor = cursor.isAfter(seasonStart) ? cursor : seasonStart;
                for (int i = 0; i < count; i++) {
                    cursor = rng.dateBetween(cursor, cursor.plusDays(120));
                    Catalog.InjuryDef def = rng.weighted(catalog.injuries, Catalog.InjuryDef::weight);
                    LocalDate end = cursor.plusDays(rng.between(def.minDays(), def.maxDays()));
                    if (!cursor.isAfter(SeedTime.TODAY) && !cursor.isBefore(player.joinedHistory)
                            && (player.retiredOn == null || cursor.isBefore(player.retiredOn))) {
                        player.injuries.add(new PlayerState.InjuryRecord(cursor, end, def.description()));
                    }
                    cursor = end.plusDays(1);
                }
            }
        }
    }

    private void assignCoaches() {
        List<TeamState> teams = new ArrayList<>(clubs);
        teams.addAll(nations.values());
        List<Coach> spare = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            spare.add(newCoach());
        }
        for (TeamState team : teams) {
            team.coaches.add(new TeamState.CoachStint(newCoach(), SeedTime.HISTORY_START));
        }
        // In date order, so a dismissed coach can only take another job later on.
        List<LocalDate> changeDates = List.of(LocalDate.of(2023, 10, 16), LocalDate.of(2024, 2, 5),
                LocalDate.of(2024, 7, 1), LocalDate.of(2024, 10, 14), LocalDate.of(2025, 2, 3),
                LocalDate.of(2025, 7, 1), LocalDate.of(2025, 10, 13), LocalDate.of(2026, 2, 2),
                LocalDate.of(2026, 7, 20), LocalDate.of(2026, 10, 19));
        for (LocalDate date : changeDates) {
            boolean summer = date.getMonthValue() == 7;
            for (TeamState team : rng.shuffled(teams)) {
                // National teams change only after a tournament summer; clubs also dismiss mid-season.
                double chance = team.national ? (summer ? 0.15 : 0) : (summer ? 0.20 : 0.05);
                if (rng.chance(chance)) {
                    Coach outgoing = team.coaches.getLast().coach();
                    team.coaches.add(new TeamState.CoachStint(spare.removeFirst(), date));
                    spare.addLast(outgoing);
                }
            }
        }
    }

    private Coach newCoach() {
        Catalog.NationDef nation = rng.chance(0.7) ? rng.pick(uefaNations) : rng.pick(catalog.nations);
        NameFactory.Person person = names.male(nation.culture());
        Coach coach = new Coach(person.firstName(), person.lastName(), master.region(nation.name()));
        coach.setBirthDate(rng.birthDate(SeedTime.HISTORY_START, 36, 65));
        coaches.add(coach);
        return coach;
    }

    // ---------------------------------------------------------------- persistence

    /** Coaches come first because Team.coach_id points at them; the last stint is the current coach. */
    void persistPeopleAndTeams(Db db, EditorDirectory editors) {
        for (Coach coach : coaches) {
            db.save(coach, editors.masterDataEditor(), SeedTime.MASTER_DATA_AT);
        }
        List<TeamState> teams = new ArrayList<>(clubs);
        teams.addAll(nations.values());
        for (TeamState team : teams) {
            team.team.setCoach(team.coaches.getLast().coach());
            db.save(team.team, editors.masterDataEditor(), SeedTime.MASTER_DATA_AT);
        }
        for (PlayerState player : players) {
            db.save(player.player, editors.masterDataEditor(), playerRecordedAt(player));
        }
        db.endOfBatch();
        transfers.sort(Comparator.comparing(PendingTransfer::date));
        for (PendingTransfer pending : transfers) {
            Transfer transfer = new Transfer(pending.player().player, pending.from().team, pending.to().team,
                    master.transferTypes.get(pending.type()), pending.date());
            transfer.setLoanEndDate(pending.loanEnd());
            transfer.setAmount(pending.amount());
            transfer.setCurrency(pending.currency());
            db.save(transfer, editors.transferEditor(), pending.date().atTime(12, 0));
        }
        db.endOfBatch();
    }

    private static java.time.LocalDateTime playerRecordedAt(PlayerState player) {
        java.time.LocalDateTime joined = player.joinedHistory.minusDays(7).atTime(10, 0);
        return joined.isAfter(SeedTime.MASTER_DATA_AT) ? joined : SeedTime.MASTER_DATA_AT;
    }

    /** A club short of fit players on a match day calls up an academy player, as real clubs do. */
    PlayerState promoteFromAcademy(TeamState club, String position, LocalDate date, Db db, Editor editor) {
        PlayerState youth = newPlayer(position, club.country, club.tier, rng.birthDate(date, 17, 19), date);
        addStint(youth, club, date, null, false);
        db.save(youth.player, editor, date.minusDays(1).atTime(10, 0));
        return youth;
    }

    /** The national equivalent: someone from a club outside the catalog gets an emergency call-up. */
    PlayerState emergencyCallUp(TeamState nation, String position, LocalDate date, Db db, Editor editor) {
        PlayerState player = newPlayer(position, nation.country, 2, rng.birthDate(date, 19, 33), date);
        db.save(player.player, editor, date.minusDays(1).atTime(10, 0));
        return player;
    }

    List<PlayerState> nationPool(String nation) {
        return playersByNation.get(nation);
    }

    void persistInjuries(Db db, EditorDirectory editors) {
        for (PlayerState player : players) {
            player.injuries.sort(Comparator.comparing(PlayerState.InjuryRecord::start));
            for (PlayerState.InjuryRecord record : player.injuries) {
                Injury injury = new Injury(player.player, record.start(), record.description());
                // No end date means the player is still out on the reference date.
                injury.setEndDate(record.end().isAfter(SeedTime.TODAY) ? null : record.end());
                db.save(injury, editors.injuryEditor(), record.start().atTime(16, 0));
            }
        }
        db.endOfBatch();
    }

    /** One club at most, and the last call-up of the player's own nation, for anyone still playing. */
    void persistSquads(Db db, EditorDirectory editors, Map<TeamState, List<PlayerState>> lastNationalSquads) {
        Editor editor = editors.masterDataEditor();
        for (PlayerState player : players) {
            PlayerState.Stint stint = player.currentStint();
            if (stint != null && stint.contains(SeedTime.TODAY) && !player.isRetired(SeedTime.TODAY)) {
                db.save(new TeamSquad(stint.club.team, player.player), editor, stint.from.atTime(12, 0));
            }
        }
        lastNationalSquads.forEach((nation, squad) -> {
            for (PlayerState player : squad) {
                if (!player.isRetired(SeedTime.TODAY)) {
                    db.save(new TeamSquad(nation.team, player.player), editor, player.callUps.getLast().from().atTime(12, 0));
                }
            }
        });
        db.endOfBatch();
    }
}
