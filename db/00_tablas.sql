-- Modelo del enunciado, para ensayar el resto de scripts en un esquema Oracle vacío.
-- No cambia las reglas. La aplicación de esta entrega crea el mismo modelo en H2.

CREATE TABLE payment (
  id           NUMBER PRIMARY KEY,
  external_ref VARCHAR2(40)  UNIQUE NOT NULL,
  status       VARCHAR2(20)  NOT NULL,
  amount       NUMBER(18,2)  NOT NULL,
  updated_at   TIMESTAMP     NOT NULL,
  row_version  NUMBER        DEFAULT 0 NOT NULL
);

CREATE TABLE provider_event (
  id                NUMBER PRIMARY KEY,
  payment_id        NUMBER        NOT NULL REFERENCES payment(id),
  provider_event_id VARCHAR2(60)  UNIQUE NOT NULL,
  provider_status   VARCHAR2(20)  NOT NULL,
  received_at       TIMESTAMP     NOT NULL
);

CREATE TABLE ledger_entry (
  id               NUMBER PRIMARY KEY,
  payment_id       NUMBER        NOT NULL REFERENCES payment(id),
  entry_type       VARCHAR2(20)  NOT NULL,
  amount           NUMBER(18,2)  NOT NULL,
  related_entry_id NUMBER        NULL REFERENCES ledger_entry(id),
  created_at       TIMESTAMP     NOT NULL
);

CREATE TABLE support_audit (
  id         NUMBER PRIMARY KEY,
  payment_id NUMBER        NOT NULL REFERENCES payment(id),
  action     VARCHAR2(40)  NOT NULL,
  reason     VARCHAR2(400) NOT NULL,
  created_at TIMESTAMP     NOT NULL
);
