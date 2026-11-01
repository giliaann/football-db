package pl.football.seeder.random;

import pl.football.seeder.entity.Stadium;

import java.time.LocalDateTime;
import java.util.function.Consumer;

/** A match that has been arranged but not played. Created by EditionPlanner, consumed by MatchSimulator. */
final class Fixture {

    /** A knockout pairing over one or two legs. Decided by the last leg, which carries the aggregate. */
    static final class Tie {
        final TeamState first;
        final TeamState second;
        final int legs;
        /** False where a level tie goes straight to penalties (Copa América before the final). */
        final boolean extraTime;
        int firstGoals;
        int secondGoals;
        TeamState winner;
        TeamState loser;
        Consumer<Tie> onDecided = t -> {
        };

        Tie(TeamState first, TeamState second, int legs, boolean extraTime) {
            this.first = first;
            this.second = second;
            this.legs = legs;
            this.extraTime = extraTime;
        }
    }

    /** Tells the planner when the last match of a round has finished. */
    static final class Round {
        private int pending;
        private final Runnable whenDone;

        Round(Runnable whenDone) {
            this.whenDone = whenDone;
        }

        void add() {
            pending++;
        }

        void matchFinished() {
            pending--;
            if (pending == 0) {
                whenDone.run();
            }
        }
    }

    final EditionRun run;
    final EditionRun.StageRun stage;
    final TeamState home;
    final TeamState away;
    final Stadium stadium;
    final LocalDateTime kickoff;
    final boolean neutral;
    final Round round;
    final Tie tie;
    final int leg;
    /** Set for the few matches that end up postponed or cancelled instead of being played. */
    String forcedStatus;

    Fixture(EditionRun run, EditionRun.StageRun stage, TeamState home, TeamState away, Stadium stadium,
            LocalDateTime kickoff, boolean neutral, Round round, Tie tie, int leg) {
        this.run = run;
        this.stage = stage;
        this.home = home;
        this.away = away;
        this.stadium = stadium;
        this.kickoff = kickoff;
        this.neutral = neutral;
        this.round = round;
        this.tie = tie;
        this.leg = leg;
    }

    boolean decidesTie() {
        return tie != null && leg == tie.legs;
    }
}
