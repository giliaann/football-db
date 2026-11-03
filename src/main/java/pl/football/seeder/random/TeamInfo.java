package pl.football.seeder.random;

import pl.football.seeder.entity.Coach;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Team;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A club or national team in memory: its squad in each season, shirt numbers and coaching history. */
final class TeamInfo {

    /** {@code coach} leads the team from {@code from} until the next stint starts. */
    record CoachStint(Coach coach, LocalDate from) {
    }

    final Team team;
    final String country;
    final int tier;
    /** The home ground; null for national teams, which play at neutral venues. */
    final Stadium stadium;
    final boolean national;

    /** Index = season. A national team has one squad, used for its only tournament. */
    final List<List<PlayerInfo>> rosters = new ArrayList<>();
    final List<Map<PlayerInfo, Short>> shirts = new ArrayList<>();
    final List<CoachStint> coaches = new ArrayList<>();
    private final Map<Integer, Double> strength = new HashMap<>();

    TeamInfo(Team team, String country, int tier, Stadium stadium, boolean national) {
        this.team = team;
        this.country = country;
        this.tier = tier;
        this.stadium = stadium;
        this.national = national;
    }

    String name() {
        return team.getName();
    }

    List<PlayerInfo> roster(int season) {
        return rosters.get(Math.min(season, rosters.size() - 1));
    }

    short shirt(int season, PlayerInfo player) {
        return shirts.get(Math.min(season, shirts.size() - 1)).get(player);
    }

    Coach coachAt(LocalDate date) {
        Coach current = coaches.getFirst().coach();
        for (CoachStint stint : coaches) {
            if (!stint.from().isAfter(date)) {
                current = stint.coach();
            }
        }
        return current;
    }

    /** The average quality of the best eleven; decides how many goals the team tends to score. */
    double strength(int season) {
        return strength.computeIfAbsent(Math.min(season, rosters.size() - 1), s -> roster(s).stream()
                .map(p -> p.quality).sorted(Comparator.reverseOrder()).limit(11)
                .mapToDouble(Double::doubleValue).average().orElse(0));
    }

    @Override
    public String toString() {
        return name();
    }
}
