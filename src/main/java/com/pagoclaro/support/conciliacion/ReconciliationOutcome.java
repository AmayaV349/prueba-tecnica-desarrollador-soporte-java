package com.pagoclaro.support.conciliacion;

public enum ReconciliationOutcome {
  /** Esta ejecución cambia el estado o agrega un movimiento. */
  RECONCILED,
  /** El pago ya cumple el estado y el neto que pide la evidencia. */
  ALREADY_CONSISTENT,
  /** No hay evidencia suficiente para corregir. Los datos se conservan. */
  MANUAL_REVIEW
}
