package pl.football.seeder;

import jakarta.persistence.EntityManager;
import net.datafaker.Faker;
import pl.football.seeder.entity.Appearance;
import pl.football.seeder.entity.AuditedEntity;
import pl.football.seeder.entity.Coach;
import pl.football.seeder.entity.Competition;
import pl.football.seeder.entity.Currency;
import pl.football.seeder.entity.Edition;
import pl.football.seeder.entity.EditionParticipation;
import pl.football.seeder.entity.Editor;
import pl.football.seeder.entity.EventType;
import pl.football.seeder.entity.Foot;
import pl.football.seeder.entity.Injury;
import pl.football.seeder.entity.Match;
import pl.football.seeder.entity.MatchEvent;
import pl.football.seeder.entity.Permission;
import pl.football.seeder.entity.PermissionAssignment;
import pl.football.seeder.entity.Player;
import pl.football.seeder.entity.Referee;
import pl.football.seeder.entity.Region;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Stage;
import pl.football.seeder.entity.Team;
import pl.football.seeder.entity.TeamSquad;
import pl.football.seeder.entity.Transfer;
import pl.football.seeder.entity.TransferType;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


/**
 * Inserts a small, handwritten, internally consistent data set covering every table.
 *
 * <p>People are fictional. The order of the steps follows the foreign keys. Each step returns
 * everything it created, so a step can later be replaced by a generator without touching the others.
 */
public final class DataSeeder {

    /** Not a real hash: seeded editors are not meant to be able to log in. */
    private static final String PLACEHOLDER_PASSWORD_HASH = "seed-placeholder-not-a-real-hash";

    private final EntityManager em;
    private Editor currentEditor;
    private final Random random;
    private final Faker faker;
    private final Set<String> usedLogins = new HashSet<>();

    public DataSeeder(EntityManager em, long seed) {
        this.em = em;
        this.random = new Random(seed);
        this.faker = new Faker(Locale.forLanguageTag("pl"), random);
    }

    public record Editors(Editor system, Editor admin, Editor matchEditor, Editor blocked, List<Editor> others) {
    }

    public record Regions(Region poland, Region spain, Region germany, Region europe, List<Region> others) {
        List<Region> countries() {
            List<Region> all = new ArrayList<>(List.of(poland, spain, germany));
            all.addAll(others);
            return all;
        }
    }
    public record Feet(Foot left, Foot right, Foot both) {
    }

    public record TransferTypes(TransferType permanent, TransferType loan, TransferType free) {
    }

    public record Currencies(Currency pln, Currency eur, Currency gbp) {
    }

    public record EventTypes(EventType goal, EventType penaltyGoal, EventType ownGoal, EventType assist,
                             EventType yellowCard, EventType redCard, EventType substitutionOn,
                             EventType substitutionOff, EventType foul) {
    }

    public record Referees(Referee first, Referee second, Referee third, List<Referee> other) {
    }

    public record Club(Team team, Stadium stadium, Coach coach,
                       Player goalkeeper, Player defender, Player midfielder, Player forward) {
    }

    public record Clubs(Club legia, Club lech, Club wisla, Club rakow, Team nationalTeam, Coach nationalCoach) {
        List<Club> all() {
            return List.of(legia, lech, wisla, rakow);
        }
    }

    public record Competitions(Competition league, Competition cup, Competition euro) {
    }

    public record Editions(Edition league2024, Edition league2026, Edition cup2024, Edition euro2024) {
    }

    public record Stages(Stage league2024, Stage league2026, Stage cupSemifinal, Stage cupFinal, Stage euroGroup) {
    }

    /** One team's players and coach in a match. */
    public record Lineup(Appearance goalkeeper, Appearance defender, Appearance midfielder, Appearance forward,
                         Appearance coach) {
    }

    /** A match that has kicked off, so it has lineups and events. */
    public record PlayedMatch(Match match, Lineup home, Lineup away, List<MatchEvent> events) {
    }

    public record Matches(PlayedMatch leagueWin, PlayedMatch goallessDraw, PlayedMatch cupSemifinalOnPenalties,
                          PlayedMatch cupSemifinalInRegulation, Match postponedFinal, PlayedMatch inProgress,
                          Match scheduled) {
    }

    public record Permissions(Permission administrator, Permission competitionAdmin, Permission leagueMatchEditor,
                              Permission semifinalEditor, Permission transferEditor) {
    }

    public record SeedData(Editors editors, Regions regions, Feet feet, TransferTypes transferTypes,
                           Currencies currencies, EventTypes eventTypes, Referees referees, Clubs clubs,
                           Competitions competitions, Editions editions, Stages stages,
                           List<EditionParticipation> participations, List<TeamSquad> squads,
                           List<Injury> injuries, List<Transfer> transfers, Matches matches,
                           Permissions permissions, List<PermissionAssignment> permissionAssignments) {
    }

    public SeedData seedAll() {
        Editors editors = seedEditors();
        currentEditor = editors.system();

        Regions regions = seedRegions();
        Feet feet = seedFeet();
        TransferTypes transferTypes = seedTransferTypes();
        Currencies currencies = seedCurrencies();
        EventTypes eventTypes = seedEventTypes();

        Referees referees = seedReferees(regions);
        Clubs clubs = seedClubs(regions, feet);
        Competitions competitions = seedCompetitions(regions);
        Editions editions = seedEditions(competitions);
        Stages stages = seedStages(editions);

        List<EditionParticipation> participations = seedParticipations(clubs, editions);
        List<TeamSquad> squads = seedSquads(clubs);
        List<Injury> injuries = seedInjuries(clubs);
        List<Transfer> transfers = seedTransfers(clubs, transferTypes, currencies);

        currentEditor = editors.matchEditor();
        Matches matches = seedMatches(clubs, stages, referees, eventTypes);

        currentEditor = editors.admin();
        Permissions permissions = seedPermissions(editions, matches);
        List<PermissionAssignment> assignments = seedPermissionAssignments(permissions, editors);

        return new SeedData(editors, regions, feet, transferTypes, currencies, eventTypes, referees, clubs,
                competitions, editions, stages, participations, squads, injuries, transfers, matches,
                permissions, assignments);
    }

    // ---------------------------------------------------------------- editors

    private Editors seedEditors() {
        Editor system = bootstrapSystemEditor();
        currentEditor = system;
        usedLogins.add("system");

        Editor admin = save(randomEditor(false));
        admin.setLastLoginAt(LocalDateTime.now().minusHours(1 + random.nextInt(48)));

        Editor matchEditor = save(randomEditor(false));
        Editor blocked = save(randomEditor(true));

        List<Editor> others = new ArrayList<>();
        for (int i = 0; i < 70; i++) {
            others.add(save(randomEditor(random.nextInt(10) == 0)));
        }
        return new Editors(system, admin, matchEditor, blocked, others);
    }

    private Editor randomEditor(boolean blocked){
        String first, last, login;
        do{
            first = faker.name().firstName();
            last = faker.name().lastName();
            login = (ascii(first) + "." + ascii(last)).toLowerCase();
        }while (!usedLogins.add(login));

        Editor editor = new Editor(first, last, login, login + "@example.com", PLACEHOLDER_PASSWORD_HASH, blocked);
        if(random.nextInt(5) != 0){
            editor.setLastLoginAt(LocalDateTime.now().minusDays(random.nextInt(90)).minusMinutes(random.nextInt(1440)));
        }
        return editor;
    }


    private static String ascii(String s){
        String t = s.replace('ł', 'l').replace('Ł', 'L');
        return Normalizer.normalize(t, Normalizer.Form.NFD).replaceAll("\\p{M}", "").replaceAll("[^A-Za-z]","");
    }
    /**
     * The first editor has to reference itself through a NOT NULL, non-deferrable foreign key, which
     * persist() cannot do because the id is only known after the insert. Taking the id from the
     * sequence up front lets one statement use it for both columns.
     */
    private Editor bootstrapSystemEditor() {
        Number id = (Number) em.createNativeQuery("""
                        INSERT INTO editor (editor_id, first_name, last_name, login, email, password_hash,
                                            is_blocked, last_edited_by_id)
                        OVERRIDING SYSTEM VALUE
                        SELECT id, ?1, ?2, ?3, ?4, ?5, false, id
                        FROM (SELECT nextval(pg_get_serial_sequence('editor', 'editor_id')) AS id) s
                        RETURNING editor_id
                        """)
                .setParameter(1, "System")
                .setParameter(2, "Seeder")
                .setParameter(3, "system")
                .setParameter(4, "system@example.com")
                .setParameter(5, PLACEHOLDER_PASSWORD_HASH)
                .getSingleResult();
        return em.find(Editor.class, id.intValue());
    }

    // ---------------------------------------------------------------- dictionaries

    private Regions seedRegions() {
        Region polska = save(new Region("Polska", "country"));
        Region hiszpania = save(new Region("Hiszpania", "country"));
        Region niemcy = save(new Region("Niemcy", "country"));
        Region europa = save(new Region("Europa", "continent"));

        Locale pl = Locale.forLanguageTag("pl");
        Set<String> used = new HashSet<>(Set.of("Polska", "Hiszpania", "Niemcy", "Europa"));
        List<Region> other = new ArrayList<>();
        for (String code : Locale.getISOCountries()){
            String name = Locale.of("", code).getDisplayCountry(pl);
            if(used.add(name)){
                other.add(save(new Region(name, "country")));
            }
        }
        return new Regions(polska, hiszpania, niemcy, europa, other);
    }

    private static final List<String> EUROPEAN_CORE = List.of(
            "Niemcy", "Hiszpania", "Francja", "Włochy", "Czechy", "Słowacja",
            "Ukraina", "Chorwacja", "Serbia", "Anglia", "Holandia", "Portugalia");

    private Region randomCountry(Regions regions) {
        int roll = random.nextInt(100);
        if (roll < 70) {
            return regions.poland();
        }
        List<Region> all = regions.countries();
        if (roll < 90) {
            List<Region> core = all.stream().filter(r -> EUROPEAN_CORE.contains(r.getName())).toList();
            if (!core.isEmpty()) {
                return core.get(random.nextInt(core.size()));
            }
        }
        return all.get(random.nextInt(all.size()));
    }

    private Feet seedFeet() {
        return new Feet(save(new Foot("left")), save(new Foot("right")), save(new Foot("both")));
    }

    private TransferTypes seedTransferTypes() {
        return new TransferTypes(
                save(new TransferType("permanent")),
                save(new TransferType("loan")),
                save(new TransferType("free")));
    }

    private Currencies seedCurrencies() {
        return new Currencies(
                save(new Currency("PLN", "Polski złoty")),
                save(new Currency("EUR", "Euro")),
                save(new Currency("GBP", "Funt szterling")));
    }

    private EventTypes seedEventTypes() {
        return new EventTypes(
                save(new EventType("goal", "goal", (short) 1)),
                save(new EventType("penalty_goal", "goal", (short) 1)),
                save(new EventType("own_goal", "goal", (short) -1)),
                save(new EventType("assist", "assist", (short) 0)),
                save(new EventType("yellow_card", "card", (short) 0)),
                save(new EventType("red_card", "card", (short) 0)),
                save(new EventType("substitution_on", "substitution", (short) 0)),
                save(new EventType("substitution_off", "substitution", (short) 0)),
                save(new EventType("foul", "other", (short) 0)));
    }

    // ---------------------------------------------------------------- people, clubs, venues

    private Referees seedReferees(Regions regions) {
        Referee first = new Referee("Marek", "Zieliński", regions.poland());
        first.setBirthDate(LocalDate.of(1980, 3, 12));
        Referee second = new Referee("Carlos", "Ortega", regions.spain());
        second.setBirthDate(LocalDate.of(1983, 11, 2));
        Referee third = new Referee("Lukas", "Brandt", regions.germany());

        save(first);
        save(second);
        save(third);

        List<Referee> others = new ArrayList<>();
        for (int i = 0; i < 150; i++) {
            others.add(save(randomReferee(regions)));
        }
        return new Referees(first, second, third, others);
    }

    private Referee randomReferee(Regions regions) {
        Referee referee = new Referee(faker.name().firstName(), faker.name().lastName(), randomCountry(regions));
        referee.setBirthDate(faker.timeAndDate().birthday(30, 55));
        return referee;
    }

    private Clubs seedClubs(Regions regions, Feet feet) {
        Region poland = regions.poland();
        Region germany = regions.germany();
        Region spain = regions.spain();

        Club legia = seedClub(
                newTeam("Legia Warszawa", "LEG", 1916, "club", poland),
                newStadium("Stadion Wojska Polskiego", "Warszawa", 31_103, poland),
                newCoach("Tomasz", "Lis", LocalDate.of(1975, 4, 9), poland),
                newPlayer("Adam", "Sowa", "goalkeeper", 192, feet.right(), poland),
                newPlayer("Bartosz", "Wilk", "defender", 186, feet.left(), poland),
                newPlayer("Cezary", "Bąk", "midfielder", 178, feet.both(), germany),
                newPlayer("Dawid", "Kruk", "forward", 183, feet.right(), spain));

        Club lech = seedClub(
                newTeam("Lech Poznań", "LPO", 1922, "club", poland),
                newStadium("Stadion Miejski w Poznaniu", "Poznań", 42_837, poland),
                newCoach("Piotr", "Mazur", LocalDate.of(1972, 8, 30), poland),
                newPlayer("Emil", "Dudek", "goalkeeper", 192, feet.right(), poland),
                newPlayer("Filip", "Gil", "defender", 186, feet.left(), poland),
                newPlayer("Grzegorz", "Jeż", "midfielder", 178, feet.both(), germany),
                newPlayer("Hubert", "Kos", "forward", 183, feet.right(), spain));

        Club wisla = seedClub(
                newTeam("Wisła Kraków", "WIS", 1906, "club", poland),
                newStadium("Stadion im. Henryka Reymana", "Kraków", 33_130, poland),
                newCoach("Robert", "Czajka", LocalDate.of(1978, 1, 17), poland),
                newPlayer("Igor", "Łoś", "goalkeeper", 192, feet.right(), poland),
                newPlayer("Jakub", "Ryś", "defender", 186, feet.left(), poland),
                newPlayer("Kamil", "Szpak", "midfielder", 178, feet.both(), germany),
                newPlayer("Leon", "Żuraw", "forward", 183, feet.right(), spain));

        Club rakow = seedClub(
                newTeam("Raków Częstochowa", "RAK", 1921, "club", poland),
                newStadium("Miejski Stadion Piłkarski Raków", "Częstochowa", null, poland),
                newCoach("Michał", "Sikora", LocalDate.of(1981, 12, 5), poland),
                newPlayer("Mateusz", "Orzeł", "goalkeeper", 192, feet.right(), poland),
                newPlayer("Norbert", "Sęp", "defender", 186, feet.left(), poland),
                newPlayer("Oskar", "Dzik", "midfielder", 178, feet.both(), germany),
                newPlayer("Paweł", "Borsuk", "forward", 183, feet.right(), spain));

        Coach nationalCoach = save(newCoach("Stefan", "Gołąb", LocalDate.of(1970, 6, 21), poland));
        Team nationalTeam = newTeam("Reprezentacja Polski", "POL", 1919, "national_team", poland);
        nationalTeam.setCoach(nationalCoach);
        save(nationalTeam);

        return new Clubs(legia, lech, wisla, rakow, nationalTeam, nationalCoach);
    }

    private Club seedClub(Team team, Stadium stadium, Coach coach,
                          Player goalkeeper, Player defender, Player midfielder, Player forward) {
        save(stadium);
        save(coach);
        team.setCoach(coach);
        save(team);
        save(goalkeeper);
        save(defender);
        save(midfielder);
        save(forward);
        return new Club(team, stadium, coach, goalkeeper, defender, midfielder, forward);
    }

    private Team newTeam(String name, String shortName, int foundedYear, String teamType, Region region) {
        Team team = new Team(name, teamType, region);
        team.setShortName(shortName);
        team.setFoundedYear((short) foundedYear);
        return team;
    }

    private Stadium newStadium(String name, String city, Integer capacity, Region region) {
        Stadium stadium = new Stadium(name, city, region);
        stadium.setCapacity(capacity);
        return stadium;
    }

    private Coach newCoach(String firstName, String lastName, LocalDate birthDate, Region region) {
        Coach coach = new Coach(firstName, lastName, region);
        coach.setBirthDate(birthDate);
        return coach;
    }

    private Player newPlayer(String firstName, String lastName, String position, int heightCm, Foot foot, Region region) {
        Player player = new Player(firstName, lastName, LocalDate.of(1998, 5, 14), region);
        player.setPrimaryPosition(position);
        player.setHeightCm((short) heightCm);
        player.setFoot(foot);
        return player;
    }

    // ---------------------------------------------------------------- competitions

    private Competitions seedCompetitions(Regions regions) {
        return new Competitions(
                save(new Competition("Ekstraklasa", "league", "club", regions.poland())),
                save(new Competition("Puchar Polski", "cup", "club", regions.poland())),
                save(new Competition("Mistrzostwa Europy", "mixed", "national", regions.europe())));
    }

    private Editions seedEditions(Competitions competitions) {
        Edition league2024 = new Edition(competitions.league(), "2024/25", LocalDate.of(2024, 7, 19), (short) 5);
        league2024.setEndDate(LocalDate.of(2025, 5, 24));
        league2024.setAdvancementRules("Mistrz zajmuje pierwsze miejsce w tabeli.");

        // Still running, so it has no end date.
        Edition league2026 = new Edition(competitions.league(), "2026/27", LocalDate.of(2026, 7, 17), (short) 5);
        league2026.setAdvancementRules("Mistrz zajmuje pierwsze miejsce w tabeli.");

        // The final is postponed, so this edition has no end date either.
        Edition cup2024 = new Edition(competitions.cup(), "2024/25", LocalDate.of(2024, 8, 1), (short) 5);
        cup2024.setAdvancementRules("Pojedyncze mecze, przy remisie dogrywka i rzuty karne.");

        Edition euro2024 = new Edition(competitions.euro(), "2024", LocalDate.of(2024, 6, 14), (short) 5);
        euro2024.setEndDate(LocalDate.of(2024, 7, 14));

        return new Editions(save(league2024), save(league2026), save(cup2024), save(euro2024));
    }

    private Stages seedStages(Editions editions) {
        return new Stages(
                save(new Stage(editions.league2024(), "Sezon zasadniczy", "league", (short) 1)),
                save(new Stage(editions.league2026(), "Sezon zasadniczy", "league", (short) 1)),
                save(new Stage(editions.cup2024(), "Półfinał", "knockout", (short) 1)),
                save(new Stage(editions.cup2024(), "Finał", "knockout", (short) 2)),
                save(new Stage(editions.euro2024(), "Faza grupowa", "group", (short) 1)));
    }

    /**
     * Points and goals match the results in {@link #seedMatches}; penalties do not count as goals.
     * The only 2026/27 match with goals is still in progress, so nothing is counted for that edition yet.
     */
    private List<EditionParticipation> seedParticipations(Clubs clubs, Editions editions) {
        Team legia = clubs.legia().team();
        Team lech = clubs.lech().team();
        Team wisla = clubs.wisla().team();
        Team rakow = clubs.rakow().team();
        Edition league2024 = editions.league2024();
        Edition league2026 = editions.league2026();
        Edition cup = editions.cup2024();

        return List.of(
                participate(legia, league2024, 3, 2, 1, "Sezon zasadniczy", (short) 1),
                participate(wisla, league2024, 1, 0, 0, "Sezon zasadniczy", (short) 2),
                participate(rakow, league2024, 1, 0, 0, "Sezon zasadniczy", (short) 3),
                participate(lech, league2024, 0, 1, 2, "Sezon zasadniczy", (short) 4),

                participate(legia, league2026, 0, 0, 0, null, null),
                participate(lech, league2026, 0, 0, 0, null, null),
                participate(wisla, league2026, 0, 0, 0, null, null),
                participate(rakow, league2026, 0, 0, 0, null, null),

                participate(legia, cup, 0, 1, 1, "Finał", null),
                participate(wisla, cup, 0, 2, 0, "Finał", null),
                participate(rakow, cup, 0, 1, 1, "Półfinał", null),
                participate(lech, cup, 0, 0, 2, "Półfinał", null),

                participate(clubs.nationalTeam(), editions.euro2024(), 0, 0, 0, "Faza grupowa", null));
    }

    private EditionParticipation participate(Team team, Edition edition, int points, int goalsFor, int goalsAgainst,
                                             String stageReached, Short finalPosition) {
        EditionParticipation participation = new EditionParticipation(team, edition);
        participation.setPoints((short) points);
        participation.setGoalsFor((short) goalsFor);
        participation.setGoalsAgainst((short) goalsAgainst);
        participation.setStageReached(stageReached);
        participation.setFinalPosition(finalPosition);
        return save(participation);
    }

    private List<TeamSquad> seedSquads(Clubs clubs) {
        List<TeamSquad> squads = new ArrayList<>();
        for (Club club : clubs.all()) {
            squads.add(save(new TeamSquad(club.team(), club.goalkeeper())));
            squads.add(save(new TeamSquad(club.team(), club.defender())));
            squads.add(save(new TeamSquad(club.team(), club.midfielder())));
            squads.add(save(new TeamSquad(club.team(), club.forward())));
        }
        squads.add(save(new TeamSquad(clubs.nationalTeam(), clubs.legia().goalkeeper())));
        squads.add(save(new TeamSquad(clubs.nationalTeam(), clubs.lech().defender())));
        return squads;
    }

    private List<Injury> seedInjuries(Clubs clubs) {
        Injury healed = new Injury(clubs.legia().defender(), LocalDate.of(2025, 2, 3), "Naderwanie mięśnia dwugłowego");
        healed.setEndDate(LocalDate.of(2025, 3, 10));

        Injury healedAnkle = new Injury(clubs.lech().forward(), LocalDate.of(2025, 9, 20), "Skręcenie stawu skokowego");
        healedAnkle.setEndDate(LocalDate.of(2025, 10, 5));

        // No end date: the player is still injured.
        Injury ongoing = new Injury(clubs.wisla().midfielder(), LocalDate.of(2026, 10, 12), "Uszkodzenie więzadła kolanowego");

        return List.of(save(healed), save(healedAnkle), save(ongoing));
    }

    /** All transfers predate the matches, and every player is in the destination club's squad. */
    private List<Transfer> seedTransfers(Clubs clubs, TransferTypes types, Currencies currencies) {
        Transfer permanent = new Transfer(clubs.legia().forward(), clubs.lech().team(), clubs.legia().team(),
                types.permanent(), LocalDate.of(2023, 7, 1));
        permanent.setAmount(new BigDecimal("1500000.00"));
        permanent.setCurrency(currencies.eur());

        Transfer loan = new Transfer(clubs.rakow().midfielder(), clubs.wisla().team(), clubs.rakow().team(),
                types.loan(), LocalDate.of(2024, 1, 15));
        loan.setLoanEndDate(LocalDate.of(2027, 6, 30));

        Transfer domesticFee = new Transfer(clubs.wisla().defender(), clubs.rakow().team(), clubs.wisla().team(),
                types.permanent(), LocalDate.of(2023, 8, 20));
        domesticFee.setAmount(new BigDecimal("750000.00"));
        domesticFee.setCurrency(currencies.pln());

        Transfer free = new Transfer(clubs.lech().midfielder(), clubs.legia().team(), clubs.lech().team(),
                types.free(), LocalDate.of(2024, 2, 1));

        return List.of(save(permanent), save(loan), save(domesticFee), save(free));
    }

    // ---------------------------------------------------------------- matches

    private Matches seedMatches(Clubs clubs, Stages stages, Referees referees, EventTypes types) {
        return new Matches(
                seedLeagueWin(clubs, stages, referees, types),
                seedGoallessDraw(clubs, stages, referees, types),
                seedCupSemifinalOnPenalties(clubs, stages, referees, types),
                seedCupSemifinalInRegulation(clubs, stages, referees, types),
                seedPostponedFinal(clubs, stages, referees),
                seedInProgress(clubs, stages, referees, types),
                seedScheduled(clubs, stages, referees));
    }

    /** Legia 2:1 Lech, with an assist, a card, a substitution and stoppage time. */
    private PlayedMatch seedLeagueWin(Clubs clubs, Stages stages, Referees referees, EventTypes types) {
        Match match = newMatch(stages.league2024(), clubs.legia(), clubs.lech(), referees.first(),
                LocalDateTime.of(2024, 9, 14, 18, 0), "finished");
        playRegulation(match);
        match.setHomeGoals((short) 2);
        match.setAwayGoals((short) 1);
        save(match);

        Lineup home = seedLineup(match, clubs.legia());
        Lineup away = seedLineup(match, clubs.lech());

        List<MatchEvent> events = List.of(
                event(home.forward(), types.goal(), "first_half", 23, null, 1, match),
                event(home.defender(), types.assist(), "first_half", 23, null, 2, match),
                event(away.defender(), types.yellowCard(), "first_half", 45, 2, 3, match),
                event(away.forward(), types.goal(), "second_half", 58, null, 4, match),
                event(home.defender(), types.substitutionOff(), "second_half", 70, null, 5, match),
                event(home.midfielder(), types.substitutionOn(), "second_half", 70, null, 6, match),
                event(home.midfielder(), types.goal(), "second_half", 84, null, 7, match));
        return new PlayedMatch(match, home, away, events);
    }

    /** Wisła 0:0 Raków: the only goal is disallowed, so it does not count towards the score. */
    private PlayedMatch seedGoallessDraw(Clubs clubs, Stages stages, Referees referees, EventTypes types) {
        Match match = newMatch(stages.league2024(), clubs.wisla(), clubs.rakow(), referees.second(),
                LocalDateTime.of(2024, 9, 15, 15, 0), "finished");
        playRegulation(match);
        save(match);

        Lineup home = seedLineup(match, clubs.wisla());
        Lineup away = seedLineup(match, clubs.rakow());

        MatchEvent disallowed = newEvent(home.forward(), types.goal(), "second_half", 66, null, 1, match);
        disallowed.setIsCancelled(true);
        disallowed.setDescription("Bramka anulowana po analizie VAR: spalony");
        save(disallowed);

        return new PlayedMatch(match, home, away, List.of(disallowed));
    }

    /** Legia 1:1 Raków after extra time, 3:0 on penalties. */
    private PlayedMatch seedCupSemifinalOnPenalties(Clubs clubs, Stages stages, Referees referees, EventTypes types) {
        LocalDateTime kickoff = LocalDateTime.of(2025, 4, 2, 20, 30);
        Match match = newMatch(stages.cupSemifinal(), clubs.legia(), clubs.rakow(), referees.third(), kickoff, "finished");
        playRegulation(match);
        match.setExtraTimeFirstHalfStart(kickoff.plusMinutes(115));
        match.setExtraTimeFirstHalfEnd(kickoff.plusMinutes(131));
        match.setExtraTimeSecondHalfStart(kickoff.plusMinutes(133));
        match.setExtraTimeSecondHalfEnd(kickoff.plusMinutes(149));
        match.setFinishedAt(kickoff.plusMinutes(165));
        match.setHomeGoals((short) 1);
        match.setAwayGoals((short) 1);
        match.setHomePenalties((short) 3);
        match.setAwayPenalties((short) 0);
        match.setAdvancingSide("home");
        save(match);

        Lineup home = seedLineup(match, clubs.legia());
        Lineup away = seedLineup(match, clubs.rakow());

        List<MatchEvent> events = List.of(
                event(away.forward(), types.goal(), "second_half", 52, null, 1, match),
                event(home.forward(), types.goal(), "extra_time_1", 104, null, 2, match),
                event(home.forward(), types.penaltyGoal(), "penalties", 120, null, 3, match),
                event(home.defender(), types.penaltyGoal(), "penalties", 120, null, 4, match),
                event(home.goalkeeper(), types.penaltyGoal(), "penalties", 120, null, 5, match));
        return new PlayedMatch(match, home, away, events);
    }

    /** Wisła 2:0 Lech. */
    private PlayedMatch seedCupSemifinalInRegulation(Clubs clubs, Stages stages, Referees referees, EventTypes types) {
        Match match = newMatch(stages.cupSemifinal(), clubs.wisla(), clubs.lech(), referees.first(),
                LocalDateTime.of(2025, 4, 3, 20, 30), "finished");
        playRegulation(match);
        match.setHomeGoals((short) 2);
        match.setAdvancingSide("home");
        save(match);

        Lineup home = seedLineup(match, clubs.wisla());
        Lineup away = seedLineup(match, clubs.lech());

        List<MatchEvent> events = List.of(
                event(home.forward(), types.goal(), "first_half", 11, null, 1, match),
                event(home.defender(), types.goal(), "second_half", 77, null, 2, match));
        return new PlayedMatch(match, home, away, events);
    }

    private Match seedPostponedFinal(Clubs clubs, Stages stages, Referees referees) {
        return save(newMatch(stages.cupFinal(), clubs.legia(), clubs.wisla(), referees.second(),
                LocalDateTime.of(2025, 5, 2, 16, 0), "postponed"));
    }

    /** Lech 1:0 Raków, currently in the second half. */
    private PlayedMatch seedInProgress(Clubs clubs, Stages stages, Referees referees, EventTypes types) {
        LocalDateTime kickoff = LocalDateTime.of(2026, 10, 31, 18, 0);
        Match match = newMatch(stages.league2026(), clubs.lech(), clubs.rakow(), referees.third(), kickoff, "in_progress");
        match.setFirstHalfStart(kickoff);
        match.setFirstHalfEnd(kickoff.plusMinutes(47));
        match.setSecondHalfStart(kickoff.plusMinutes(62));
        match.setMatchPeriod("second_half");
        match.setHomeGoals((short) 1);
        save(match);

        Lineup home = seedLineup(match, clubs.lech());
        Lineup away = seedLineup(match, clubs.rakow());

        List<MatchEvent> events = List.of(
                event(home.forward(), types.goal(), "first_half", 30, null, 1, match),
                event(away.defender(), types.yellowCard(), "second_half", 49, null, 2, match));
        return new PlayedMatch(match, home, away, events);
    }

    private Match seedScheduled(Clubs clubs, Stages stages, Referees referees) {
        return save(newMatch(stages.league2026(), clubs.legia(), clubs.wisla(), referees.first(),
                LocalDateTime.of(2027, 3, 6, 17, 30), "scheduled"));
    }

    private Match newMatch(Stage stage, Club home, Club away, Referee referee, LocalDateTime kickoff, String status) {
        return new Match(stage, home.team(), away.team(), home.stadium(), referee, kickoff, status);
    }

    private void playRegulation(Match match) {
        LocalDateTime kickoff = match.getScheduledAt();
        match.setFirstHalfStart(kickoff);
        match.setFirstHalfEnd(kickoff.plusMinutes(47));
        match.setSecondHalfStart(kickoff.plusMinutes(62));
        match.setSecondHalfEnd(kickoff.plusMinutes(110));
        match.setFinishedAt(kickoff.plusMinutes(110));
    }

    /** The midfielder starts on the bench; the coach is recorded as a non-starting participant. */
    private Lineup seedLineup(Match match, Club club) {
        Appearance goalkeeper = appearPlayer(match, club, club.goalkeeper(), 1, "goalkeeper", true);
        Appearance defender = appearPlayer(match, club, club.defender(), 4, "defender", true);
        Appearance midfielder = appearPlayer(match, club, club.midfielder(), 14, "midfielder", false);
        Appearance forward = appearPlayer(match, club, club.forward(), 9, "forward", true);

        Appearance coach = new Appearance(match, club.team(), false);
        coach.setCoach(club.coach());
        save(coach);

        return new Lineup(goalkeeper, defender, midfielder, forward, coach);
    }

    private Appearance appearPlayer(Match match, Club club, Player player, int shirt, String position, boolean starting) {
        Appearance appearance = new Appearance(match, club.team(), starting);
        appearance.setPlayer(player);
        appearance.setShirtNumber((short) shirt);
        appearance.setPosition(position);
        return save(appearance);
    }

    private MatchEvent event(Appearance appearance, EventType type, String period, int minute, Integer stoppage,
                             int sequence, Match match) {
        return save(newEvent(appearance, type, period, minute, stoppage, sequence, match));
    }

    private MatchEvent newEvent(Appearance appearance, EventType type, String period, int minute, Integer stoppage,
                                int sequence, Match match) {
        MatchEvent event = new MatchEvent(appearance, type, period, (short) minute, sequence, false);
        if (stoppage != null) {
            event.setStoppageMinute(stoppage.shortValue());
        }
        event.setRecordedAt(match.getScheduledAt().plusMinutes(minute));
        return event;
    }

    // ---------------------------------------------------------------- permissions

    private Permissions seedPermissions(Editions editions, Matches matches) {
        Permission administrator = new Permission("administrator");
        administrator.setDescription("Pełny dostęp do systemu");

        Permission competitionAdmin = new Permission("competition_administrator");
        competitionAdmin.setEdition(editions.league2026());
        competitionAdmin.setDescription("Zarządzanie edycją Ekstraklasy 2026/27");

        Permission leagueMatchEditor = new Permission("match_editor");
        leagueMatchEditor.setMatch(matches.leagueWin().match());
        leagueMatchEditor.setDescription("Edycja przebiegu jednego meczu");

        Permission semifinalEditor = new Permission("match_editor");
        semifinalEditor.setMatch(matches.cupSemifinalOnPenalties().match());
        semifinalEditor.setDescription("Edycja przebiegu półfinału pucharu");

        Permission transferEditor = new Permission("transfer_editor");
        transferEditor.setDescription("Edycja transferów");

        return new Permissions(save(administrator), save(competitionAdmin), save(leagueMatchEditor),
                save(semifinalEditor), save(transferEditor));
    }

    private List<PermissionAssignment> seedPermissionAssignments(Permissions permissions, Editors editors) {
        Editor system = editors.system();
        Editor admin = editors.admin();
        Editor matchEditor = editors.matchEditor();

        return List.of(
                assign(permissions.administrator(), admin, system, LocalDateTime.of(2024, 6, 1, 8, 0), null),
                assign(permissions.competitionAdmin(), admin, system, LocalDateTime.of(2026, 7, 1, 8, 0), null),
                assign(permissions.leagueMatchEditor(), matchEditor, admin, LocalDateTime.of(2024, 9, 1, 12, 0), null),
                assign(permissions.semifinalEditor(), matchEditor, admin, LocalDateTime.of(2025, 3, 20, 12, 0), null),
                // Revoked from the editor who was later blocked.
                assign(permissions.transferEditor(), editors.blocked(), admin,
                        LocalDateTime.of(2025, 1, 10, 10, 0), LocalDateTime.of(2025, 6, 1, 10, 0)));
    }

    private PermissionAssignment assign(Permission permission, Editor editor, Editor grantedBy,
                                        LocalDateTime grantedAt, LocalDateTime revokedAt) {
        PermissionAssignment assignment = new PermissionAssignment(permission, editor, grantedBy);
        // Set explicitly because the schema requires revoked_at >= created_at.
        assignment.setCreatedAt(grantedAt);
        assignment.setRevokedAt(revokedAt);
        return save(assignment);
    }

    // ---------------------------------------------------------------- helpers

    private <T extends AuditedEntity> T save(T entity) {
        entity.setLastEditedBy(currentEditor);
        em.persist(entity);
        return entity;
    }
}
