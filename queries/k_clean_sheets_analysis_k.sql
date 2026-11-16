WITH goalkeeper_matches AS (
    SELECT
        a.player_id,
        a.team_id,
        m.match_id,
        m.scheduled_at,

        CASE
            WHEN a.team_id = m.home_team_id
                THEN m.away_goals
            ELSE m.home_goals
        END AS goals_conceded

    FROM appearance a
    JOIN player p
        ON p.player_id = a.player_id
    JOIN match m
        ON m.match_id = a.match_id

    WHERE m.status = 'finished'
      AND a.is_starting = TRUE
      AND COALESCE(a.position, p.primary_position) = 'goalkeeper'
),
goalkeeper_statistics AS (
    SELECT
        gm.player_id,

        COUNT(DISTINCT gm.match_id) AS matches_played,

        COUNT(DISTINCT gm.match_id) FILTER (
            WHERE gm.goals_conceded = 0
        ) AS clean_sheets,

        COUNT(DISTINCT gm.match_id) FILTER (
            WHERE gm.goals_conceded > 0
        ) AS matches_with_conceded_goals,

        ROUND(AVG(gm.goals_conceded), 2)
            AS avg_goals_conceded

    FROM goalkeeper_matches gm
    GROUP BY gm.player_id
),
goalkeeper_ranking AS (
    SELECT
        gs.*,

        ROUND(
            100.0 * gs.clean_sheets /
            NULLIF(gs.matches_played, 0), 2
        ) AS clean_sheet_percentage,

        RANK() OVER (
            ORDER BY gs.clean_sheets DESC
        ) AS goalkeeper_rank,

        AVG(gs.clean_sheets) OVER ()
            AS avg_clean_sheets

    FROM goalkeeper_statistics gs
)
SELECT
    p.first_name || ' ' || p.last_name AS goalkeeper_name,

    gr.matches_played,
    gr.clean_sheets,
    gr.matches_with_conceded_goals,
    gr.clean_sheet_percentage,
    gr.avg_goals_conceded,

    gr.goalkeeper_rank,

    ROUND(gr.avg_clean_sheets, 2)
        AS avg_clean_sheets,

    CASE
        WHEN gr.clean_sheets > gr.avg_clean_sheets
            THEN 'Above average'
        WHEN gr.clean_sheets < gr.avg_clean_sheets
            THEN 'Below average'
        ELSE 'Average'
    END AS goalkeeper_performance

FROM goalkeeper_ranking gr
JOIN player p
    ON p.player_id = gr.player_id

WHERE gr.matches_played >= 5

ORDER BY
    gr.clean_sheets DESC,
    gr.clean_sheet_percentage DESC;