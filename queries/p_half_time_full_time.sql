-- Half-time versus full-time outcome, from the home team's perspective.
-- Shows how often a half-time lead is converted into a win and how often the result is reversed.
WITH first_half_goals AS (
    -- An own goal is recorded against the player who scored it, so it is credited to the opponent.
    SELECT
        m.match_id,
        CASE WHEN (et.name = 'own_goal') = (a.team_id = m.home_team_id) THEN 0 ELSE 1 END AS for_home
    FROM matchevent me
    JOIN eventtype et ON et.event_type_id = me.event_type_id
    JOIN appearance a ON a.appearance_id = me.appearance_id
    JOIN match m ON m.match_id = a.match_id
    WHERE et.name IN ('goal', 'penalty_goal', 'own_goal')
      AND me.match_period = 'first_half'
      AND me.is_cancelled = false
),
half_time_score AS (
    SELECT
        m.match_id,
        m.home_goals,
        m.away_goals,
        COALESCE(SUM(g.for_home), 0) AS home_ht,
        COALESCE(SUM(1 - g.for_home), 0) AS away_ht
    FROM match m
    LEFT JOIN first_half_goals g ON g.match_id = m.match_id
    WHERE m.status = 'finished'
    GROUP BY m.match_id, m.home_goals, m.away_goals
),
results AS (
    SELECT
        CASE
            WHEN home_ht > away_ht THEN '1. Home team leading'
            WHEN home_ht = away_ht THEN '2. Level'
            ELSE '3. Home team trailing'
        END AS half_time,
        CASE
            WHEN home_goals > away_goals THEN 'win'
            WHEN home_goals = away_goals THEN 'draw'
            ELSE 'loss'
        END AS full_time
    FROM half_time_score
)
SELECT
    half_time,
    COUNT(*) AS matches,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 1) AS pct_of_matches,
    ROUND(COUNT(*) FILTER (WHERE full_time = 'win') * 100.0 / COUNT(*), 1) AS pct_home_win,
    ROUND(COUNT(*) FILTER (WHERE full_time = 'draw') * 100.0 / COUNT(*), 1) AS pct_draw,
    ROUND(COUNT(*) FILTER (WHERE full_time = 'loss') * 100.0 / COUNT(*), 1) AS pct_home_loss
FROM results
GROUP BY half_time
ORDER BY half_time;
