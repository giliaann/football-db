package pl.football.seeder.random;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Points, goals and the furthest stage of every team in one edition. Only finished matches count. */
final class Standings {

    static final class Row {
        int points;
        int goalsFor;
        int goalsAgainst;
        String stageReached;
    }

    private final Map<TeamInfo, Row> rows = new LinkedHashMap<>();

    void enter(TeamInfo team) {
        rows.putIfAbsent(team, new Row());
    }

    Row row(TeamInfo team) {
        return rows.get(team);
    }

    /** Points come only from league and group stages; goals from every match, without penalty shoot-outs. */
    void record(String stageName, String stageType, TeamInfo home, TeamInfo away, MatchEngine.Result result) {
        if (!result.finished()) {
            return;
        }
        Row h = rows.get(home);
        Row a = rows.get(away);
        h.goalsFor += result.homeGoals();
        h.goalsAgainst += result.awayGoals();
        a.goalsFor += result.awayGoals();
        a.goalsAgainst += result.homeGoals();
        h.stageReached = stageName;
        a.stageReached = stageName;
        if (stageType.equals("league") || stageType.equals("group")) {
            int diff = result.homeGoals() - result.awayGoals();
            h.points += diff > 0 ? 3 : diff == 0 ? 1 : 0;
            a.points += diff < 0 ? 3 : diff == 0 ? 1 : 0;
        }
    }

    /** By points, goal difference, goals scored, then name. */
    List<TeamInfo> ranking(Collection<TeamInfo> teams) {
        return teams.stream().sorted(Comparator
                .comparingInt((TeamInfo t) -> rows.get(t).points).reversed()
                .thenComparing(Comparator.comparingInt((TeamInfo t) -> rows.get(t).goalsFor - rows.get(t).goalsAgainst).reversed())
                .thenComparing(Comparator.comparingInt((TeamInfo t) -> rows.get(t).goalsFor).reversed())
                .thenComparing(TeamInfo::name)).toList();
    }
}
