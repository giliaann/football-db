-- Referee strictness relative to other referees officiating in the same competition.
-- Strictness index = referee's average cards per match / competition average * 100
-- (a value of 100 denotes an average referee). Only referees with at least 10 matches are included.
WITH match_cards AS (
    SELECT
        m.match_id,
        m.referee_id,
        e.competition_id,
        COUNT(*) FILTER (WHERE et.name IN ('yellow_card', 'red_card')) AS cards,
        COUNT(*) FILTER (WHERE et.name = 'red_card') AS red_cards
    FROM match m
    JOIN stage s ON s.stage_id = m.stage_id
    JOIN edition e ON e.edition_id = s.edition_id
    LEFT JOIN appearance a ON a.match_id = m.match_id
    LEFT JOIN matchevent me ON me.appearance_id = a.appearance_id AND me.is_cancelled = false
    LEFT JOIN eventtype et ON et.event_type_id = me.event_type_id
    WHERE m.status = 'finished'
    GROUP BY m.match_id, m.referee_id, e.competition_id
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
