-- Pagos de las últimas 24 horas con neto distinto del esperado
-- o con estado incompatible con la evidencia del proveedor.
--
-- No modifica datos. Eventos y movimientos se agregan por separado:
-- unirlos en el mismo JOIN multiplicaría montos y conteos.
--
-- Con SYSTIMESTAMP, la muestra del 2026-10-13 no sale si hoy es posterior.
-- Para reproducir el incidente, cambie as_of por:
--   TIMESTAMP '2026-10-13 12:00:00'
-- Resultado esperado con 04_muestra.sql:
--   PAY-AX91  APPROVED_MISMATCH
--   PAY-BX17  APPROVED_MISMATCH   (neto 178000, monto 89000)
--   PAY-CQ22  CONTRADICTORY
--   PAY-DK44  no aparece

WITH params AS (
  SELECT CAST(SYSTIMESTAMP AS TIMESTAMP) AS as_of
  FROM dual
),
recent_payment AS (
  SELECT p.id
  FROM payment p
  CROSS JOIN params
  WHERE p.updated_at >= params.as_of - INTERVAL '24' HOUR
  UNION
  SELECT e.payment_id
  FROM provider_event e
  CROSS JOIN params
  WHERE e.received_at >= params.as_of - INTERVAL '24' HOUR
  UNION
  SELECT l.payment_id
  FROM ledger_entry l
  CROSS JOIN params
  WHERE l.created_at >= params.as_of - INTERVAL '24' HOUR
),
event_count AS (
  SELECT payment_id,
         SUM(CASE WHEN provider_status = 'APPROVED' THEN 1 ELSE 0 END) AS approved_count,
         SUM(CASE WHEN provider_status = 'DECLINED' THEN 1 ELSE 0 END) AS declined_count
  FROM provider_event
  GROUP BY payment_id
),
ledger_net AS (
  SELECT payment_id,
         SUM(CASE
               WHEN entry_type = 'DEBIT' THEN amount
               WHEN entry_type = 'REVERSAL' THEN -amount
             END) AS net_debit
  FROM ledger_entry
  GROUP BY payment_id
),
picture AS (
  SELECT p.external_ref,
         p.status,
         p.amount,
         NVL(e.approved_count, 0) AS approved_count,
         NVL(e.declined_count, 0) AS declined_count,
         NVL(l.net_debit, 0) AS net_debit
  FROM payment p
  JOIN recent_payment r ON r.id = p.id
  LEFT JOIN event_count e ON e.payment_id = p.id
  LEFT JOIN ledger_net l ON l.payment_id = p.id
)
SELECT external_ref,
       status,
       amount,
       approved_count,
       declined_count,
       net_debit,
       CASE
         WHEN approved_count > 0 AND declined_count > 0 THEN 'CONTRADICTORY'
         WHEN approved_count = 0 AND declined_count = 0 THEN 'INSUFFICIENT'
         WHEN approved_count > 0 THEN 'APPROVED_MISMATCH'
         ELSE 'DECLINED_MISMATCH'
       END AS finding
FROM picture
WHERE (approved_count > 0 AND declined_count > 0)
   OR (approved_count = 0 AND declined_count = 0
       AND (status IN ('CONFIRMED', 'REJECTED') OR net_debit <> 0))
   OR (approved_count > 0 AND declined_count = 0
       AND (status <> 'CONFIRMED' OR net_debit <> amount))
   OR (declined_count > 0 AND approved_count = 0
       AND (status <> 'REJECTED' OR net_debit <> 0))
ORDER BY external_ref;
