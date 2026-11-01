package pl.football.seeder.random;

import pl.football.seeder.entity.Referee;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The referees: domestic pools per league country, and neutral appointments for international matches. */
final class Officials {

    private static final List<String> LEAGUE_COUNTRIES = List.of("Spain", "Italy", "Poland");

    private final Rng rng;
    private final Catalog catalog;
    private final MasterData master;
    private final NameFactory names;
    private final Db db;
    private final EditorDirectory editors;
    private final Map<String, List<Referee>> byCountry = new LinkedHashMap<>();
    private final Map<String, List<Referee>> byConfederation = new LinkedHashMap<>();
    private final List<Referee> all = new ArrayList<>();
    private final Map<Referee, LocalDate> lastMatch = new HashMap<>();

    Officials(Rng rng, Catalog catalog, MasterData master, NameFactory names, Db db, EditorDirectory editors) {
        this.rng = rng;
        this.catalog = catalog;
        this.master = master;
        this.names = names;
        this.db = db;
        this.editors = editors;
        for (String country : LEAGUE_COUNTRIES) {
            for (int i = 0; i < 30; i++) {
                create(catalog.nationByName.get(country));
            }
        }
        createFrom("UEFA", 60);
        createFrom("AFC", 14);
        createFrom("CAF", 16);
        createFrom("CONMEBOL", 14);
        createFrom("CONCACAF", 8);
        createFrom("OFC", 2);
    }

    private void createFrom(String confederation, int count) {
        List<Catalog.NationDef> nations = catalog.nations.stream()
                .filter(n -> n.confederation().equals(confederation) && !LEAGUE_COUNTRIES.contains(n.name()))
                .toList();
        for (int i = 0; i < count; i++) {
            create(rng.pick(nations));
        }
    }

    private void create(Catalog.NationDef nation) {
        NameFactory.Person person = names.male(nation.culture());
        Referee referee = new Referee(person.firstName(), person.lastName(), master.region(nation.name()));
        referee.setBirthDate(rng.birthDate(SeedTime.HISTORY_START, 28, 50));
        db.save(referee, editors.masterDataEditor(), SeedTime.MASTER_DATA_AT);
        byCountry.computeIfAbsent(nation.name(), n -> new ArrayList<>()).add(referee);
        byConfederation.computeIfAbsent(nation.confederation(), n -> new ArrayList<>()).add(referee);
        all.add(referee);
    }

    Referee appoint(Fixture fixture) {
        String key = fixture.run.competition.key();
        boolean domestic = false;
        List<Referee> pool;
        switch (key) {
            case "laliga", "copa" -> {
                pool = byCountry.get("Spain");
                domestic = true;
            }
            case "seriea", "coppa" -> {
                pool = byCountry.get("Italy");
                domestic = true;
            }
            case "ekstraklasa", "puchar" -> {
                pool = byCountry.get("Poland");
                domestic = true;
            }
            case "ucl", "uel", "euro" -> pool = byConfederation.get("UEFA");
            case "afcon" -> pool = byConfederation.get("CAF");
            case "asia" -> pool = byConfederation.get("AFC");
            case "copaam" -> {
                pool = new ArrayList<>(byConfederation.get("CONMEBOL"));
                pool.addAll(byConfederation.get("CONCACAF"));
            }
            default -> pool = all;
        }
        LocalDate date = fixture.kickoff.toLocalDate();
        List<Referee> rested = suitable(pool, fixture, date, 2, !domestic);
        if (rested.isEmpty()) {
            rested = suitable(pool, fixture, date, 1, !domestic);
        }
        if (rested.isEmpty()) {
            throw new IllegalStateException("No referee available for " + fixture.home + " v " + fixture.away + " on " + date);
        }
        Referee referee = rng.pick(rested);
        lastMatch.put(referee, date);
        return referee;
    }

    private List<Referee> suitable(List<Referee> pool, Fixture fixture, LocalDate date, int rest, boolean neutralOnly) {
        List<Referee> result = new ArrayList<>();
        for (Referee referee : pool) {
            LocalDate last = lastMatch.get(referee);
            if (last != null && last.plusDays(rest).isAfter(date)) {
                continue;
            }
            String country = referee.getRegion().getName();
            if (neutralOnly && (country.equals(fixture.home.country) || country.equals(fixture.away.country))) {
                continue;
            }
            result.add(referee);
        }
        return result;
    }
}
