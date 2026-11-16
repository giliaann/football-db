-- Optimized version of queries/p_referee_strictness.sql (same result).
-- The original LEFT JOINs every Appearance (~135 thousand rows) and every MatchEvent of each match
-- only to count cards with FILTER. Here the query starts from card events (~12.5 thousand rows),
-- reaches the match through Appearance and attaches the per-match counts to Match with a LEFT JOIN,
-- so matches without cards still count as 0.
WITH card_counts AS (
    SELECT
        a.match_id,
        COUNT(*) AS cards,
        COUNT(*) FILTER (WHERE et.name = 'red_card') AS red_cards
    FROM matchevent me
    JOIN eventtype et ON et.event_type_id = me.event_type_id
    JOIN appearance a ON a.appearance_id = me.appearance_id
    WHERE et.name IN ('yellow_card', 'red_card')
      AND me.is_cancelled = false
    GROUP BY a.match_id
),
match_cards AS (
    SELECT
        m.match_id,
        m.referee_id,
        e.competition_id,
        COALESCE(cc.cards, 0) AS cards,
        COALESCE(cc.red_cards, 0) AS red_cards
    FROM match m
    JOIN stage s ON s.stage_id = m.stage_id
    JOIN edition e ON e.edition_id = s.edition_id
    LEFT JOIN card_counts cc ON cc.match_id = m.match_id
    WHERE m.status = 'finished'
),
referee_stats AS (
    SELECT
        mc.referee_id,
        mc.competition_id,
        COUNT(*) AS matches,
        AVG(mc.cards) AS avg_cards,
        COUNT(*) FILTER (WHERE mc.red_cards > 0) * 100.0 / COUNT(*) AS pct_matches_with_red
    FROM match_cards mc
    GROUP BY mc.referee_id, mc.competition_id
    HAVING COUNT(*) >= 10
)
SELECT
    c.name AS competition_name,
    r.first_name || ' ' || r.last_name AS referee_name,
    rs.matches,
    ROUND(rs.avg_cards, 2) AS avg_cards,
    ROUND(AVG(rs.avg_cards) OVER (PARTITION BY rs.competition_id), 2) AS competition_avg_cards,
    ROUND(rs.avg_cards * 100 / AVG(rs.avg_cards) OVER (PARTITION BY rs.competition_id), 1) AS strictness_index,
    ROUND(rs.pct_matches_with_red, 1) AS pct_matches_with_red,
    CASE NTILE(4) OVER (PARTITION BY rs.competition_id ORDER BY rs.avg_cards)
        WHEN 4 THEN 'Strict'
        WHEN 1 THEN 'Lenient'
        ELSE 'Average'
    END AS referee_profile
FROM referee_stats rs
JOIN referee r ON r.referee_id = rs.referee_id
JOIN competition c ON c.competition_id = rs.competition_id
ORDER BY competition_name, strictness_index DESC;
