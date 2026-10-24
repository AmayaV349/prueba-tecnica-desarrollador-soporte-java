package com.pagoclaro.support.conciliacion;

public record AuditRecord(AuditAction action, String reason) {

  public AuditRecord {
    if (action == null || reason == null || reason.isBlank()) {
      throw new IllegalArgumentException("La auditoría está incompleta");
    }
  }
}
