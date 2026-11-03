package pl.football.seeder.random;

import pl.football.seeder.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A player in memory: where he plays in each season and when he is injured. All of it is decided before any match. */
final class PlayerInfo {

    /** {@code end} is null while the injury lasts at "now". */
    record InjurySpan(LocalDate start, LocalDate end, String description) {
    }

    final Player player;
    final String position;
    /** Hidden ability; better players are picked more often and score more. */
    final double quality;
    final String nation;

    /** Index = season. The club he plays for (null: none in the catalog). */
    final TeamInfo[] clubs = new TeamInfo[SeedTime.SEASONS.size()];
    /** Index = season. The club that owns him; differs from {@link #clubs} while he is on loan. */
    final TeamInfo[] owners = new TeamInfo[SeedTime.SEASONS.size()];
    /** Index = season. When he joined that season's club; null if he was there from the start of the history. */
    final LocalDate[] joined = new LocalDate[SeedTime.SEASONS.size()];
    final List<InjurySpan> injuries = new ArrayList<>();

    PlayerInfo(Player player, String position, double quality, String nation) {
        this.player = player;
        this.position = position;
        this.quality = quality;
        this.nation = nation;
    }

    boolean injuredOn(LocalDate date) {
        for (InjurySpan injury : injuries) {
            if (!date.isBefore(injury.start()) && (injury.end() == null || !date.isAfter(injury.end()))) {
                return true;
            }
        }
        return false;
    }

    LocalDate birthDate() {
        return player.getBirthDate();
    }
}
