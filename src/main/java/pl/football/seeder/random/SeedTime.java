package pl.football.seeder.random;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Fixed points in time. The seeder never reads the system clock, so the data depends only on the seed. */
final class SeedTime {

    static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 1, 18, 30);
    static final LocalDate TODAY = NOW.toLocalDate();
    static final LocalDate HISTORY_START = LocalDate.of(2024, 6, 1);
    static final LocalDateTime GLOBAL_GRANT_AT = LocalDateTime.of(2024, 4, 1, 10, 0);
    static final LocalDateTime MASTER_DATA_AT = LocalDateTime.of(2024, 4, 15, 9, 0);

    /** Start years of the club seasons: 2024/25, 2025/26 and 2026/27 (in progress). */
    static final List<Integer> SEASONS = List.of(2024, 2025, 2026);
    static final int CURRENT_SEASON = SEASONS.size() - 1;

    private SeedTime() {
    }

    static String seasonName(int season) {
        int year = SEASONS.get(season);
        return "%d/%02d".formatted(year, (year + 1) % 100);
    }

    static LocalDateTime notAfterNow(LocalDateTime time) {
        return time.isAfter(NOW) ? NOW : time;
    }
}
