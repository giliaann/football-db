package pl.football.seeder.random;

import pl.football.seeder.entity.Edition;
import pl.football.seeder.entity.EditionParticipation;
import pl.football.seeder.entity.Referee;
import pl.football.seeder.entity.Stadium;
import pl.football.seeder.entity.Stage;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** One edition while it is being played: its stages, referees and standings. */
final class RunningEdition {

    final Edition edition;
    final int season;
    final Standings table = new Standings();

    private final String title;
    private final Editors.Staff staff;
    private final MatchEngine.Rules rules;
    private final List<Referee> referees;
    private final Rng rng;
    private final Db db;
    private final MatchEngine engine;
    private final Calendar calendar;
    private short stageOrder = 0;

    RunningEdition(Edition edition, String title, int season, Editors.Staff staff, MatchEngine.Rules rules,
                   List<Referee> referees, Rng rng, Db db, MatchEngine engine, Calendar calendar) {
        this.edition = edition;
        this.title = title;
        this.season = season;
        this.staff = staff;
        this.rules = rules;
        this.referees = referees;
        this.rng = rng;
        this.db = db;
        this.engine = engine;
        this.calendar = calendar;
    }

    void enter(Collection<TeamInfo> teams) {
        teams.forEach(table::enter);
    }

    /** Stages are numbered in the order they are created; the competition administrator creates them. */
    Stage stage(String name, String type, LocalDateTime createdAt) {
        return db.save(new Stage(edition, name, type, ++stageOrder), staff.administrator(), createdAt);
    }

    /** A match that may end level (league or group). */
    MatchEngine.Result play(Stage stage, TeamInfo home, TeamInfo away, Stadium stadium, LocalDateTime kickoff,
                            LocalDateTime createdAt, String forcedStatus) {
        return play(stage, home, away, stadium, kickoff, createdAt, forcedStatus, false, false, null);
    }

    /** A knockout match: a winner is needed, after extra time if allowed, otherwise straight from penalties. */
    MatchEngine.Result playDecisive(Stage stage, TeamInfo home, TeamInfo away, Stadium stadium, LocalDateTime kickoff,
                                    LocalDateTime createdAt, boolean extraTime, int[] firstLeg) {
        return play(stage, home, away, stadium, kickoff, createdAt, null, true, extraTime, firstLeg);
    }

    private MatchEngine.Result play(Stage stage, TeamInfo home, TeamInfo away, Stadium stadium, LocalDateTime kickoff,
                                    LocalDateTime createdAt, String forcedStatus, boolean decisive, boolean extraTime,
                                    int[] firstLeg) {
        Referee referee = calendar.referee(referees, kickoff, rng);
        MatchEngine.Result result = engine.play(new MatchEngine.Fixture(stage, home, away, stadium, referee, kickoff,
                season, rules, decisive, extraTime, firstLeg, staff, title, createdAt, forcedStatus));
        table.record(stage.getName(), stage.getStageType(), home, away, result);
        return result;
    }

    /** One row per team. Final positions are given only for finished editions. */
    void finish(Collection<TeamInfo> teams, Map<TeamInfo, Integer> positions, LocalDate end) {
        LocalDateTime createdAt = edition.getStartDate().minusDays(14).atTime(10, 0);
        LocalDateTime updatedAt = end != null ? SeedTime.notAfterNow(end.atTime(23, 30)) : SeedTime.NOW;
        for (TeamInfo team : teams) {
            Standings.Row row = table.row(team);
            EditionParticipation participation = new EditionParticipation(team.team, edition);
            participation.setPoints((short) row.points);
            participation.setGoalsFor((short) row.goalsFor);
            participation.setGoalsAgainst((short) row.goalsAgainst);
            participation.setStageReached(row.stageReached);
            Integer position = positions.get(team);
            participation.setFinalPosition(position == null ? null : position.shortValue());
            participation.setUpdatedAt(updatedAt);
            db.save(participation, staff.administrator(), createdAt);
        }
        db.endOfBatch();
    }
}
