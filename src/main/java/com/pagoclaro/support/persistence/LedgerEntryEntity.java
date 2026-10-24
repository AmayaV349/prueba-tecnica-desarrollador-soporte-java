package com.pagoclaro.support.persistence;

import com.pagoclaro.support.conciliacion.LedgerEntryType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "ledger_entry")
public class LedgerEntryEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entry_type", nullable = false, length = 20)
  private LedgerEntryType entryType;

  @Column(nullable = false, precision = 18, scale = 2)
  private BigDecimal amount;

  @Column(name = "related_entry_id")
  private Long relatedEntryId;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected LedgerEntryEntity() {}

  public LedgerEntryEntity(
      Long paymentId,
      LedgerEntryType entryType,
      BigDecimal amount,
      Long relatedEntryId,
      Instant createdAt) {
    this.paymentId = paymentId;
    this.entryType = entryType;
    this.amount = amount;
    this.relatedEntryId = relatedEntryId;
    this.createdAt = createdAt;
  }

  public Long getId() {
    return id;
  }

  public LedgerEntryType getEntryType() {
    return entryType;
  }

  public BigDecimal getAmount() {
    return amount;
  }

  public Long getRelatedEntryId() {
    return relatedEntryId;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }
}
