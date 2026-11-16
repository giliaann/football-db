WITH player_appearances AS (
    SELECT
        a.appearance_id,
        a.player_id,
        a.team_id,
        a.match_id,
        a.is_starting,
        m.scheduled_at,
        m.status,

        CASE
            WHEN m.extra_time_second_half_end IS NOT NULL
                THEN 120
            WHEN m.second_half_end IS NOT NULL
                THEN 90
            ELSE 90
        END AS match_duration

    FROM appearance a
    JOIN match m
        ON m.match_id = a.match_id

    WHERE a.player_id IS NOT NULL
      AND m.status = 'finished'
),
substitution_events AS (
    SELECT
        me.appearance_id,

        MIN(
            CASE
                WHEN me.match_period = 'first_half'
                    THEN me.minute + COALESCE(me.stoppage_minute, 0)
                WHEN me.match_period = 'second_half'
                    THEN 45 + me.minute +
                         COALESCE(me.stoppage_minute, 0)
                WHEN me.match_period = 'extra_time_1'
                    THEN 90 + me.minute +
                         COALESCE(me.stoppage_minute, 0)
                WHEN me.match_period = 'extra_time_2'
                    THEN 105 + me.minute +
                         COALESCE(me.stoppage_minute, 0)
            END
        ) AS substitution_minute

    FROM matchevent me
    JOIN eventtype et
        ON et.event_type_id = me.event_type_id

    WHERE et.category = 'substitution'
      AND me.is_cancelled = FALSE

    GROUP BY me.appearance_id
),
player_minutes AS (
    SELECT
        pa.player_id,
        pa.team_id,
        pa.match_id,
        pa.scheduled_at,

        CASE
            WHEN pa.is_starting = TRUE THEN
                COALESCE(
                    se.substitution_minute,
                    pa.match_duration
                )

            ELSE
                GREATEST(
                    0,
                    pa.match_duration -
                    COALESCE(se.substitution_minute,
                             pa.match_duration)
                )
        END AS minutes_played

    FROM player_appearances pa
    LEFT JOIN substitution_events se
        ON se.appearance_id = pa.appearance_id
),
player_statistics AS (
    SELECT
        pm.player_id,

        COUNT(DISTINCT pm.match_id) AS matches_played,

        SUM(pm.minutes_played) AS total_minutes,

        ROUND(
            AVG(pm.minutes_played), 2
        ) AS avg_minutes_per_match,

        MAX(pm.minutes_played) AS max_minutes_in_match,

        COUNT(DISTINCT pm.match_id) FILTER (
            WHERE pm.minutes_played > 0
        ) AS actual_appearances

    FROM player_minutes pm
    GROUP BY pm.player_id
),
player_ranking AS (
    SELECT
        ps.*,

        RANK() OVER (
            ORDER BY ps.total_minutes DESC
        ) AS minutes_rank,

        AVG(ps.total_minutes) OVER ()
            AS avg_total_minutes

    FROM player_statistics ps
)
SELECT
    p.first_name || ' ' || p.last_name AS player_name,

    p.primary_position,

    pr.matches_played,
    pr.actual_appearances,
    pr.total_minutes,
    pr.avg_minutes_per_match,
    pr.max_minutes_in_match,

    pr.minutes_rank,

    ROUND(pr.avg_total_minutes, 2)
        AS avg_player_minutes,

    ROUND(
        pr.total_minutes - pr.avg_total_minutes, 2
    ) AS difference_from_average

FROM player_ranking pr
JOIN player p
    ON p.player_id = pr.player_id

WHERE pr.actual_appearances >= 5

ORDER BY
    pr.total_minutes DESC;