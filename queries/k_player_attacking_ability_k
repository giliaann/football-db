WITH player_matches AS (
    SELECT
        a.player_id,
        m.match_id,
        m.scheduled_at,
        COUNT(DISTINCT CASE
            WHEN et.category = 'goal' THEN me.match_event_id
        END) AS goals,
        COUNT(DISTINCT CASE
            WHEN et.category = 'assist' THEN me.match_event_id
        END) AS assists
    FROM appearance a
    JOIN match m ON m.match_id = a.match_id
    LEFT JOIN matchevent me
        ON me.appearance_id = a.appearance_id
       AND me.is_cancelled = FALSE
    LEFT JOIN eventtype et
        ON et.event_type_id = me.event_type_id
    WHERE a.player_id IS NOT NULL
      AND m.status = 'finished'
    GROUP BY
        a.player_id,
        m.match_id,
        m.scheduled_at
),
player_form AS (
    SELECT
        pm.player_id,
        pm.match_id,
        pm.scheduled_at,
        pm.goals,
        pm.assists,
        pm.goals + pm.assists AS contributions,
        AVG(pm.goals + pm.assists) OVER (
            PARTITION BY pm.player_id
            ORDER BY pm.scheduled_at
            ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
        ) AS avg_contributions,
        COUNT(*) OVER (
            PARTITION BY pm.player_id
            ORDER BY pm.scheduled_at
            ROWS BETWEEN 4 PRECEDING AND CURRENT ROW
        ) AS matches_in_window,
        ROW_NUMBER() OVER (
            PARTITION BY pm.player_id
            ORDER BY pm.scheduled_at DESC
        ) AS match_rank
    FROM player_matches pm
),
player_summary AS (
    SELECT
        pf.player_id,
        SUM(pf.goals) AS total_goals,
        SUM(pf.assists) AS total_assists,
        AVG(pf.avg_contributions) AS avg_contributions_last_5
    FROM player_form pf
    WHERE pf.match_rank <= 5
      AND pf.matches_in_window = 5
    GROUP BY pf.player_id
)
SELECT
    p.first_name || ' ' || p.last_name AS player_name,
    ps.total_goals,
    ps.total_assists,
    ROUND(ps.avg_contributions_last_5, 2)
        AS avg_contributions_last_5,
    ROUND(
        AVG(ps.avg_contributions_last_5) OVER (), 2
    ) AS avg_all_players,
    ROUND(
        ps.avg_contributions_last_5 -
        AVG(ps.avg_contributions_last_5) OVER (), 2
    ) AS difference_from_average
FROM player_summary ps
JOIN player p ON p.player_id = ps.player_id
ORDER BY avg_contributions_last_5 DESC;