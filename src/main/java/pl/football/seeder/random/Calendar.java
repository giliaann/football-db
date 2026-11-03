package pl.football.seeder.random;

import pl.football.seeder.entity.Referee;
import pl.football.seeder.entity.Stadium;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The date template and the bookings. Leagues play at weekends, the Champions League and the cups
 * in different midweeks, so competitions never meet. Bookings make sure a stadium, a team or a
 * referee never has two matches at the same time, as the uk_match_* constraints require.
 */
final class Calendar {

    /** When a match of a league round starts, relative to the round's Saturday. */
    record Slot(int dayOffset, LocalTime time) {
    }

    /**
     * Every round uses the first slots, so it always has matches on Sunday at 17:00, 17:30 and 18:15.
     * At "now" (Sunday 18:30) those are in the second half, at half-time and in the first half.
     */
    static final List<Slot> LEAGUE_SLOTS = List.of(
            new Slot(1, LocalTime.of(17, 0)), new Slot(1, LocalTime.of(17, 30)), new Slot(1, LocalTime.of(18, 15)),
            new Slot(0, LocalTime.of(15, 30)), new Slot(0, LocalTime.of(18, 0)), new Slot(0, LocalTime.of(20, 45)),
            new Slot(1, LocalTime.of(12, 30)), new Slot(1, LocalTime.of(15, 0)), new Slot(1, LocalTime.of(20, 45)),
            new Slot(-1, LocalTime.of(20, 45)), new Slot(0, LocalTime.of(13, 0)), new Slot(2, LocalTime.of(20, 45)));

    static final List<LocalTime> EVENING = List.of(LocalTime.of(21, 0), LocalTime.of(18, 45), LocalTime.of(20, 0),
            LocalTime.of(19, 0), LocalTime.of(17, 30));

    private final Map<Object, Set<LocalDateTime>> busy = new HashMap<>();

    /** The first Saturday on or after 15 August: the first league weekend. */
    static LocalDate leagueStart(int year) {
        LocalDate date = LocalDate.of(year, 8, 15);
        while (date.getDayOfWeek() != DayOfWeek.SATURDAY) {
            date = date.plusDays(1);
        }
        return date;
    }

    static LocalDate saturday(int year, int week) {
        return leagueStart(year).plusWeeks(week);
    }

    /** A Tuesday, Wednesday or Thursday right after the given weekend. */
    static LocalDate midweek(int year, int week, DayOfWeek day) {
        return saturday(year, week).plusDays(day.getValue() + 1);
    }

    /** One Saturday per round, skipping the winter break from 20 December to 6 January. */
    static List<LocalDate> leagueWeekends(int year, int rounds) {
        List<LocalDate> weekends = new java.util.ArrayList<>();
        for (LocalDate saturday = leagueStart(year); weekends.size() < rounds; saturday = saturday.plusWeeks(1)) {
            boolean winterBreak = (saturday.getMonthValue() == 12 && saturday.getDayOfMonth() >= 20)
                    || (saturday.getMonthValue() == 1 && saturday.getDayOfMonth() <= 6);
            if (!winterBreak) {
                weekends.add(saturday);
            }
        }
        return weekends;
    }

    /**
     * Books the first of the candidate times (days times hours, starting at {@code offset}) when the
     * stadium and both teams are free.
     */
    LocalDateTime kickoff(Stadium stadium, TeamInfo home, TeamInfo away, List<LocalDate> days, List<LocalTime> times,
                          int offset) {
        int candidates = days.size() * times.size();
        for (int i = 0; i < candidates; i++) {
            int k = (offset + i) % candidates;
            LocalDateTime at = days.get(k / times.size()).atTime(times.get(k % times.size()));
            if (free(stadium, at) && free(home, at) && free(away, at)) {
                book(stadium, at);
                book(home, at);
                book(away, at);
                return at;
            }
        }
        throw new IllegalStateException("No free time for " + home + " v " + away + " on " + days);
    }

    Referee referee(List<Referee> pool, LocalDateTime at, Rng rng) {
        for (Referee referee : rng.shuffled(pool)) {
            if (free(referee, at)) {
                book(referee, at);
                return referee;
            }
        }
        throw new IllegalStateException("No free referee at " + at);
    }

    private boolean free(Object resource, LocalDateTime at) {
        return !busy.getOrDefault(resource, Set.of()).contains(at);
    }

    private void book(Object resource, LocalDateTime at) {
        busy.computeIfAbsent(resource, r -> new HashSet<>()).add(at);
    }
}
