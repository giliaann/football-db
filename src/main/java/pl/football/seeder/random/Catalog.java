package pl.football.seeder.random;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/** Hand-written reference data from src/main/resources/seed: real clubs, nations, stadiums and competitions. */
final class Catalog {

    /** tier: 1 elite, 2 established, 3 small club. division: the club's league at the start of the history. */
    record ClubDef(String name, String shortName, int founded, String country, String city, String stadium,
                   int capacity, String division, int tier) {
    }

    record NationDef(String name, String code, int founded, String confederation, String culture) {
    }

    /** tag: a tournament's host pool, or FINAL for neutral final venues. */
    record StadiumDef(String name, String city, String country, int capacity, String tag) {
    }

    record CompetitionDef(String key, String name, String format, String level, String region, int bench,
                          int substitutionLimit, String rules) {
    }

    record InjuryDef(String description, int minDays, int maxDays, int weight) {
    }

    final List<ClubDef> clubs;
    final List<NationDef> nations;
    final List<StadiumDef> stadiums;
    final List<CompetitionDef> competitions;
    final List<InjuryDef> injuries;

    private Catalog() {
        clubs = read("clubs.csv", 9, r -> new ClubDef(r[0], r[1], Integer.parseInt(r[2]), r[3], r[4], r[5],
                Integer.parseInt(r[6]), r[7], Integer.parseInt(r[8])));
        nations = read("national_teams.csv", 5, r -> new NationDef(r[0], r[1], Integer.parseInt(r[2]), r[3], r[4]));
        stadiums = read("stadiums.csv", 5, r -> new StadiumDef(r[0], r[1], r[2], Integer.parseInt(r[3]), r[4]));
        competitions = read("competitions.csv", 10, r -> new CompetitionDef(r[0], r[1], r[2], r[3], r[4],
                Integer.parseInt(r[6]), Integer.parseInt(r[7]), r[9]));
        injuries = read("injuries.csv", 4, r -> new InjuryDef(r[0], Integer.parseInt(r[1]), Integer.parseInt(r[2]),
                Integer.parseInt(r[3])));
    }

    static Catalog load() {
        return new Catalog();
    }

    NationDef nation(String name) {
        return nations.stream().filter(n -> n.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No national team " + name));
    }

    CompetitionDef competition(String key) {
        return competitions.stream().filter(c -> c.key().equals(key)).findFirst()
                .orElseThrow(() -> new IllegalStateException("No competition " + key));
    }

    /** Reads a semicolon-separated file; lines starting with # are comments. */
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
                // The last column is free text and may contain semicolons itself.
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
