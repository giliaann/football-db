package pl.football.seeder.random;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** The fixed "now" of the data set, so that a seed always produces the same rows, whenever it is run. */
final class SeedTime {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 18, 30);
    static final LocalDate TODAY = NOW.toLocalDate();

    /** The day before the first simulated transfer window. Squads on this day have no transfer rows. */
    static final LocalDate HISTORY_START = LocalDate.of(2023, 6, 30);

    /** When the dictionaries and master data are recorded as created. */
    static final LocalDateTime MASTER_DATA_AT = LocalDateTime.of(2023, 6, 1, 9, 0);

    /** First calendar year of every simulated club season. */
    static final List<Integer> SEASONS = List.of(2023, 2024, 2025, 2026);

    private SeedTime() {
    }

    static String seasonName(int startYear) {
        return "%d/%02d".formatted(startYear, (startYear + 1) % 100);
    }
}
