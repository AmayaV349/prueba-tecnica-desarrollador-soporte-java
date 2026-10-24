-- Índices opcionales. La conciliación funciona sin ellos.
-- Aceleran la lectura por pago y evitan dos reversos del mismo débito.
-- Ejecutar una vez, con las tablas ya creadas.
-- En Oracle un índice único admite varios NULL, así que los débitos
-- (related_entry_id nulo) pueden repetirse. Cada débito solo admite un reverso.

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX ix_provider_event_payment ON provider_event (payment_id)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX ix_ledger_entry_payment ON ledger_entry (payment_id, entry_type)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE INDEX ix_support_audit_payment ON support_audit (payment_id, action)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE UNIQUE INDEX ux_ledger_entry_related ON ledger_entry (related_entry_id)';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE NOT IN (-955, -1408) THEN RAISE; END IF;
END;
/
