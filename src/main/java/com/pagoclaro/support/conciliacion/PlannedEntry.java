package com.pagoclaro.support.conciliacion;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Movimiento nuevo. Todavía no está en el ledger. */
public record PlannedEntry(LedgerEntryType type, BigDecimal amount, Long relatedEntryId) {

  public PlannedEntry {
    if (type == null || amount == null) {
      throw new IllegalArgumentException("El movimiento planificado está incompleto");
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
