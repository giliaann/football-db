package pl.football.seeder.random;

import pl.football.seeder.entity.Currency;
import pl.football.seeder.entity.Editor;
import pl.football.seeder.entity.EventType;
import pl.football.seeder.entity.Foot;
import pl.football.seeder.entity.Region;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.TransferType;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Regions, dictionaries and stadiums: everything that exists before people and matches, in English. */
final class MasterData {

    /** ISO codes that have no football association of their own, or that FIFA splits up (GB). */
    private static final Set<String> NOT_FOOTBALL_COUNTRIES = Set.of("AQ", "AX", "BL", "BQ", "BV", "CC", "CX", "EH",
            "FK", "FM", "GB", "GF", "GG", "GL", "GP", "GS", "HM", "IM", "IO", "JE", "KI", "MF", "MH", "MQ", "NF", "NR",
            "NU", "PM", "PN", "PW", "RE", "SH", "SJ", "TF", "TK", "TV", "UM", "VA", "WF", "YT");

    /** Names as football uses them, where they differ from what the JDK returns. */
    private static final Map<String, String> COUNTRY_NAMES = Map.ofEntries(
            Map.entry("BA", "Bosnia and Herzegovina"), Map.entry("CD", "DR Congo"), Map.entry("CG", "Congo"),
            Map.entry("CI", "Ivory Coast"), Map.entry("CV", "Cape Verde"), Map.entry("CZ", "Czech Republic"),
            Map.entry("HK", "Hong Kong"), Map.entry("IE", "Republic of Ireland"), Map.entry("MM", "Myanmar"),
            Map.entry("MO", "Macau"), Map.entry("PS", "Palestine"), Map.entry("TR", "Turkey"),
            Map.entry("TT", "Trinidad and Tobago"), Map.entry("AG", "Antigua and Barbuda"),
            Map.entry("KN", "Saint Kitts and Nevis"), Map.entry("LC", "Saint Lucia"),
            Map.entry("VC", "Saint Vincent and the Grenadines"), Map.entry("ST", "Sao Tome and Principe"));

    private static final List<String> HOME_NATIONS = List.of("England", "Scotland", "Wales", "Northern Ireland");

    private static final List<String> CONTINENTS = List.of("Europe", "Asia", "Africa", "North America",
            "South America", "Oceania", "World");

    /** Fees are stored in the currency of the buying club's country; everything else pays in euros. */
    private static final Map<String, String> CURRENCY_BY_COUNTRY = Map.ofEntries(
            Map.entry("England", "GBP"), Map.entry("Scotland", "GBP"), Map.entry("Poland", "PLN"),
            Map.entry("Switzerland", "CHF"), Map.entry("Denmark", "DKK"), Map.entry("Norway", "NOK"),
            Map.entry("Sweden", "SEK"), Map.entry("Czech Republic", "CZK"), Map.entry("Hungary", "HUF"));

    private static final Map<String, Double> UNITS_PER_EURO = Map.of("EUR", 1.0, "GBP", 0.86, "PLN", 4.3, "CHF", 0.95,
            "DKK", 7.46, "NOK", 11.5, "SEK", 11.3, "CZK", 25.0, "HUF", 390.0);

    final Map<String, Region> regions = new HashMap<>();
    final Map<String, Currency> currencies = new HashMap<>();
    final Map<String, Stadium> stadiums = new HashMap<>();
    final Map<String, EventType> eventTypes = new HashMap<>();
    final Map<String, TransferType> transferTypes = new HashMap<>();
    final Foot left;
    final Foot right;
    final Foot both;

    private final Db db;
    private final Editor system;

    MasterData(Db db, Editor system, Catalog catalog) {
        this.db = db;
        this.system = system;
        seedRegions();
        left = db.save(new Foot("left"), system, at());
        right = db.save(new Foot("right"), system, at());
        both = db.save(new Foot("both"), system, at());
        for (String name : List.of("permanent", "loan", "free")) {
            transferTypes.put(name, db.save(new TransferType(name), system, at()));
        }
        seedCurrencies();
        seedEventTypes();
        seedStadiums(catalog);
    }

    Region region(String name) {
        Region region = regions.get(name);
        if (region == null) {
            throw new IllegalStateException("Unknown region: " + name);
        }
        return region;
    }

    Stadium stadium(String name, String city) {
        Stadium stadium = stadiums.get(name + "|" + city);
        if (stadium == null) {
            throw new IllegalStateException("Unknown stadium: " + name + ", " + city);
        }
        return stadium;
    }

    Currency currencyFor(String country) {
        return currencies.get(CURRENCY_BY_COUNTRY.getOrDefault(country, "EUR"));
    }

    double unitsPerEuro(Currency currency) {
        return UNITS_PER_EURO.get(currency.getCode());
    }

    private void seedRegions() {
        Set<String> names = new HashSet<>();
        for (String code : new java.util.TreeSet<>(Set.of(Locale.getISOCountries()))) {
            if (!NOT_FOOTBALL_COUNTRIES.contains(code)) {
                String name = COUNTRY_NAMES.getOrDefault(code, Locale.of("", code).getDisplayCountry(Locale.ENGLISH));
                if (names.add(name)) {
                    regions.put(name, db.save(new Region(name, "country"), system, at()));
                }
            }
        }
        for (String name : HOME_NATIONS) {
            regions.put(name, db.save(new Region(name, "country"), system, at()));
        }
        // The schema only knows countries and continents, so World is stored as a continent.
        for (String name : CONTINENTS) {
            regions.put(name, db.save(new Region(name, "continent"), system, at()));
        }
    }

    private void seedCurrencies() {
        for (String code : new java.util.TreeSet<>(UNITS_PER_EURO.keySet())) {
            String name = java.util.Currency.getInstance(code).getDisplayName(Locale.ENGLISH);
            currencies.put(code, db.save(new Currency(code, name), system, at()));
        }
    }

    private void seedEventTypes() {
        String[][] types = {
                {"goal", "goal"}, {"penalty_goal", "goal"}, {"own_goal", "goal"}, {"assist", "assist"},
                {"yellow_card", "card"}, {"red_card", "card"}, {"substitution_on", "substitution"},
                {"substitution_off", "substitution"}, {"foul", "other"}, {"penalty_missed", "other"}};
        for (String[] type : types) {
            eventTypes.put(type[0], db.save(new EventType(type[0], type[1]), system, at()));
        }
    }

    private void seedStadiums(Catalog catalog) {
        for (Catalog.ClubDef club : catalog.clubs) {
            addStadium(club.stadium(), club.city(), club.capacity(), club.country());
        }
        for (Catalog.StadiumDef stadium : catalog.stadiums) {
            addStadium(stadium.name(), stadium.city(), stadium.capacity(), stadium.country());
        }
    }

    private void addStadium(String name, String city, int capacity, String country) {
        String key = name + "|" + city;
        if (!stadiums.containsKey(key)) {
            Stadium stadium = new Stadium(name, city, region(country));
            stadium.setCapacity(capacity);
            stadiums.put(key, db.save(stadium, system, at()));
        }
    }

    private static LocalDateTime at() {
        return SeedTime.MASTER_DATA_AT;
    }
}
