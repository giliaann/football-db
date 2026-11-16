-- Optimized version of queries/k_player_attacking_ability_k.sql (same result).
-- 1. Goals and assists are counted per appearance BEFORE the join with Appearance, so only
--    ~13 thousand event rows are aggregated instead of ~146 thousand appearance x event rows.
-- 2. COUNT(DISTINCT ...) is replaced by COUNT(*) FILTER: every event row is already unique, and
--    without DISTINCT the planner may use HashAggregate instead of sorting (no spill to disk).
-- 3. All window functions share one ordering (scheduled_at DESC), so only one sort is needed.
--    "4 PRECEDING .. CURRENT ROW" in ascending order equals "CURRENT ROW .. 4 FOLLOWING" in descending order.
WITH event_counts AS (
    SELECT
        me.appearance_id,
        COUNT(*) FILTER (WHERE et.category = 'goal') AS goals,
        COUNT(*) FILTER (WHERE et.category = 'assist') AS assists
    FROM matchevent me
    JOIN eventtype et ON et.event_type_id = me.event_type_id
    WHERE me.is_cancelled = FALSE
      AND et.category IN ('goal', 'assist')
    GROUP BY me.appearance_id
),
player_matches AS (
    SELECT
        a.player_id,
        m.scheduled_at,
        COALESCE(ec.goals, 0) AS goals,
        COALESCE(ec.assists, 0) AS assists
    FROM appearance a
    JOIN match m ON m.match_id = a.match_id
    LEFT JOIN event_counts ec ON ec.appearance_id = a.appearance_id
    WHERE a.player_id IS NOT NULL
      AND m.status = 'finished'
),
player_form AS (
    SELECT
        pm.player_id,
        pm.goals,
        pm.assists,
        AVG(pm.goals + pm.assists) OVER last5 AS avg_contributions,
        COUNT(*) OVER last5 AS matches_in_window,
        ROW_NUMBER() OVER newest_first AS match_rank
    FROM player_matches pm
    WINDOW newest_first AS (PARTITION BY pm.player_id ORDER BY pm.scheduled_at DESC),
           last5 AS (newest_first ROWS BETWEEN CURRENT ROW AND 4 FOLLOWING)
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
    ROUND(ps.avg_contributions_last_5, 2) AS avg_contributions_last_5,
    ROUND(AVG(ps.avg_contributions_last_5) OVER (), 2) AS avg_all_players,
    ROUND(ps.avg_contributions_last_5 - AVG(ps.avg_contributions_last_5) OVER (), 2) AS difference_from_average
FROM player_summary ps
JOIN player p ON p.player_id = ps.player_id
ORDER BY avg_contributions_last_5 DESC;
