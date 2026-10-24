package com.pagoclaro.support.conciliacion;

import java.util.List;
import java.util.Optional;

/** Efectos propuestos. Quien llama decide si los escribe. */
public record ReconciliationDecision(
    ReconciliationOutcome outcome,
    PaymentStatus resultingStatus,
    List<PlannedEntry> entriesToAdd,
    Optional<AuditRecord> auditToRecord) {

  public ReconciliationDecision {
    if (outcome == null || resultingStatus == null || entriesToAdd == null || auditToRecord == null) {
      throw new IllegalArgumentException("La decisión está incompleta");
    }
    entriesToAdd = List.copyOf(entriesToAdd);
  }
}
