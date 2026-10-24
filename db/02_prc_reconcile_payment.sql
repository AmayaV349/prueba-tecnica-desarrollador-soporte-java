-- Conciliación atómica de un pago en Oracle.
--
-- Hace las mismas reglas que ReconciliationPolicy. No borra ni actualiza
-- movimientos: solo inserta un débito faltante o un reverso, y solo cambia
-- el estado del pago. Si la evidencia no alcanza, no toca estado ni ledger
-- y deja una auditoría MANUAL_REVIEW.
--
-- No ejecuta COMMIT. Quien llama confirma o revierte.
-- Bloquea la fila del pago con SELECT FOR UPDATE antes de decidir.
-- Repetir la llamada, ya con el ajuste aplicado, no inserta otro movimiento
-- ni otra auditoría equivalente.
--
-- Las secuencias reemplazan el IDENTITY de H2. El DDL de Oracle confirma
-- solo ese cambio de estructura. El procedimiento, no.
--
-- Ensayo, después de cargar 04_muestra.sql. Debe terminar en ROLLBACK
-- si no quiere conservar la corrección:
--
--   VAR outcome VARCHAR2(40)
--   VAR detail  VARCHAR2(400)
--   EXEC prc_reconcile_payment('PAY-BX17', :outcome, :detail)
--   PRINT outcome
--   PRINT detail
--   ROLLBACK;

BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_ledger_entry START WITH 6000 INCREMENT BY 1 NOCACHE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

BEGIN
  EXECUTE IMMEDIATE 'CREATE SEQUENCE seq_support_audit START WITH 1 INCREMENT BY 1 NOCACHE';
EXCEPTION
  WHEN OTHERS THEN
    IF SQLCODE != -955 THEN RAISE; END IF;
END;
/

CREATE OR REPLACE PROCEDURE prc_reconcile_payment (
  p_external_ref IN  VARCHAR2,
  p_outcome      OUT VARCHAR2,
  p_detail       OUT VARCHAR2
) AS
  TYPE t_plan_item IS RECORD (
    debit_id NUMBER,
    amount   NUMBER
  );
  TYPE t_plan IS TABLE OF t_plan_item;

  v_id             NUMBER;
  v_status         VARCHAR2(20);
  v_amount         NUMBER(18,2);
  v_approved       NUMBER;
  v_declined       NUMBER;
  v_net            NUMBER(18,2);
  v_kind           VARCHAR2(20);
  v_target_status  VARCHAR2(20);
  v_target_net     NUMBER(18,2);
  v_remaining      NUMBER(18,2);
  v_plan_ok        BOOLEAN;
  v_exists         NUMBER;
  v_reason         VARCHAR2(400);
  v_plan           t_plan := t_plan();

  FUNCTION plain_amount(p_amount NUMBER) RETURN VARCHAR2 IS
  BEGIN
    IF p_amount = TRUNC(p_amount) THEN
      RETURN TO_CHAR(p_amount, 'FM999999999999990');
    END IF;
    RETURN TO_CHAR(p_amount, 'FM999999999999990.00');
  END;

  PROCEDURE insert_audit(p_action VARCHAR2, p_reason VARCHAR2) IS
  BEGIN
    INSERT INTO support_audit (id, payment_id, action, reason, created_at)
    VALUES (seq_support_audit.NEXTVAL, v_id, p_action, p_reason, SYSTIMESTAMP);
  END;

  PROCEDURE touch_payment(p_status VARCHAR2) IS
  BEGIN
    UPDATE payment
       SET status = p_status,
           updated_at = SYSTIMESTAMP,
           row_version = row_version + 1
     WHERE id = v_id;
  END;

  PROCEDURE finish_review(p_reason VARCHAR2) IS
  BEGIN
    SELECT COUNT(*)
      INTO v_exists
      FROM support_audit
     WHERE payment_id = v_id
       AND action = 'MANUAL_REVIEW'
       AND reason = p_reason;

    IF v_exists = 0 THEN
      insert_audit('MANUAL_REVIEW', p_reason);
    END IF;

    p_outcome := 'MANUAL_REVIEW';
    p_detail := p_reason;
  END;

  PROCEDURE finish_consistent IS
  BEGIN
    SELECT COUNT(*)
      INTO v_exists
      FROM support_audit
     WHERE payment_id = v_id
       AND action IN ('RECONCILIATION_APPLIED', 'RECONCILIATION_NO_CHANGE');

    v_reason := 'El pago ya está consistente con la evidencia. No se requieren cambios.';
    IF v_exists = 0 THEN
      insert_audit('RECONCILIATION_NO_CHANGE', v_reason);
    END IF;

    p_outcome := 'ALREADY_CONSISTENT';
    p_detail := v_reason;
  END;

  PROCEDURE finish_applied(p_reason VARCHAR2) IS
  BEGIN
    touch_payment(v_target_status);
    insert_audit('RECONCILIATION_APPLIED', p_reason);
    p_outcome := 'RECONCILED';
    p_detail := p_reason;
  END;
BEGIN
  BEGIN
    SELECT id, status, amount
      INTO v_id, v_status, v_amount
      FROM payment
     WHERE external_ref = p_external_ref
       FOR UPDATE;
  EXCEPTION
    WHEN NO_DATA_FOUND THEN
      p_outcome := 'NOT_FOUND';
      p_detail := 'No existe un pago con esa referencia.';
      RETURN;
  END;

  SELECT NVL(SUM(CASE WHEN provider_status = 'APPROVED' THEN 1 ELSE 0 END), 0),
         NVL(SUM(CASE WHEN provider_status = 'DECLINED' THEN 1 ELSE 0 END), 0)
    INTO v_approved, v_declined
    FROM provider_event
   WHERE payment_id = v_id;

  SELECT NVL(SUM(CASE
                   WHEN entry_type = 'DEBIT' THEN amount
                   WHEN entry_type = 'REVERSAL' THEN -amount
                 END), 0)
    INTO v_net
    FROM ledger_entry
   WHERE payment_id = v_id;

  IF v_approved > 0 AND v_declined > 0 THEN
    v_kind := 'CONTRADICTORY';
  ELSIF v_approved > 0 THEN
    v_kind := 'APPROVED';
  ELSIF v_declined > 0 THEN
    v_kind := 'DECLINED';
  ELSE
    v_kind := 'INSUFFICIENT';
  END IF;

  IF v_kind = 'CONTRADICTORY' THEN
    finish_review('Hay eventos APPROVED y DECLINED para el mismo pago. No se aplica corrección.');
    RETURN;
  END IF;

  IF v_kind = 'INSUFFICIENT' THEN
    finish_review('No hay eventos del proveedor. No se aplica corrección.');
    RETURN;
  END IF;

  IF v_kind = 'APPROVED' AND v_status = 'REJECTED' THEN
    finish_review('El pago está REJECTED y la evidencia es APPROVED. No se reabre el pago.');
    RETURN;
  END IF;

  IF v_kind = 'APPROVED' THEN
    v_target_status := 'CONFIRMED';
    v_target_net := v_amount;
  ELSE
    v_target_status := 'REJECTED';
    v_target_net := 0;
  END IF;

  IF v_net = v_target_net AND v_status = v_target_status THEN
    finish_consistent;
    RETURN;
  END IF;

  IF v_net < v_target_net THEN
    IF v_kind <> 'APPROVED' OR v_net < 0 THEN
      finish_review('El ajuste del débito neto no coincide con movimientos completos. No se aplica corrección.');
      RETURN;
    END IF;

    INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at)
    VALUES (
      seq_ledger_entry.NEXTVAL,
      v_id,
      'DEBIT',
      v_target_net - v_net,
      NULL,
      SYSTIMESTAMP
    );

    finish_applied(
      'Se registró un débito de ' || plain_amount(v_target_net - v_net)
      || ' porque la evidencia es APPROVED y el débito neto no cubría el monto. El pago queda CONFIRMED.'
    );
    RETURN;
  END IF;

  IF v_net > v_target_net THEN
    v_remaining := v_net - v_target_net;
    v_plan_ok := TRUE;

    FOR debit IN (
      SELECT id, amount
        FROM ledger_entry
       WHERE payment_id = v_id
         AND entry_type = 'DEBIT'
         AND id NOT IN (
           SELECT related_entry_id
             FROM ledger_entry
            WHERE payment_id = v_id
              AND entry_type = 'REVERSAL'
              AND related_entry_id IS NOT NULL
         )
       ORDER BY created_at DESC, id DESC
    ) LOOP
      EXIT WHEN v_remaining = 0;
      IF debit.amount > v_remaining THEN
        v_plan_ok := FALSE;
        EXIT;
      END IF;
      v_plan.EXTEND;
      v_plan(v_plan.COUNT).debit_id := debit.id;
      v_plan(v_plan.COUNT).amount := debit.amount;
      v_remaining := v_remaining - debit.amount;
    END LOOP;

    IF v_remaining <> 0 THEN
      v_plan_ok := FALSE;
    END IF;

    IF NOT v_plan_ok THEN
      finish_review('El ajuste del débito neto no coincide con movimientos completos. No se aplica corrección.');
      RETURN;
    END IF;

    FOR i IN 1 .. v_plan.COUNT LOOP
      INSERT INTO ledger_entry (id, payment_id, entry_type, amount, related_entry_id, created_at)
      VALUES (
        seq_ledger_entry.NEXTVAL,
        v_id,
        'REVERSAL',
        v_plan(i).amount,
        v_plan(i).debit_id,
        SYSTIMESTAMP
      );
    END LOOP;

    IF v_kind = 'APPROVED' AND v_plan.COUNT = 1 THEN
      v_reason := 'Se registró un reverso de ' || plain_amount(v_plan(1).amount)
        || ' sobre el débito duplicado más reciente. El débito neto queda igual al monto.';
    ELSIF v_kind = 'APPROVED' THEN
      v_reason := 'Se registraron reversos de los débitos duplicados más recientes. El débito neto queda igual al monto.';
    ELSE
      v_reason := 'Se registraron reversos para dejar el débito neto en cero. El pago queda REJECTED.';
    END IF;

    finish_applied(v_reason);
    RETURN;
  END IF;

  IF v_kind = 'APPROVED' THEN
    v_reason := 'El pago queda CONFIRMED. La evidencia es APPROVED y el débito neto ya es igual al monto.';
  ELSE
    v_reason := 'El pago queda REJECTED. La evidencia es DECLINED y el débito neto ya es cero.';
  END IF;
  finish_applied(v_reason);
END;
/
