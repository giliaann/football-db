package pl.football.seeder.random;

import pl.football.seeder.entity.Coach;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Team;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A club or national team during the simulation: what the Team row cannot remember. */
final class TeamState {

    /** Who coached the team from a date. Open-ended until the next entry. */
    record CoachStint(Coach coach, LocalDate from) {
    }

    final Team team;
    final String name;
    final String country;
    final int tier;
    final Stadium stadium;
    final boolean national;
    /** The division at the start of the history; clubs later move through relegation and promotion. */
    final String division;

    /** Everyone who has ever played here. Who was here on a date is decided by each player's stints. */
    final List<PlayerState> members = new ArrayList<>();
    /** The squad while careers are being simulated: changes with each transfer window. */
    final List<PlayerState> roster = new ArrayList<>();
    final List<CoachStint> coaches = new ArrayList<>();
    final Map<PlayerState, Short> shirts = new HashMap<>();

    TeamState(Team team, String country, int tier, Stadium stadium, boolean national, String division) {
        this.team = team;
        this.name = team.getName();
        this.country = country;
        this.tier = tier;
        this.stadium = stadium;
        this.national = national;
        this.division = division;
    }

    List<PlayerState> membersAt(LocalDate date) {
        List<PlayerState> squad = new ArrayList<>();
        for (PlayerState player : members) {
            if (player.clubAt(date) == this) {
                squad.add(player);
            }
        }
        return squad;
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

    long rosterCount(String position) {
        return roster.stream().filter(p -> p.position.equals(position)).count();
    }

    @Override
    public String toString() {
        return name;
    }
}
