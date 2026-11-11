  WITH team_venue_matches AS (
        SELECT
            m.home_team_id AS team_id,
            m.stage_id,
            CASE WHEN m.home_goals > m.away_goals THEN 3 WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END AS pts,
            m.home_goals AS gf,
            m.away_goals AS ga,
            1 AS is_home
        FROM match m
        WHERE m.status = 'finished'

        UNION ALL

        SELECT
            m.away_team_id AS team_id,
            m.stage_id,
            CASE WHEN m.away_goals > m.home_goals THEN 3 WHEN m.away_goals = m.home_goals THEN 1 ELSE 0 END AS pts,
            m.away_goals AS gf,
            m.home_goals AS ga,
            0 AS is_home
        FROM match m
        WHERE m.status = 'finished'
    ),
    team_stats AS (
        SELECT
            tvm.team_id,
            COUNT(*) AS total_matches,
            SUM(CASE WHEN tvm.is_home = 1 THEN 1 ELSE 0 END) AS home_matches,
            SUM(CASE WHEN tvm.is_home = 0 THEN 1 ELSE 0 END) AS away_matches,
            SUM(CASE WHEN tvm.is_home = 1 THEN tvm.pts ELSE 0 END) AS home_pts,
            SUM(CASE WHEN tvm.is_home = 0 THEN tvm.pts ELSE 0 END) AS away_pts,
            SUM(tvm.pts) AS total_pts,
            ROUND(AVG(CASE WHEN tvm.is_home = 1 THEN tvm.gf END), 2) AS avg_home_goals_scored,
            ROUND(AVG(CASE WHEN tvm.is_home = 0 THEN tvm.gf END), 2) AS avg_away_goals_scored
        FROM team_venue_matches tvm
        JOIN stage s ON s.stage_id = tvm.stage_id
        WHERE s.stage_type = 'league'
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
