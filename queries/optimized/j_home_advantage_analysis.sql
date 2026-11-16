-- Optimized version of queries/j_home_advantage_analysis.sql (same result).
-- The original reads Match twice (UNION ALL of the home and away perspectives) and joins Stage after
-- the union. Here league stages are filtered first, each finished match is read once and turned
-- into two rows (home and away perspective) with LATERAL (VALUES ...).
WITH team_venue_matches AS (
    SELECT
        side.team_id,
        side.is_home,
        side.gf,
        CASE WHEN side.gf > side.ga THEN 3 WHEN side.gf = side.ga THEN 1 ELSE 0 END AS pts
    FROM match m
    JOIN stage s ON s.stage_id = m.stage_id
    CROSS JOIN LATERAL (VALUES
        (m.home_team_id, m.home_goals, m.away_goals, 1),
        (m.away_team_id, m.away_goals, m.home_goals, 0)
    ) AS side (team_id, gf, ga, is_home)
    WHERE m.status = 'finished'
      AND s.stage_type = 'league'
),
team_stats AS (
    SELECT
        tvm.team_id,
        COUNT(*) AS total_matches,
        COUNT(*) FILTER (WHERE tvm.is_home = 1) AS home_matches,
        COUNT(*) FILTER (WHERE tvm.is_home = 0) AS away_matches,
        COALESCE(SUM(tvm.pts) FILTER (WHERE tvm.is_home = 1), 0) AS home_pts,
        COALESCE(SUM(tvm.pts) FILTER (WHERE tvm.is_home = 0), 0) AS away_pts,
        SUM(tvm.pts) AS total_pts,
        ROUND(AVG(tvm.gf) FILTER (WHERE tvm.is_home = 1), 2) AS avg_home_goals_scored,
        ROUND(AVG(tvm.gf) FILTER (WHERE tvm.is_home = 0), 2) AS avg_away_goals_scored
    FROM team_venue_matches tvm
    GROUP BY tvm.team_id
    HAVING COUNT(*) >= 50
)
SELECT
    t.name AS team_name,
    ts.total_matches,
    ts.home_pts,
    ts.away_pts,
    ts.total_pts,
    ROUND(ts.home_pts * 100.0 / NULLIF(ts.total_pts, 0), 1) AS home_points_share_pct,
    ts.avg_home_goals_scored,
    ts.avg_away_goals_scored,
    ROUND(ts.avg_home_goals_scored - ts.avg_away_goals_scored, 2) AS home_scoring_premium,
    CASE
        WHEN (ts.home_pts * 100.0 / ts.total_pts) >= 65.0 THEN 'Twierdza domowa'
        WHEN (ts.home_pts * 100.0 / ts.total_pts) <= 45.0 THEN 'Lepszy na wyjeździe'
        ELSE 'Zrównoważony'
    END AS team_profile
FROM team_stats ts
JOIN team t ON t.team_id = ts.team_id
ORDER BY home_points_share_pct DESC
LIMIT 12;
