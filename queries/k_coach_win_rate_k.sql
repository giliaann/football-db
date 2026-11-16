WITH coach_matches AS (
    SELECT
        c.coach_id,
        c.first_name,
        c.last_name,
        a.team_id,
        m.match_id,

        CASE
            WHEN a.team_id = m.home_team_id
                 AND m.home_goals > m.away_goals
                THEN 'Win'

            WHEN a.team_id = m.away_team_id
                 AND m.away_goals > m.home_goals
                THEN 'Win'

            WHEN m.home_goals = m.away_goals
                THEN 'Draw'

            ELSE 'Loss'
        END AS match_result

    FROM appearance a
    JOIN coach c
        ON c.coach_id = a.coach_id
    JOIN match m
        ON m.match_id = a.match_id

    WHERE m.status = 'finished'
      AND a.coach_id IS NOT NULL
),
coach_statistics AS (
    SELECT
        cm.coach_id,
        cm.first_name,
        cm.last_name,

        COUNT(DISTINCT cm.match_id) AS matches_played,

        COUNT(DISTINCT cm.match_id) FILTER (
            WHERE cm.match_result = 'Win'
        ) AS wins,

        COUNT(DISTINCT cm.match_id) FILTER (
            WHERE cm.match_result = 'Draw'
        ) AS draws,

        COUNT(DISTINCT cm.match_id) FILTER (
            WHERE cm.match_result = 'Loss'
        ) AS losses,

        ROUND(
            100.0 * COUNT(DISTINCT cm.match_id) FILTER (
                WHERE cm.match_result = 'Win'
            ) / NULLIF(COUNT(DISTINCT cm.match_id), 0),
            2
        ) AS win_rate

    FROM coach_matches cm

    GROUP BY
        cm.coach_id,
        cm.first_name,
        cm.last_name
),
coach_ranking AS (
    SELECT
        cs.*,

        RANK() OVER (
            ORDER BY cs.win_rate DESC
        ) AS coach_rank,

        AVG(cs.win_rate) OVER ()
            AS avg_win_rate

    FROM coach_statistics cs
)
SELECT
    first_name || ' ' || last_name AS coach_name,

    matches_played,
    wins,
    draws,
    losses,

    win_rate,
    coach_rank,

    ROUND(avg_win_rate, 2) AS avg_win_rate,

    ROUND(
        win_rate - avg_win_rate, 2
    ) AS difference_from_average,

    CASE
        WHEN win_rate > avg_win_rate
            THEN 'Above average'
        WHEN win_rate < avg_win_rate
            THEN 'Below average'
        ELSE 'Average'
    END AS coach_performance

FROM coach_ranking

WHERE matches_played >= 5

ORDER BY
    win_rate DESC,
    wins DESC;