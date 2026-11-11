-- Longest streaks of consecutive matches with at least one goal (gaps-and-islands technique).
-- Only matches in which the player actually took part (started or came on as a substitute) are counted;
-- unused substitutes also have an Appearance row and are therefore excluded.
-- A match played without scoring ends the streak. Shoot-out penalties and own goals are not counted.
WITH player_matches AS (
    SELECT
        a.player_id,
        m.scheduled_at,
        COUNT(me.match_event_id) AS goals,
        ROW_NUMBER() OVER (PARTITION BY a.player_id ORDER BY m.scheduled_at) AS match_no
    FROM appearance a
    JOIN match m ON m.match_id = a.match_id
    LEFT JOIN matchevent me ON me.appearance_id = a.appearance_id
                           AND me.is_cancelled = false
                           AND me.match_period <> 'penalties'
                           AND me.event_type_id IN (SELECT event_type_id FROM eventtype
                                                    WHERE name IN ('goal', 'penalty_goal'))
    WHERE a.player_id IS NOT NULL
      AND m.status = 'finished'
      AND (a.is_starting OR EXISTS (
              SELECT 1
              FROM matchevent sub
              JOIN eventtype et ON et.event_type_id = sub.event_type_id
              WHERE sub.appearance_id = a.appearance_id AND et.name = 'substitution_on'))
    GROUP BY a.player_id, m.scheduled_at
),
islands AS (
    -- Within a streak, match_no and the row number among scoring matches increase together,
    -- so their difference is constant and identifies the streak (island).
    SELECT
        pm.*,
        pm.match_no - ROW_NUMBER() OVER (PARTITION BY pm.player_id ORDER BY pm.scheduled_at) AS island_id
    FROM player_matches pm
    WHERE pm.goals > 0
)
SELECT
    p.first_name || ' ' || p.last_name AS player_name,
    p.primary_position,
    COUNT(*) AS streak_length,
    SUM(i.goals) AS goals_in_streak,
    MIN(i.scheduled_at)::date AS streak_start,
    MAX(i.scheduled_at)::date AS streak_end
FROM islands i
JOIN player p ON p.player_id = i.player_id
GROUP BY p.player_id, p.first_name, p.last_name, p.primary_position, i.island_id
HAVING COUNT(*) >= 3
ORDER BY streak_length DESC, goals_in_streak DESC
LIMIT 15;
