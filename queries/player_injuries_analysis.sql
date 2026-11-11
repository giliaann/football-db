  WITH player_injuries AS (
        SELECT
            p.player_id,
            p.first_name || ' ' || p.last_name AS player_name,
            p.primary_position,
            t.team_id,
            t.name AS team_name,
            i.injury_id,
            i.start_date,
            COALESCE(i.end_date, '2026-11-01'::date) AS end_date,
            (COALESCE(i.end_date, '2026-11-01'::date) - i.start_date) AS injury_days
        FROM injury i
        JOIN player p ON p.player_id = i.player_id
        JOIN teamsquad ts ON ts.player_id = p.player_id
        JOIN team t ON t.team_id = ts.team_id
    ),
    missed_matches AS (
        SELECT
            pi.player_id,
            pi.player_name,
            pi.primary_position,
            pi.team_name,
            COUNT(DISTINCT pi.injury_id) AS total_injuries,
            SUM(pi.injury_days) AS total_days_injured,
            COUNT(DISTINCT m.match_id) AS team_matches_missed_due_to_injury
        FROM player_injuries pi
        JOIN match m ON (m.home_team_id = pi.team_id OR m.away_team_id = pi.team_id)
                    AND m.scheduled_at::date BETWEEN pi.start_date AND pi.end_date
                    AND m.status = 'finished'
        GROUP BY pi.player_id, pi.player_name, pi.primary_position, pi.team_name
    )
    SELECT
        DENSE_RANK() OVER (ORDER BY team_matches_missed_due_to_injury DESC, total_days_injured DESC) AS fragility_rank,
        player_name,
        primary_position,
        team_name,
        total_injuries,
        total_days_injured,
        ROUND(total_days_injured::numeric / total_injuries, 1) AS avg_days_per_injury,
        team_matches_missed_due_to_injury,
        ROUND(team_matches_missed_due_to_injury::numeric / total_injuries, 1) AS avg_missed_matches_per_injury
    FROM missed_matches
    WHERE total_injuries >= 2
    ORDER BY fragility_rank
    LIMIT 12;
