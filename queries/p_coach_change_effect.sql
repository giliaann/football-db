-- Effect of a coaching change: average points per match in the 5 matches before and after the change.
-- The coach of a team in a given match is identified by the Appearance row with a non-null coach_id.
-- Only league matches are considered, so that cup and Champions League fixtures, played against
-- opponents of a substantially different level, do not distort the measure of form.
WITH team_matches AS (
    SELECT
        a.team_id,
        a.coach_id,
        m.scheduled_at,
        CASE
            WHEN m.home_goals = m.away_goals THEN 1
            WHEN (a.team_id = m.home_team_id) = (m.home_goals > m.away_goals) THEN 3
            ELSE 0
        END AS pts
    FROM appearance a
    JOIN match m ON m.match_id = a.match_id
    JOIN stage s ON s.stage_id = m.stage_id
    JOIN edition e ON e.edition_id = s.edition_id
    JOIN competition c ON c.competition_id = e.competition_id
    WHERE a.coach_id IS NOT NULL
      AND m.status = 'finished'
      AND c.format = 'league'
),
windows AS (
    SELECT
        tm.team_id,
        tm.coach_id,
        tm.scheduled_at,
        LAG(tm.coach_id) OVER w AS prev_coach_id,
        COUNT(*) OVER (w ROWS BETWEEN 5 PRECEDING AND 1 PRECEDING) AS n_before,
        COUNT(*) OVER (w ROWS BETWEEN CURRENT ROW AND 4 FOLLOWING) AS n_after,
        AVG(tm.pts) OVER (w ROWS BETWEEN 5 PRECEDING AND 1 PRECEDING) AS ppm_before,
        AVG(tm.pts) OVER (w ROWS BETWEEN CURRENT ROW AND 4 FOLLOWING) AS ppm_after,
        -- MIN = MAX within a frame guarantees that a single coach led the team in all 5 matches.
        MIN(tm.coach_id) OVER (w ROWS BETWEEN CURRENT ROW AND 4 FOLLOWING) AS min_coach_after,
        MAX(tm.coach_id) OVER (w ROWS BETWEEN CURRENT ROW AND 4 FOLLOWING) AS max_coach_after,
        MIN(tm.coach_id) OVER (w ROWS BETWEEN 5 PRECEDING AND 1 PRECEDING) AS min_coach_before,
        MAX(tm.coach_id) OVER (w ROWS BETWEEN 5 PRECEDING AND 1 PRECEDING) AS max_coach_before
    FROM team_matches tm
    WINDOW w AS (PARTITION BY tm.team_id ORDER BY tm.scheduled_at)
)
SELECT
    t.name AS team_name,
    w.scheduled_at::date AS first_match_new_coach,
    old_c.first_name || ' ' || old_c.last_name AS old_coach,
    new_c.first_name || ' ' || new_c.last_name AS new_coach,
    ROUND(w.ppm_before, 2) AS ppm_before,
    ROUND(w.ppm_after, 2) AS ppm_after,
    ROUND(w.ppm_after - w.ppm_before, 2) AS ppm_change,
    CASE
        WHEN w.ppm_after > w.ppm_before THEN 'Improvement'
        WHEN w.ppm_after < w.ppm_before THEN 'Decline'
        ELSE 'No change'
    END AS effect,
    -- Average change across all coaching changes, provided as a baseline.
    ROUND(AVG(w.ppm_after - w.ppm_before) OVER (), 2) AS avg_ppm_change_all
FROM windows w
JOIN team t ON t.team_id = w.team_id
JOIN coach old_c ON old_c.coach_id = w.prev_coach_id
JOIN coach new_c ON new_c.coach_id = w.coach_id
WHERE w.coach_id <> w.prev_coach_id
  AND w.n_before = 5
  AND w.n_after = 5
  AND w.min_coach_before = w.max_coach_before
  AND w.min_coach_after = w.max_coach_after
ORDER BY ppm_change DESC;
