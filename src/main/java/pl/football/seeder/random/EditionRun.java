package pl.football.seeder.random;

import pl.football.seeder.entity.Edition;
import pl.football.seeder.entity.EditionParticipation;
import pl.football.seeder.entity.Stage;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** One edition while it is being played: tables, discipline, national squads and the eventual standings. */
final class EditionRun {

    static final class Stats {
        int points;
        int goalsFor;
        int goalsAgainst;
        String reached;
        int reachedOrder;

        int difference() {
            return goalsFor - goalsAgainst;
        }
    }

    /** A stage with its own table, used to rank a group or a league. */
    static final class StageRun {
        final Stage stage;
        final Map<TeamState, Stats> table = new LinkedHashMap<>();

        StageRun(Stage stage) {
            this.stage = stage;
        }

        boolean isKnockout() {
            return stage.getStageType().equals("knockout");
        }

        /** Points, then goal difference, then goals scored. The name only makes the order repeatable. */
        List<TeamState> ranking() {
            return table.keySet().stream()
                    .sorted(Comparator.<TeamState>comparingInt(t -> -table.get(t).points)
                            .thenComparingInt(t -> -table.get(t).difference())
                            .thenComparingInt(t -> -table.get(t).goalsFor)
                            .thenComparing(t -> t.name))
                    .toList();
        }
    }

    final Catalog.CompetitionDef competition;
    final Edition edition;
    final EditorDirectory.EditionStaff staff;
    final String title;
    final LocalDate plannedEnd;

    final List<StageRun> stages = new ArrayList<>();
    final Map<TeamState, Stats> stats = new LinkedHashMap<>();
    final Map<TeamState, Double> form = new HashMap<>();
    final Map<PlayerState, Integer> suspensions = new HashMap<>();
    final Map<PlayerState, Integer> yellowCards = new HashMap<>();
    final Map<TeamState, List<PlayerState>> squads = new LinkedHashMap<>();
    final Map<TeamState, Integer> finalPositions = new HashMap<>();

    EditionRun(Catalog.CompetitionDef competition, Edition edition, EditorDirectory.EditionStaff staff,
               LocalDate plannedEnd) {
        this.competition = competition;
        this.edition = edition;
        this.staff = staff;
        this.title = competition.name() + " " + edition.getSeason();
        this.plannedEnd = plannedEnd;
    }

    boolean isOver() {
        return !plannedEnd.isAfter(SeedTime.TODAY);
    }

    void enrol(TeamState team, Rng rng) {
        stats.computeIfAbsent(team, t -> new Stats());
        // Hidden strength for this edition: the club's level and a bit of luck, so tables are not flat.
        form.computeIfAbsent(team, t -> {
            double level = t.national ? 1.0 : t.tier == 1 ? 1.28 : t.tier == 2 ? 1.05 : 0.9;
            return level * Math.exp(rng.gaussian(0, 0.14));
        });
    }

    void enrolInStage(StageRun stage, TeamState team, Rng rng) {
        enrol(team, rng);
        stage.table.computeIfAbsent(team, t -> new Stats());
    }

    void recordResult(StageRun stage, TeamState home, TeamState away, int homeGoals, int awayGoals) {
        apply(stats.get(home), stage, homeGoals, awayGoals);
        apply(stats.get(away), stage, awayGoals, homeGoals);
        if (!stage.isKnockout()) {
            apply(stage.table.get(home), stage, homeGoals, awayGoals);
            apply(stage.table.get(away), stage, awayGoals, homeGoals);
        }
    }

    private static void apply(Stats stats, StageRun stage, int scored, int conceded) {
        stats.goalsFor += scored;
        stats.goalsAgainst += conceded;
        if (!stage.isKnockout()) {
            stats.points += scored > conceded ? 3 : scored == conceded ? 1 : 0;
        }
        if (stage.stage.getStageOrder() >= stats.reachedOrder) {
            stats.reachedOrder = stage.stage.getStageOrder();
            stats.reached = stage.stage.getName();
        }
    }

    void persistParticipation(Db db) {
        stats.forEach((team, line) -> {
            EditionParticipation row = new EditionParticipation(team.team, edition);
            row.setPoints((short) line.points);
            row.setGoalsFor((short) line.goalsFor);
            row.setGoalsAgainst((short) line.goalsAgainst);
            row.setStageReached(line.reached);
            Integer position = finalPositions.get(team);
            row.setFinalPosition(position == null ? null : position.shortValue());
            LocalDate updated = edition.getEndDate() != null ? edition.getEndDate() : SeedTime.TODAY;
            row.setUpdatedAt(updated.atTime(23, 30).isAfter(SeedTime.NOW) ? SeedTime.NOW : updated.atTime(23, 30));
            db.save(row, staff.administrator(), edition.getStartDate().minusDays(14).atTime(10, 0));
        });
    }
}
