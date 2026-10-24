package com.pagoclaro.support.service;

import com.pagoclaro.support.api.ReconciliationResponse;
import com.pagoclaro.support.conciliacion.AuditAction;
import com.pagoclaro.support.conciliacion.AuditRecord;
import com.pagoclaro.support.conciliacion.LedgerEntry;
import com.pagoclaro.support.conciliacion.LedgerEntryType;
import com.pagoclaro.support.conciliacion.PaymentEvidence;
import com.pagoclaro.support.conciliacion.PlannedEntry;
import com.pagoclaro.support.conciliacion.ProviderEvent;
import com.pagoclaro.support.conciliacion.ReconciliationDecision;
import com.pagoclaro.support.conciliacion.ReconciliationOutcome;
import com.pagoclaro.support.conciliacion.ReconciliationPolicy;
import com.pagoclaro.support.persistence.LedgerEntryEntity;
import com.pagoclaro.support.persistence.LedgerEntryRepository;
import com.pagoclaro.support.persistence.PaymentEntity;
import com.pagoclaro.support.persistence.PaymentRepository;
import com.pagoclaro.support.persistence.ProviderEventEntity;
import com.pagoclaro.support.persistence.ProviderEventRepository;
import com.pagoclaro.support.persistence.SupportAuditEntity;
import com.pagoclaro.support.persistence.SupportAuditRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReconciliationService {

  private static final Logger log = LoggerFactory.getLogger(ReconciliationService.class);

  private final PaymentRepository payments;
  private final ProviderEventRepository events;
  private final LedgerEntryRepository ledger;
  private final SupportAuditRepository audits;
  private final ReconciliationPolicy policy;
  private final Clock clock;

  public ReconciliationService(
      PaymentRepository payments,
      ProviderEventRepository events,
      LedgerEntryRepository ledger,
      SupportAuditRepository audits,
      ReconciliationPolicy policy,
      Clock clock) {
    this.payments = payments;
    this.events = events;
    this.ledger = ledger;
    this.audits = audits;
    this.policy = policy;
    this.clock = clock;
  }

  @Transactional
  public ReconciliationResponse reconcile(String externalRef) {
    PaymentEntity payment = payments.findByExternalRefForUpdate(externalRef)
        .orElseThrow(() -> new PaymentNotFoundException(externalRef));

    List<ProviderEventEntity> providerEvents = events.findByPaymentId(payment.getId());
    List<LedgerEntryEntity> entries = ledger.findByPaymentId(payment.getId());
    List<SupportAuditEntity> previousAudits = audits.findByPaymentId(payment.getId());
    PaymentEvidence evidence = toEvidence(payment, providerEvents, entries, previousAudits);

    ReconciliationDecision decision = policy.decide(evidence);
    apply(payment, decision);
    List<LedgerEntryEntity> storedEntries = ledger.findByPaymentId(payment.getId());
    String reason = reasonOf(decision, evidence);

    log.info("Conciliación externalRef={} outcome={}", externalRef, decision.outcome());
    return new ReconciliationResponse(
        externalRef,
        decision.outcome().name(),
        decision.resultingStatus().name(),
        netDebit(storedEntries),
        reason);
  }

  private void apply(PaymentEntity payment, ReconciliationDecision decision) {
    Instant now = clock.instant();
    boolean paymentChanged = false;
    if (payment.getStatus() != decision.resultingStatus()) {
      payment.setStatus(decision.resultingStatus());
      paymentChanged = true;
    }
    for (PlannedEntry planned : decision.entriesToAdd()) {
      ledger.save(new LedgerEntryEntity(
          payment.getId(),
          planned.type(),
          planned.amount(),
          planned.relatedEntryId(),
          now));
      paymentChanged = true;
    }
    decision.auditToRecord().ifPresent(audit -> audits.save(new SupportAuditEntity(
        payment.getId(),
        audit.action(),
        audit.reason(),
        now)));
    if (paymentChanged) {
      payment.setUpdatedAt(now);
    }
  }

  private PaymentEvidence toEvidence(
      PaymentEntity payment,
      List<ProviderEventEntity> providerEvents,
      List<LedgerEntryEntity> entries,
      List<SupportAuditEntity> previousAudits) {
    return new PaymentEvidence(
        payment.getExternalRef(),
        payment.getStatus(),
        payment.getAmount(),
        providerEvents.stream()
            .map(event -> new ProviderEvent(event.getProviderEventId(), event.getProviderStatus()))
            .toList(),
        entries.stream()
            .map(entry -> new LedgerEntry(
                entry.getId(),
                entry.getEntryType(),
                entry.getAmount(),
                entry.getRelatedEntryId(),
                entry.getCreatedAt()))
            .toList(),
        previousAudits.stream()
            .map(audit -> new AuditRecord(audit.getAction(), audit.getReason()))
            .toList());
  }

  private String reasonOf(ReconciliationDecision decision, PaymentEvidence evidence) {
    if (decision.auditToRecord().isPresent()) {
      return decision.auditToRecord().get().reason();
    }
    if (decision.outcome() == ReconciliationOutcome.ALREADY_CONSISTENT) {
      return "El pago ya está consistente con la evidencia. No se requieren cambios.";
    }
    return evidence.audits().stream()
        .filter(audit -> audit.action() == AuditAction.MANUAL_REVIEW)
        .reduce((first, second) -> second)
        .map(AuditRecord::reason)
        .orElse("El caso requiere revisión manual.");
  }

  private BigDecimal netDebit(List<LedgerEntryEntity> entries) {
    BigDecimal net = BigDecimal.ZERO.setScale(2);
    for (LedgerEntryEntity entry : entries) {
      if (entry.getEntryType() == LedgerEntryType.DEBIT) {
        net = net.add(entry.getAmount());
      } else {
        net = net.subtract(entry.getAmount());
      }
    }
    return net;
  }
}
