WITH player_match_goals AS (
    SELECT
        a.player_id,
        m.match_id,
        COUNT(me.match_event_id) AS goals_scored
    FROM appearance a
    JOIN match m
        ON m.match_id = a.match_id
    JOIN matchevent me
        ON me.appearance_id = a.appearance_id
    JOIN eventtype et
        ON et.event_type_id = me.event_type_id
    WHERE m.status = 'finished'
      AND et.category = 'goal'
      AND me.is_cancelled = FALSE
      AND a.player_id IS NOT NULL
    GROUP BY
        a.player_id,
        m.match_id
),
player_hattricks AS (
    SELECT
        pmg.player_id,
        pmg.match_id,
        pmg.goals_scored
    FROM player_match_goals pmg
    WHERE pmg.goals_scored >= 3
),
player_statistics AS (
    SELECT
        ph.player_id,
        COUNT(DISTINCT ph.match_id) AS hattricks_count,
        SUM(ph.goals_scored) AS goals_in_hattrick_matches,
        MAX(ph.goals_scored) AS most_goals_in_one_match
    FROM player_hattricks ph
    GROUP BY ph.player_id
),
player_ranking AS (
    SELECT
        ps.*,
        RANK() OVER (
            ORDER BY ps.hattricks_count DESC
        ) AS hattrick_rank,
        AVG(ps.hattricks_count) OVER ()
            AS avg_hattricks
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
JOIN player p
    ON p.player_id = pr.player_id
ORDER BY
    pr.hattricks_count DESC,
    pr.goals_in_hattrick_matches DESC;