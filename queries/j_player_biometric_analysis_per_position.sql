SELECT
    p.primary_position,
    COUNT(p.player_id) AS total_players,
    MIN(p.height_cm) AS min_height,
    ROUND(AVG(p.height_cm), 2) AS mean_height,
    ROUND(STDDEV_SAMP(p.height_cm), 2) AS stddev_height,
    PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY p.height_cm) AS q1_height,
    PERCENTILE_CONT(0.50) WITHIN GROUP (ORDER BY p.height_cm) AS median_height,
    PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY p.height_cm) AS q3_height,
    (PERCENTILE_CONT(0.75) WITHIN GROUP (ORDER BY p.height_cm) -
      PERCENTILE_CONT(0.25) WITHIN GROUP (ORDER BY p.height_cm)) AS iqr_height,
      MAX(p.height_cm) AS max_height,
      COUNT(*) FILTER (WHERE p.height_cm >= 190) AS players_190cm_plus,
      ROUND(COUNT(*) FILTER (WHERE p.height_cm >= 190) * 100.0 / COUNT(*), 1) AS pct_190cm_plus
 FROM player p
 WHERE p.height_cm IS NOT NULL
    AND p.primary_position IS NOT NULL
 GROUP BY p.primary_position