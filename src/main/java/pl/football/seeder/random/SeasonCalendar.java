package pl.football.seeder.random;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Sorts one club season's weeks into international breaks, UEFA weeks, cup weeks and league rounds.
 * The result keeps competitions out of each other's way, which is what gives every team its rest days.
 */
final class SeasonCalendar {

    /** A league round: the weekend closing the given week, or midweek if the calendar is short of weekends. */
    record RoundSlot(LocalDate week, boolean midweek) {
    }

    /** Weeks of each stage of a cup, each given by its Monday; the semi-finals may use both. */
    record CupWeeks(LocalDate round32, LocalDate round16, LocalDate quarterFinals, LocalDate semiFinalFirst,
                    LocalDate semiFinalSecond, LocalDate fin) {
    }

    private final int year;
    private final Set<LocalDate> breaks = new HashSet<>();
    private final Set<LocalDate> uefaWeeks = new HashSet<>();
    final boolean leaguePhaseFormat;

    SeasonCalendar(int year) {
        this.year = year;
        leaguePhaseFormat = year >= 2024;
        for (LocalDate date : List.of(of(9, 7), of(10, 12), of(11, 16), of(12, 28), next(3, 23))) {
            breaks.add(week(date));
        }
        uefaWeeks.addAll(uefaFirstStageWeeks());
        for (List<LocalDate> legs : List.of(uefaPlayOffWeeks(), uefaRound16Weeks(), uefaQuarterFinalWeeks(), uefaSemiFinalWeeks())) {
            uefaWeeks.addAll(legs);
        }
    }

    static LocalDate week(LocalDate date) {
        return date.with(DayOfWeek.MONDAY);
    }

    private LocalDate of(int month, int day) {
        return LocalDate.of(year, month, day);
    }

    private LocalDate next(int month, int day) {
        return LocalDate.of(year + 1, month, day);
    }

    private List<LocalDate> weeks(LocalDate... dates) {
        List<LocalDate> weeks = new ArrayList<>();
        for (LocalDate date : dates) {
            weeks.add(week(date));
        }
        return weeks;
    }

    /** The group stage (six matchdays) or, from 2024/25, the league phase (eight). */
    List<LocalDate> uefaFirstStageWeeks() {
        return leaguePhaseFormat
                ? weeks(of(9, 16), of(9, 30), of(10, 21), of(11, 4), of(11, 25), of(12, 9), next(1, 20), next(1, 27))
                : weeks(of(9, 19), of(10, 3), of(10, 24), of(11, 7), of(11, 28), of(12, 12));
    }

    List<LocalDate> uefaPlayOffWeeks() {
        return weeks(next(2, 10), next(2, 17));
    }

    List<LocalDate> uefaRound16Weeks() {
        return weeks(next(3, 3), next(3, 10));
    }

    List<LocalDate> uefaQuarterFinalWeeks() {
        return weeks(next(4, 7), next(4, 14));
    }

    List<LocalDate> uefaSemiFinalWeeks() {
        return weeks(next(4, 28), next(5, 5));
    }

    CupWeeks cupWeeks() {
        return new CupWeeks(firstFreeWeek(of(10, 20)), firstFreeWeek(of(12, 1)), firstFreeWeek(next(1, 7)),
                firstFreeWeek(next(2, 1)), firstFreeWeek(next(3, 15)), firstFreeWeek(next(5, 10)));
    }

    private LocalDate firstFreeWeek(LocalDate from) {
        LocalDate candidate = week(from);
        while (breaks.contains(candidate) || uefaWeeks.contains(candidate)) {
            candidate = candidate.plusWeeks(1);
        }
        return candidate;
    }

    /** Weekends first; only when they run out does a league use free midweeks, as real calendars do. */
    List<RoundSlot> leagueRounds(String leagueKey, int rounds, Set<LocalDate> cupWeeks) {
        boolean winterBreak = leagueKey.equals("ekstraklasa");
        LocalDate first = week(winterBreak ? of(7, 20) : of(8, year == 2026 ? 19 : 12));
        LocalDate last = week(next(5, 18));

        List<RoundSlot> slots = new ArrayList<>();
        List<RoundSlot> midweeks = new ArrayList<>();
        for (LocalDate week = first; !week.isAfter(last); week = week.plusWeeks(1)) {
            if (breaks.contains(week) || (winterBreak && isWinterBreak(week))) {
                continue;
            }
            slots.add(new RoundSlot(week, false));
            // The UEFA finals fall in the last weeks of the season, so midweek rounds stop before them.
            if (!uefaWeeks.contains(week) && !cupWeeks.contains(week) && week.isBefore(next(5, 4))) {
                midweeks.add(new RoundSlot(week, true));
            }
        }
        while (slots.size() > rounds) {
            slots.removeLast();
        }
        // Spread across the free weeks, avoiding the early ones first used by the opening rounds.
        for (int i = midweeks.size() - 1; i >= 0 && slots.size() < rounds; i -= 2) {
            slots.add(midweeks.get(i));
        }
        if (slots.size() < rounds) {
            throw new IllegalStateException(leagueKey + " " + year + ": only " + slots.size() + " slots for " + rounds + " rounds");
        }
        slots.sort((a, b) -> a.week().equals(b.week())
                ? Boolean.compare(b.midweek(), a.midweek())
                : a.week().compareTo(b.week()));
        return slots;
    }

    private boolean isWinterBreak(LocalDate week) {
        return !week.isBefore(of(12, 14)) && week.isBefore(next(1, 26));
    }
}
