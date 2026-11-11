-- Transfer market breakdown by player position and age at the date of the transfer.
-- Fees are stored in the buying club's currency and are converted to EUR using fixed exchange rates

WITH fx (code, units_per_eur) AS (
    VALUES ('EUR', 1.0), ('PLN', 4.3), ('GBP', 0.86), ('CHF', 0.95), ('CZK', 25.0),
           ('DKK', 7.46), ('SEK', 11.3), ('NOK', 11.5), ('HUF', 390.0)
),
transfers AS (
    SELECT
        p.primary_position,
        CASE
            WHEN EXTRACT(YEAR FROM AGE(t.transfer_date, p.birth_date)) <= 21 THEN '1. Under 22'
            WHEN EXTRACT(YEAR FROM AGE(t.transfer_date, p.birth_date)) <= 25 THEN '2. 22-25'
            WHEN EXTRACT(YEAR FROM AGE(t.transfer_date, p.birth_date)) <= 29 THEN '3. 26-29'
            ELSE '4. 30 and over'
        END AS age_group,
        tt.name AS transfer_type,
        t.amount / fx.units_per_eur / 1e6 AS fee_m_eur
    FROM transfer t
    JOIN player p ON p.player_id = t.player_id
    JOIN transfertype tt ON tt.transfer_type_id = t.transfer_type_id
    LEFT JOIN currency cu ON cu.currency_id = t.currency_id
    LEFT JOIN fx ON fx.code = cu.code
)
SELECT
    primary_position,
    age_group,
    COUNT(*) AS transfers,
    ROUND(COUNT(*) FILTER (WHERE transfer_type = 'permanent') * 100.0 / COUNT(*), 1) AS pct_permanent,
    ROUND(COUNT(*) FILTER (WHERE transfer_type = 'loan') * 100.0 / COUNT(*), 1) AS pct_loan,
    ROUND(COUNT(*) FILTER (WHERE transfer_type = 'free') * 100.0 / COUNT(*), 1) AS pct_free,
    -- Fee statistics cover paid transfers only (NULL amounts are ignored by the aggregates).
    ROUND(AVG(fee_m_eur)::numeric, 2) AS avg_fee_m_eur,
    ROUND((PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY fee_m_eur))::numeric, 2) AS median_fee_m_eur,
    ROUND(MAX(fee_m_eur)::numeric, 2) AS max_fee_m_eur,
    ROUND((SUM(fee_m_eur) * 100.0 / SUM(SUM(fee_m_eur)) OVER ())::numeric, 1) AS pct_of_market_value
FROM transfers
WHERE primary_position IS NOT NULL
GROUP BY primary_position, age_group
ORDER BY primary_position, age_group;
