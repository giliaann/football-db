package pl.football.seeder.random;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/** The hand-written reference data from src/main/resources/seed, which Faker cannot provide. */
final class Catalog {

    record ClubDef(String name, String shortName, int founded, String country, String city, String stadium,
                   int capacity, String division, int tier) {
    }

    record NationDef(String name, String code, int founded, String confederation, String culture) {
    }

    record StadiumDef(String name, String city, String country, int capacity, String tag) {
    }

    record CompetitionDef(String key, String name, String format, String level, String region, String kind,
                          int bench, int substitutionLimit, int yellowLimit, String rules) {
    }

    /** Tournaments and UEFA finals have real dates here; leagues and cups get theirs from SeasonCalendar. */
    record EditionDef(String key, String season, String spec, LocalDate start, LocalDate end, List<String> hosts,
                      String stadiumTag, String finalStadium, String finalCity, String rules) {
    }

    record InjuryDef(String description, int minDays, int maxDays, int weight) {
    }

    final List<ClubDef> clubs;
    final List<NationDef> nations;
    final List<StadiumDef> stadiums;
    final List<CompetitionDef> competitions;
    final List<EditionDef> editions;
    final List<InjuryDef> injuries;
    final Map<String, NationDef> nationByName = new HashMap<>();
    final Map<String, CompetitionDef> competitionByKey = new HashMap<>();

    private Catalog() {
        clubs = read("clubs.csv", 9, r -> new ClubDef(r[0], r[1], Integer.parseInt(r[2]), r[3], r[4], r[5],
                Integer.parseInt(r[6]), r[7], Integer.parseInt(r[8])));
        nations = read("national_teams.csv", 5, r -> new NationDef(r[0], r[1], Integer.parseInt(r[2]), r[3], r[4]));
        stadiums = read("stadiums.csv", 5, r -> new StadiumDef(r[0], r[1], r[2], Integer.parseInt(r[3]), r[4]));
        competitions = read("competitions.csv", 10, r -> new CompetitionDef(r[0], r[1], r[2], r[3], r[4], r[5],
                Integer.parseInt(r[6]), Integer.parseInt(r[7]), Integer.parseInt(r[8]), r[9]));
        editions = read("editions.csv", 10, r -> new EditionDef(r[0], r[1], r[2], date(r[3]), date(r[4]),
                r[5].isEmpty() ? List.of() : Arrays.asList(r[5].split(",")), r[6], r[7], r[8], r[9]));
        injuries = read("injuries.csv", 4, r -> new InjuryDef(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]),
                Integer.parseInt(r[3])));
        nations.forEach(n -> nationByName.put(n.name(), n));
        competitions.forEach(c -> competitionByKey.put(c.key(), c));
    }

    static Catalog load() {
        return new Catalog();
    }

    EditionDef edition(String key, String season) {
        return editions.stream().filter(e -> e.key().equals(key) && e.season().equals(season)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No edition " + key + " " + season));
    }

    private static LocalDate date(String text) {
        return text.isEmpty() ? null : LocalDate.parse(text);
    }

    private static <T> List<T> read(String file, int columns, Function<String[], T> parser) {
        try (InputStream in = Catalog.class.getResourceAsStream("/seed/" + file)) {
            if (in == null) {
                throw new IllegalStateException("Missing catalog file " + file);
            }
            List<T> rows = new ArrayList<>();
            BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                // The last column is free text and may itself contain semicolons.
                String[] row = line.split(";", columns);
                if (row.length != columns) {
                    throw new IllegalStateException(file + ": expected " + columns + " columns in: " + line);
                }
                rows.add(parser.apply(row));
            }
            return List.copyOf(rows);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot read " + file, e);
        }
    }
}
