package com.pagoclaro.support.conciliacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * Evidencia ya leída de un pago. La política no la modifica.
 */
public record PaymentEvidence(
    String externalRef,
    PaymentStatus status,
    BigDecimal amount,
    List<ProviderEvent> events,
    List<LedgerEntry> entries,
    List<AuditRecord> audits) {

  public PaymentEvidence {
    if (externalRef == null || externalRef.isBlank() || status == null || amount == null) {
      throw new IllegalArgumentException("El pago está incompleto");
    }
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
    events = List.copyOf(events);
    entries = List.copyOf(entries);
    audits = List.copyOf(audits);
  }
}
