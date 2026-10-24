package com.pagoclaro.support.persistence;

import com.pagoclaro.support.conciliacion.PaymentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class PaymentEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "external_ref", nullable = false, unique = true, length = 40)
  private String externalRef;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private PaymentStatus status;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Version
  @Column(name = "row_version", nullable = false)
  private Long rowVersion;

  protected PaymentEntity() {}

  public PaymentEntity(String externalRef, PaymentStatus status, BigDecimal amount, Instant updatedAt) {
    this.externalRef = externalRef;
    this.status = status;
    this.amount = amount;
    this.updatedAt = updatedAt;
  }

  public Long getId() {
    return id;
  }

  public String getExternalRef() {
    return externalRef;
  }

  public PaymentStatus getStatus() {
    return status;
  }

  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Instant getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(Instant updatedAt) {
    this.updatedAt = updatedAt;
  }
}
