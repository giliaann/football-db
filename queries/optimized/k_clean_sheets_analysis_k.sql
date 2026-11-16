-- Optimized version of queries/k_clean_sheets_analysis_k.sql (same result).
-- COALESCE(a.position, p.primary_position) = 'goalkeeper' is not sargable: no index can be used and
-- every starting appearance must be joined with Player first. The condition is split into two
-- branches of UNION ALL:
--   1) the position is recorded in Appearance -> partial index ix_appearance_starting_gk
--      (player_id) INCLUDE (match_id, team_id) WHERE is_starting AND position = 'goalkeeper';
--   2) the position is missing -> the player's primary position decides (idx_player_position).
-- A goalkeeper has one appearance per match, so COUNT(DISTINCT match_id) is replaced by COUNT(*).
WITH goalkeeper_appearances AS (
    SELECT a.player_id, a.team_id, a.match_id
    FROM appearance a
    WHERE a.is_starting
      AND a.position = 'goalkeeper'
      AND a.player_id IS NOT NULL

    UNION ALL

    SELECT a.player_id, a.team_id, a.match_id
    FROM player p
    JOIN appearance a ON a.player_id = p.player_id
    WHERE p.primary_position = 'goalkeeper'
      AND a.is_starting
      AND a.position IS NULL
),
goalkeeper_matches AS (
    SELECT
        ga.player_id,
        CASE WHEN ga.team_id = m.home_team_id THEN m.away_goals ELSE m.home_goals END AS goals_conceded
    FROM goalkeeper_appearances ga
    JOIN match m ON m.match_id = ga.match_id
    WHERE m.status = 'finished'
),
goalkeeper_statistics AS (
    SELECT
        gm.player_id,
        COUNT(*) AS matches_played,
        COUNT(*) FILTER (WHERE gm.goals_conceded = 0) AS clean_sheets,
        COUNT(*) FILTER (WHERE gm.goals_conceded > 0) AS matches_with_conceded_goals,
        ROUND(AVG(gm.goals_conceded), 2) AS avg_goals_conceded
    FROM goalkeeper_matches gm
    GROUP BY gm.player_id
),
goalkeeper_ranking AS (
    SELECT
        gs.*,
        ROUND(100.0 * gs.clean_sheets / NULLIF(gs.matches_played, 0), 2) AS clean_sheet_percentage,
        RANK() OVER (ORDER BY gs.clean_sheets DESC) AS goalkeeper_rank,
        AVG(gs.clean_sheets) OVER () AS avg_clean_sheets
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
    ROUND(gr.avg_clean_sheets, 2) AS avg_clean_sheets,
    CASE
        WHEN gr.clean_sheets > gr.avg_clean_sheets THEN 'Above average'
        WHEN gr.clean_sheets < gr.avg_clean_sheets THEN 'Below average'
        ELSE 'Average'
    END AS goalkeeper_performance
FROM goalkeeper_ranking gr
JOIN player p ON p.player_id = gr.player_id
WHERE gr.matches_played >= 5
ORDER BY gr.clean_sheets DESC, gr.clean_sheet_percentage DESC;
