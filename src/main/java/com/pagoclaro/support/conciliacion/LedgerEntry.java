package com.pagoclaro.support.conciliacion;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

public record LedgerEntry(
    Long id,
    LedgerEntryType type,
    BigDecimal amount,
    Long relatedEntryId,
    Instant createdAt) {

  public LedgerEntry {
    if (id == null || type == null || amount == null || createdAt == null) {
      throw new IllegalArgumentException("El movimiento está incompleto");
    }
    if (type == LedgerEntryType.REVERSAL && relatedEntryId == null) {
      throw new IllegalArgumentException("Un reverso debe apuntar al débito que corrige");
    }
    if (type == LedgerEntryType.DEBIT && relatedEntryId != null) {
      throw new IllegalArgumentException("Un débito no corrige otro movimiento");
    }
    amount = amount.setScale(2, RoundingMode.UNNECESSARY);
  }
}
