    WITH goal_intervals AS (
        SELECT
            me.match_event_id,
            me.minute,
            me.stoppage_minute,
            me.match_period,
            et.name AS event_type,
            CASE
                WHEN me.match_period IN ('extra_time_1', 'extra_time_2') THEN 'Dogrywka (91-120)'
                WHEN me.stoppage_minute IS NOT NULL AND me.match_period = 'first_half' THEN '1. połowa - czas doliczony (45+)'
                WHEN me.stoppage_minute IS NOT NULL AND me.match_period = 'second_half' THEN '2. połowa - czas doliczony (90+)'
                WHEN me.minute BETWEEN 1 AND 15 THEN '01-15 min'
                WHEN me.minute BETWEEN 16 AND 30 THEN '16-30 min'
                WHEN me.minute BETWEEN 31 AND 45 THEN '31-45 min'
                WHEN me.minute BETWEEN 46 AND 60 THEN '46-60 min'
                WHEN me.minute BETWEEN 61 AND 75 THEN '61-75 min'
                WHEN me.minute BETWEEN 76 AND 90 THEN '76-90 min'
                ELSE 'Inne'
            END AS match_interval
        FROM matchevent me
        JOIN eventtype et ON et.event_type_id = me.event_type_id
        WHERE et.name IN ('goal', 'penalty_goal', 'own_goal')
          AND me.is_cancelled = false
          AND me.match_period <> 'penalties'
    )
    SELECT
        match_interval,
        COUNT(*) AS total_goals,
        COUNT(*) FILTER (WHERE event_type = 'penalty_goal') AS penalty_goals,
        COUNT(*) FILTER (WHERE event_type = 'own_goal') AS own_goals,
        ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) AS pct_of_all_goals,
        ROUND(AVG(minute + COALESCE(stoppage_minute, 0)), 1) AS avg_effective_minute
    FROM goal_intervals
    GROUP BY match_interval
    ORDER BY
        CASE match_interval
            WHEN '01-15 min' THEN 1
            WHEN '16-30 min' THEN 2
            WHEN '31-45 min' THEN 3
            WHEN '1. połowa - czas doliczony (45+)' THEN 4
            WHEN '46-60 min' THEN 5
            WHEN '61-75 min' THEN 6
            WHEN '76-90 min' THEN 7
            WHEN '2. połowa - czas doliczony (90+)' THEN 8
            WHEN 'Dogrywka (91-120)' THEN 9
            ELSE 10
        END;
