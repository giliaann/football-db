-- Optimized version of queries/p_player_scoring_streaks.sql (same result).
-- The original reads MatchEvent twice: a LEFT JOIN for goals and a correlated EXISTS for
-- 'substitution_on'. Here a single pass over MatchEvent produces both facts per appearance
-- (goals with FILTER, "came on" with BOOL_OR), so the hashed SubPlan disappears.
-- As in the original, cancelled substitution events still count as "came on".
WITH appearance_events AS (
    SELECT
        me.appearance_id,
        COUNT(*) FILTER (WHERE et.name IN ('goal', 'penalty_goal')
                           AND me.is_cancelled = false
                           AND me.match_period <> 'penalties') AS goals,
        BOOL_OR(et.name = 'substitution_on') AS came_on
    FROM matchevent me
    JOIN eventtype et ON et.event_type_id = me.event_type_id
    WHERE et.name IN ('goal', 'penalty_goal', 'substitution_on')
    GROUP BY me.appearance_id
),
player_matches AS (
    SELECT
        a.player_id,
        m.scheduled_at,
        COALESCE(ae.goals, 0) AS goals,
        ROW_NUMBER() OVER (PARTITION BY a.player_id ORDER BY m.scheduled_at) AS match_no
    FROM appearance a
    JOIN match m ON m.match_id = a.match_id
    LEFT JOIN appearance_events ae ON ae.appearance_id = a.appearance_id
    WHERE a.player_id IS NOT NULL
      AND m.status = 'finished'
      AND (a.is_starting OR ae.came_on)
),
islands AS (
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
