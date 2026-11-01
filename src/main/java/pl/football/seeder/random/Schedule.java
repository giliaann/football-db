package pl.football.seeder.random;

import pl.football.seeder.entity.Stadium;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Who is already booked on which day, so that a new fixture can see whether a date works. */
final class Schedule {

    private static final int MIN_DAYS_BETWEEN_MATCHES = 2;

    private final Map<TeamState, TreeSet<LocalDate>> teamDates = new HashMap<>();
    private final Map<Stadium, Set<LocalDate>> stadiumDates = new HashMap<>();

    boolean teamCanPlay(TeamState team, LocalDate date) {
        TreeSet<LocalDate> dates = teamDates.get(team);
        if (dates == null) {
            return true;
        }
        LocalDate before = dates.floor(date);
        LocalDate after = dates.ceiling(date);
        return (before == null || before.plusDays(MIN_DAYS_BETWEEN_MATCHES).compareTo(date) <= 0)
                && (after == null || date.plusDays(MIN_DAYS_BETWEEN_MATCHES).compareTo(after) <= 0);
    }

    boolean stadiumFree(Stadium stadium, LocalDate date) {
        return !stadiumDates.getOrDefault(stadium, Set.of()).contains(date);
    }

    void book(TeamState home, TeamState away, Stadium stadium, LocalDate date) {
        teamDates.computeIfAbsent(home, t -> new TreeSet<>()).add(date);
        teamDates.computeIfAbsent(away, t -> new TreeSet<>()).add(date);
        reserveStadium(stadium, date);
    }

    void reserveStadium(Stadium stadium, LocalDate date) {
        stadiumDates.computeIfAbsent(stadium, s -> new HashSet<>()).add(date);
    }
}
