package com.pagoclaro.support.persistence;

import com.pagoclaro.support.conciliacion.ProviderStatus;
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
@Table(name = "provider_event")
public class ProviderEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "payment_id", nullable = false)
  private Long paymentId;

  @Column(name = "provider_event_id", nullable = false, unique = true, length = 60)
  private String providerEventId;

  @Enumerated(EnumType.STRING)
  @Column(name = "provider_status", nullable = false, length = 20)
  private ProviderStatus providerStatus;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  protected ProviderEventEntity() {}

  public ProviderEventEntity(Long paymentId, String providerEventId, ProviderStatus providerStatus, Instant receivedAt) {
    this.paymentId = paymentId;
    this.providerEventId = providerEventId;
    this.providerStatus = providerStatus;
    this.receivedAt = receivedAt;
  }

  public String getProviderEventId() {
    return providerEventId;
  }

  public ProviderStatus getProviderStatus() {
    return providerStatus;
  }
}
