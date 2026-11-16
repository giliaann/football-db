-- Optimized version of queries/j_dynamic_league_table.sql (same result).
-- 1. The original aggregates ALL finished matches of all competitions and only then keeps
--    LaLiga 2024/25: the filter on c.name / e.season cannot be pushed below GROUP BY.
--    Here the stages are chosen first and only their matches are read.
-- 2. Instead of reading Match twice (UNION ALL of the home and away perspectives), each match row is
--    turned into two rows with LATERAL (VALUES ...), so Match is read once.
-- 3. The match columns needed are exactly those of the partial covering index
--    idx_match_finished_analysis (stage_id, scheduled_at) INCLUDE (home/away team, goals)
--    WHERE status = 'finished', which allows an Index Only Scan.
WITH target_stages AS (
    SELECT s.stage_id, c.name AS competition_name, e.season
    FROM stage s
    JOIN edition e ON e.edition_id = s.edition_id
    JOIN competition c ON c.competition_id = e.competition_id
    WHERE c.name = 'LaLiga' AND e.season = '2024/25'
),
match_results AS (
    SELECT
        m.stage_id,
        side.team_id,
        side.goals_for,
        side.goals_against,
        side.is_home,
        CASE
            WHEN side.goals_for > side.goals_against THEN 3
            WHEN side.goals_for = side.goals_against THEN 1
            ELSE 0
        END AS points
    FROM target_stages ts
    JOIN match m ON m.stage_id = ts.stage_id AND m.status = 'finished'
    CROSS JOIN LATERAL (VALUES
        (m.home_team_id, m.home_goals, m.away_goals, 1),
        (m.away_team_id, m.away_goals, m.home_goals, 0)
    ) AS side (team_id, goals_for, goals_against, is_home)
),
aggregated_stats AS (
    SELECT
        mr.stage_id,
        mr.team_id,
        COUNT(*) AS matches_played,
        COUNT(*) FILTER (WHERE mr.goals_for > mr.goals_against) AS wins,
        COUNT(*) FILTER (WHERE mr.goals_for = mr.goals_against) AS draws,
        COUNT(*) FILTER (WHERE mr.goals_for < mr.goals_against) AS losses,
        SUM(mr.goals_for) AS goals_for,
        SUM(mr.goals_against) AS goals_against,
        SUM(mr.goals_for) - SUM(mr.goals_against) AS goal_difference,
        SUM(mr.points) AS points,
        SUM(mr.points) FILTER (WHERE mr.is_home = 1) AS home_points,
        SUM(mr.points) FILTER (WHERE mr.is_home = 0) AS away_points
    FROM match_results mr
    GROUP BY mr.stage_id, mr.team_id
)
SELECT
    ts.competition_name,
    ts.season,
    DENSE_RANK() OVER (
        PARTITION BY ast.stage_id
        ORDER BY ast.points DESC, ast.goal_difference DESC, ast.goals_for DESC
    ) AS league_position,
    t.name AS team_name,
    ast.matches_played,
    ast.wins,
    ast.draws,
    ast.losses,
    ast.goals_for || ':' || ast.goals_against AS goals,
    ast.goal_difference,
    ast.points,
    COALESCE(ast.home_points, 0) AS home_points,
    COALESCE(ast.away_points, 0) AS away_points,
    ROUND(ast.points::numeric / ast.matches_played, 2) AS points_per_match
FROM aggregated_stats ast
JOIN target_stages ts ON ts.stage_id = ast.stage_id
JOIN team t ON t.team_id = ast.team_id
ORDER BY league_position;
