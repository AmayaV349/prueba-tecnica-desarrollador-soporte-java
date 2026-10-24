-- Cuatro pagos de la muestra. Las fechas son las del incidente, 2026-10-13.
-- Cárguelo después de 00_tablas.sql.

INSERT INTO payment (id, external_ref, status, amount, updated_at, row_version) VALUES
(1, 'PAY-AX91', 'PENDING',   125000, TIMESTAMP '2026-10-13 09:10:00', 0);
INSERT INTO payment (id, external_ref, status, amount, updated_at, row_version) VALUES
(2, 'PAY-BX17', 'CONFIRMED',  89000, TIMESTAMP '2026-10-13 09:41:25', 0);
INSERT INTO payment (id, external_ref, status, amount, updated_at, row_version) VALUES
(3, 'PAY-CQ22', 'PENDING',    54000, TIMESTAMP '2026-10-13 09:20:00', 0);
INSERT INTO payment (id, external_ref, status, amount, updated_at, row_version) VALUES
(4, 'PAY-DK44', 'CONFIRMED',  31000, TIMESTAMP '2026-10-13 08:00:00', 0);

INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES
(101, 1, 'EVT-A1', 'APPROVED', TIMESTAMP '2026-10-13 09:10:00');
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES
(102, 2, 'EVT-B1', 'APPROVED', TIMESTAMP '2026-10-13 09:41:20');
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES
(103, 2, 'EVT-B2', 'APPROVED', TIMESTAMP '2026-10-13 09:41:25.390');
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES
(104, 3, 'EVT-C1', 'APPROVED', TIMESTAMP '2026-10-13 09:20:00');
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES
(105, 3, 'EVT-C2', 'DECLINED', TIMESTAMP '2026-10-13 09:21:00');
INSERT INTO provider_event (id, payment_id, provider_event_id, provider_status, received_at) VALUES
(106, 4, 'EVT-D1', 'APPROVED', TIMESTAMP '2026-10-13 08:00:00');

INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES
(4101, 1, 'DEBIT', 125000, NULL, TIMESTAMP '2026-10-13 09:10:00');
INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES
(5002, 2, 'DEBIT',  89000, NULL, TIMESTAMP '2026-10-13 09:41:20');
INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES
(5003, 2, 'DEBIT',  89000, NULL, TIMESTAMP '2026-10-13 09:41:25.390');
INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at) VALUES
(4401, 4, 'DEBIT',  31000, NULL, TIMESTAMP '2026-10-13 08:00:00');
