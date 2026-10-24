package com.pagoclaro.support.persistence;

import com.pagoclaro.support.conciliacion.AuditAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "support_audit")
public class SupportAuditEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 40)
  private AuditAction action;

  @Column(nullable = false, length = 400)
  private String reason;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected SupportAuditEntity() {}

  public SupportAuditEntity(Long paymentId, AuditAction action, String reason, Instant createdAt) {
    this.paymentId = paymentId;
    this.action = action;
    this.reason = reason;
    this.createdAt = createdAt;
  }

  public AuditAction getAction() {
    return action;
  }

  public String getReason() {
    return reason;
  }
}
