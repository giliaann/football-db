package pl.football.seeder.random;

import pl.football.seeder.entity.Currency;
import pl.football.seeder.entity.EventType;
import pl.football.seeder.entity.Foot;
import pl.football.seeder.entity.Region;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.TransferType;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Regions, small dictionaries and stadiums: everything that exists before people and matches. */
final class Dictionaries {

    private static final List<String> CONTINENTS = List.of("Europe", "Asia", "Africa", "North America",
            "South America", "Oceania", "World");

    /** Transfer fees are stored in the currency of the buying club's country, otherwise in euros. */
    private static final Map<String, String> CURRENCY_BY_COUNTRY = Map.of("Poland", "PLN", "England", "GBP",
            "Scotland", "GBP", "Switzerland", "CHF", "Czech Republic", "CZK", "Denmark", "DKK", "Sweden", "SEK",
            "Norway", "NOK", "Hungary", "HUF");

    private static final Map<String, Double> UNITS_PER_EURO = Map.of("EUR", 1.0, "PLN", 4.3, "GBP", 0.86,
            "CHF", 0.95, "CZK", 25.0, "DKK", 7.46, "SEK", 11.3, "NOK", 11.5, "HUF", 390.0);

    /** name and category of every event type. */
    private static final List<List<String>> EVENT_TYPES = List.of(List.of("goal", "goal"),
            List.of("penalty_goal", "goal"), List.of("own_goal", "goal"), List.of("assist", "assist"),
            List.of("yellow_card", "card"), List.of("red_card", "card"), List.of("substitution_on", "substitution"),
            List.of("substitution_off", "substitution"), List.of("foul", "other"), List.of("penalty_missed", "other"));

    /** Stadiums from stadiums.csv that the seeder uses: Copa América venues and neutral final venues. */
    private static final Set<String> USED_TAGS = Set.of("COPA2024", "FINAL");

    final Map<String, Region> regions = new HashMap<>();
    final Map<String, Foot> feet = new HashMap<>();
    final Map<String, TransferType> transferTypes = new HashMap<>();
    final Map<String, Currency> currencies = new HashMap<>();
    final Map<String, EventType> eventTypes = new HashMap<>();
    private final Map<String, Stadium> stadiums = new HashMap<>();
    private final Map<String, List<Stadium>> stadiumsByTag = new HashMap<>();
    private final Map<Stadium, String> countryOfStadium = new HashMap<>();

    Dictionaries(Db db, Editors editors, Catalog catalog) {
        LocalDateTime at = SeedTime.MASTER_DATA_AT;
        Set<String> countries = new TreeSet<>();
        catalog.nations.forEach(n -> countries.add(n.name()));
        catalog.clubs.forEach(c -> countries.add(c.country()));
        catalog.stadiums.forEach(s -> countries.add(s.country()));
        for (String name : countries) {
            regions.put(name, db.save(new Region(name, "country"), editors.system, at));
        }
        // The schema knows only countries and continents, so "World" is stored as a continent.
        for (String name : CONTINENTS) {
            regions.put(name, db.save(new Region(name, "continent"), editors.system, at));
        }
        for (String name : List.of("right", "left", "both")) {
            feet.put(name, db.save(new Foot(name), editors.system, at));
        }
        for (String name : List.of("permanent", "loan", "free")) {
            transferTypes.put(name, db.save(new TransferType(name), editors.system, at));
        }
        for (String code : new TreeSet<>(UNITS_PER_EURO.keySet())) {
            String name = java.util.Currency.getInstance(code).getDisplayName(Locale.ENGLISH);
            currencies.put(code, db.save(new Currency(code, name), editors.system, at));
        }
        for (List<String> type : EVENT_TYPES) {
            eventTypes.put(type.get(0), db.save(new EventType(type.get(0), type.get(1)), editors.system, at));
        }
        for (Catalog.ClubDef club : catalog.clubs) {
            addStadium(db, editors, club.stadium(), club.city(), club.country(), club.capacity(), null);
        }
        for (Catalog.StadiumDef stadium : catalog.stadiums) {
            if (USED_TAGS.contains(stadium.tag())) {
                addStadium(db, editors, stadium.name(), stadium.city(), stadium.country(), stadium.capacity(),
                        stadium.tag());
            }
        }
    }

    Region region(String name) {
        Region region = regions.get(name);
        if (region == null) {
            throw new IllegalStateException("Unknown region " + name);
        }
        return region;
    }

    Stadium stadium(String name, String city) {
        return stadiums.get(name + "|" + city);
    }

    List<Stadium> tagged(String tag) {
        return stadiumsByTag.getOrDefault(tag, List.of());
    }

    String countryOf(Stadium stadium) {
        return countryOfStadium.get(stadium);
    }

    Currency currencyFor(String country) {
        return currencies.get(CURRENCY_BY_COUNTRY.getOrDefault(country, "EUR"));
    }

    double unitsPerEuro(Currency currency) {
        return UNITS_PER_EURO.get(currency.getCode());
    }

    private void addStadium(Db db, Editors editors, String name, String city, String country, int capacity,
                            String tag) {
        String key = name + "|" + city;
        Stadium stadium = stadiums.get(key);
        if (stadium == null) {
            stadium = new Stadium(name, city, region(country));
            stadium.setCapacity(capacity);
            db.save(stadium, editors.masterData(), SeedTime.MASTER_DATA_AT);
            stadiums.put(key, stadium);
            countryOfStadium.put(stadium, country);
        }
        if (tag != null) {
            stadiumsByTag.computeIfAbsent(tag, t -> new ArrayList<>()).add(stadium);
        }
    }
}
