
    WITH match_results AS (
        SELECT
            m.stage_id,
            m.home_team_id AS team_id,
            m.home_goals AS goals_for,
            m.away_goals AS goals_against,
            CASE
                WHEN m.home_goals > m.away_goals THEN 3
                WHEN m.home_goals = m.away_goals THEN 1
                ELSE 0
            END AS points,
            CASE WHEN m.home_goals > m.away_goals THEN 1 ELSE 0 END AS won,
            CASE WHEN m.home_goals = m.away_goals THEN 1 ELSE 0 END AS drawn,
            CASE WHEN m.home_goals < m.away_goals THEN 1 ELSE 0 END AS lost,
            1 AS is_home
        FROM match m
        WHERE m.status = 'finished'

        UNION ALL

        SELECT
            m.stage_id,
            m.away_team_id AS team_id,
            m.away_goals AS goals_for,
            m.home_goals AS goals_against,
            CASE
                WHEN m.away_goals > m.home_goals THEN 3
                WHEN m.away_goals = m.home_goals THEN 1
                ELSE 0
            END AS points,
            CASE WHEN m.away_goals > m.home_goals THEN 1 ELSE 0 END AS won,
            CASE WHEN m.away_goals = m.home_goals THEN 1 ELSE 0 END AS drawn,
            CASE WHEN m.away_goals < m.home_goals THEN 1 ELSE 0 END AS lost,
            0 AS is_home
        FROM match m
        WHERE m.status = 'finished'
    ),
    aggregated_stats AS (
        SELECT
            mr.stage_id,
            mr.team_id,
            COUNT(*) AS matches_played,
            SUM(mr.won) AS wins,
            SUM(mr.drawn) AS draws,
            SUM(mr.lost) AS losses,
            SUM(mr.goals_for) AS goals_for,
            SUM(mr.goals_against) AS goals_against,
            SUM(mr.goals_for) - SUM(mr.goals_against) AS goal_difference,
            SUM(mr.points) AS points,
            SUM(CASE WHEN mr.is_home = 1 THEN mr.points ELSE 0 END) AS home_points,
            SUM(CASE WHEN mr.is_home = 0 THEN mr.points ELSE 0 END) AS away_points
        FROM match_results mr
        GROUP BY mr.stage_id, mr.team_id
    )
    SELECT
        c.name AS competition_name,
        e.season,
        DENSE_RANK() OVER (
            PARTITION BY s.stage_id
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
        ast.home_points,
        ast.away_points,
        ROUND(ast.points::numeric / ast.matches_played, 2) AS points_per_match
    FROM aggregated_stats ast
    JOIN stage s ON s.stage_id = ast.stage_id
    JOIN edition e ON e.edition_id = s.edition_id
    JOIN competition c ON c.competition_id = e.competition_id
    JOIN team t ON t.team_id = ast.team_id
    WHERE c.name = 'LaLiga' AND e.season = '2024/25'
    ORDER BY league_position;
