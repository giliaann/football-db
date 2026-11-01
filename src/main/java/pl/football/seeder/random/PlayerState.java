package pl.football.seeder.random;

import pl.football.seeder.entity.Player;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** A player during the simulation: career, fitness and call-ups, none of which have a column of their own. */
final class PlayerState {

    /** One spell at a club. {@code to} is inclusive; null means it was still running at the reference date. */
    static final class Stint {
        final TeamState club;
        final LocalDate from;
        final boolean loan;
        LocalDate to;

        Stint(TeamState club, LocalDate from, LocalDate to, boolean loan) {
            this.club = club;
            this.from = from;
            this.to = to;
            this.loan = loan;
        }

        boolean contains(LocalDate date) {
            return !date.isBefore(from) && (to == null || !date.isAfter(to));
        }
    }

    record InjuryRecord(LocalDate start, LocalDate end, String description) {
        boolean covers(LocalDate date) {
            return !date.isBefore(start) && !date.isAfter(end);
        }
    }

    record CallUp(LocalDate from, LocalDate to) {
    }

    final Player player;
    final String position;
    final String nation;
    /** Hidden ability, only used to choose lineups, national squads and transfer destinations. */
    final double quality;
    final LocalDate joinedHistory;

    final List<Stint> stints = new ArrayList<>();
    final List<InjuryRecord> injuries = new ArrayList<>();
    final List<CallUp> callUps = new ArrayList<>();

    LocalDate retiredOn;
    LocalDate lastMatch;
    /** The owning club while out on loan; loans return here when they end. */
    TeamState loanParent;

    PlayerState(Player player, String position, String nation, double quality, LocalDate joinedHistory) {
        this.player = player;
        this.position = position;
        this.nation = nation;
        this.quality = quality;
        this.joinedHistory = joinedHistory;
    }

    TeamState clubAt(LocalDate date) {
        for (Stint stint : stints) {
            if (stint.contains(date)) {
                return stint.club;
            }
        }
        return null;
    }

    Stint currentStint() {
        return stints.isEmpty() ? null : stints.getLast();
    }

    boolean isRetired(LocalDate date) {
        return retiredOn != null && date.isAfter(retiredOn);
    }

    boolean isInjured(LocalDate date) {
        for (InjuryRecord injury : injuries) {
            if (injury.covers(date)) {
                return true;
            }
        }
        return false;
    }

    boolean isCalledUp(LocalDate date) {
        for (CallUp callUp : callUps) {
            if (!date.isBefore(callUp.from()) && !date.isAfter(callUp.to())) {
                return true;
            }
        }
        return false;
    }

    /** The checks shared by club and national matches; a club match also needs the player not called up. */
    boolean isFit(LocalDate date) {
        return !isRetired(date) && !date.isBefore(joinedHistory) && !isInjured(date)
                && (lastMatch == null || lastMatch.plusDays(2).compareTo(date) <= 0);
    }

    /** Adds an injury suffered in a match, dropping any planned later injury it would overlap. */
    void addMatchInjury(InjuryRecord injury) {
        injuries.removeIf(existing -> !existing.start().isAfter(injury.end()) && !existing.end().isBefore(injury.start()));
        injuries.add(injury);
    }

    int ageOn(LocalDate date) {
        return java.time.Period.between(player.getBirthDate(), date).getYears();
    }
}
