-- Optimized version of queries/k_player_hat_tricks_k.sql (same result).
-- Goals are grouped per appearance and filtered with HAVING >= 3 BEFORE joining Appearance and Match.
-- Fewer than 100 appearances survive, so they are joined by primary key instead of hashing the whole
-- Appearance table. One appearance = one player in one match, so COUNT(DISTINCT match_id) = COUNT(*).
WITH hattrick_appearances AS (
    SELECT
        me.appearance_id,
        COUNT(*) AS goals_scored
    FROM matchevent me
    JOIN eventtype et ON et.event_type_id = me.event_type_id
    WHERE et.category = 'goal'
      AND me.is_cancelled = FALSE
    GROUP BY me.appearance_id
    HAVING COUNT(*) >= 3
),
player_statistics AS (
    SELECT
        a.player_id,
        COUNT(*) AS hattricks_count,
        SUM(ha.goals_scored) AS goals_in_hattrick_matches,
        MAX(ha.goals_scored) AS most_goals_in_one_match
    FROM hattrick_appearances ha
    JOIN appearance a ON a.appearance_id = ha.appearance_id
    JOIN match m ON m.match_id = a.match_id
    WHERE m.status = 'finished'
      AND a.player_id IS NOT NULL
    GROUP BY a.player_id
),
player_ranking AS (
    SELECT
        ps.*,
        RANK() OVER (ORDER BY ps.hattricks_count DESC) AS hattrick_rank,
        AVG(ps.hattricks_count) OVER () AS avg_hattricks
    FROM player_statistics ps
)
SELECT
    p.first_name || ' ' || p.last_name AS player_name,
    p.primary_position,
    pr.hattricks_count,
    pr.goals_in_hattrick_matches,
    pr.most_goals_in_one_match,
    pr.hattrick_rank,
    ROUND(pr.avg_hattricks, 2) AS avg_hattricks
FROM player_ranking pr
JOIN player p ON p.player_id = pr.player_id
ORDER BY pr.hattricks_count DESC, pr.goals_in_hattrick_matches DESC;
