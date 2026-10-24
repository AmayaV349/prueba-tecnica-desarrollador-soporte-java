package com.pagoclaro.support.conciliacion;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decide la conciliación de un pago con la evidencia ya cargada.
 * No lee base de datos y no modifica los objetos que recibe.
 * La misma evidencia produce la misma decisión.
 */
public final class ReconciliationPolicy {

  public ReconciliationDecision decide(PaymentEvidence evidence) {
    Objects.requireNonNull(evidence, "La evidencia es obligatoria");
    EvidenceKind kind = classify(evidence.events());

    if (kind == EvidenceKind.CONTRADICTORY) {
      return manualReview(evidence, contradictoryReason());
    }
    if (kind == EvidenceKind.INSUFFICIENT) {
      return manualReview(evidence, insufficientReason());
    }
    if (kind == EvidenceKind.APPROVED && evidence.status() == PaymentStatus.REJECTED) {
      return manualReview(evidence, rejectedWithApprovalReason());
    }

    PaymentStatus targetStatus = targetStatus(kind);
    BigDecimal targetNet = targetNet(kind, evidence.amount());
    BigDecimal net = netDebit(evidence.entries());

    if (net.compareTo(targetNet) == 0 && evidence.status() == targetStatus) {
      return alreadyConsistent(evidence);
    }

    Optional<List<PlannedEntry>> planned = planEntries(kind, evidence.entries(), net, targetNet);
    if (planned.isEmpty()) {
      return manualReview(evidence, unmatchedLedgerReason());
    }

    List<PlannedEntry> entriesToAdd = planned.get();
    return reconciled(targetStatus, entriesToAdd, reasonFor(kind, entriesToAdd));
  }

  private ReconciliationDecision manualReview(PaymentEvidence evidence, String reason) {
    return new ReconciliationDecision(
        ReconciliationOutcome.MANUAL_REVIEW,
        evidence.status(),
        List.of(),
        reviewAudit(evidence.audits(), reason));
  }

  private ReconciliationDecision alreadyConsistent(PaymentEvidence evidence) {
    return new ReconciliationDecision(
        ReconciliationOutcome.ALREADY_CONSISTENT,
        evidence.status(),
        List.of(),
        consistentAudit(evidence.audits()));
  }

  private ReconciliationDecision reconciled(
      PaymentStatus targetStatus,
      List<PlannedEntry> entriesToAdd,
      String reason) {
    return new ReconciliationDecision(
        ReconciliationOutcome.RECONCILED,
        targetStatus,
        entriesToAdd,
        Optional.of(new AuditRecord(AuditAction.RECONCILIATION_APPLIED, reason)));
  }

  private Optional<AuditRecord> reviewAudit(List<AuditRecord> audits, String reason) {
    boolean alreadyRecorded = audits.stream()
        .anyMatch(audit -> audit.action() == AuditAction.MANUAL_REVIEW && reason.equals(audit.reason()));
    if (alreadyRecorded) {
      return Optional.empty();
    }
    return Optional.of(new AuditRecord(AuditAction.MANUAL_REVIEW, reason));
  }

  private Optional<AuditRecord> consistentAudit(List<AuditRecord> audits) {
    boolean alreadyClosed = audits.stream()
        .anyMatch(audit -> audit.action() == AuditAction.RECONCILIATION_APPLIED
            || audit.action() == AuditAction.RECONCILIATION_NO_CHANGE);
    if (alreadyClosed) {
      return Optional.empty();
    }
    return Optional.of(new AuditRecord(AuditAction.RECONCILIATION_NO_CHANGE, consistentReason()));
  }

  private Optional<List<PlannedEntry>> planEntries(
      EvidenceKind kind,
      List<LedgerEntry> entries,
      BigDecimal net,
      BigDecimal targetNet) {
    int comparison = net.compareTo(targetNet);
    if (comparison == 0) {
      return Optional.of(List.of());
    }
    if (comparison < 0) {
      if (kind != EvidenceKind.APPROVED || net.signum() < 0) {
        return Optional.empty();
      }
      return Optional.of(List.of(new PlannedEntry(
          LedgerEntryType.DEBIT,
          targetNet.subtract(net),
          null)));
    }
    return reversalsForExcess(entries, net.subtract(targetNet));
  }

  /**
   * Recorre los débitos sin reverso, del más nuevo al más viejo.
   * Cada reverso es el débito entero. Si el más nuevo no cabe en el exceso
   * que falta, no se usa un débito anterior y no se propone ningún movimiento.
   */
  private Optional<List<PlannedEntry>> reversalsForExcess(List<LedgerEntry> entries, BigDecimal excess) {
    List<PlannedEntry> planned = new ArrayList<>();
    BigDecimal remaining = excess;
    for (LedgerEntry debit : unreversedDebitsNewestFirst(entries)) {
      if (remaining.signum() == 0) {
        break;
      }
      if (debit.amount().compareTo(remaining) > 0) {
        return Optional.empty();
      }
      planned.add(new PlannedEntry(LedgerEntryType.REVERSAL, debit.amount(), debit.id()));
      remaining = remaining.subtract(debit.amount());
    }
    if (remaining.signum() != 0) {
      return Optional.empty();
    }
    return Optional.of(List.copyOf(planned));
  }

  private List<LedgerEntry> unreversedDebitsNewestFirst(List<LedgerEntry> entries) {
    Set<Long> reversedIds = entries.stream()
        .filter(entry -> entry.type() == LedgerEntryType.REVERSAL)
        .map(LedgerEntry::relatedEntryId)
        .collect(Collectors.toSet());

    return entries.stream()
        .filter(entry -> entry.type() == LedgerEntryType.DEBIT)
        .filter(entry -> !reversedIds.contains(entry.id()))
        .sorted(Comparator.comparing(LedgerEntry::createdAt).thenComparing(LedgerEntry::id).reversed())
        .toList();
  }

  private BigDecimal netDebit(List<LedgerEntry> entries) {
    BigDecimal net = BigDecimal.ZERO.setScale(2);
    for (LedgerEntry entry : entries) {
      if (entry.type() == LedgerEntryType.DEBIT) {
        net = net.add(entry.amount());
      } else {
        net = net.subtract(entry.amount());
      }
    }
    return net;
  }

  private EvidenceKind classify(List<ProviderEvent> events) {
    boolean approved = events.stream().anyMatch(event -> event.status() == ProviderStatus.APPROVED);
    boolean declined = events.stream().anyMatch(event -> event.status() == ProviderStatus.DECLINED);
    if (approved && declined) {
      return EvidenceKind.CONTRADICTORY;
    }
    if (approved) {
      return EvidenceKind.APPROVED;
    }
    if (declined) {
      return EvidenceKind.DECLINED;
    }
    return EvidenceKind.INSUFFICIENT;
  }

  private PaymentStatus targetStatus(EvidenceKind kind) {
    if (kind == EvidenceKind.APPROVED) {
      return PaymentStatus.CONFIRMED;
    }
    return PaymentStatus.REJECTED;
  }

  private BigDecimal targetNet(EvidenceKind kind, BigDecimal amount) {
    if (kind == EvidenceKind.APPROVED) {
      return amount;
    }
    return BigDecimal.ZERO.setScale(2);
  }

  private String reasonFor(EvidenceKind kind, List<PlannedEntry> entries) {
    if (entries.isEmpty()) {
      if (kind == EvidenceKind.APPROVED) {
        return confirmedWithMatchingNetReason();
      }
      return rejectedWithZeroNetReason();
    }
    boolean reversals = entries.stream().allMatch(entry -> entry.type() == LedgerEntryType.REVERSAL);
    boolean debits = entries.stream().allMatch(entry -> entry.type() == LedgerEntryType.DEBIT);
    if (reversals && kind == EvidenceKind.APPROVED && entries.size() == 1) {
      return singleDuplicateReversalReason(entries.get(0).amount());
    }
    if (reversals && kind == EvidenceKind.APPROVED) {
      return "Se registraron reversos de los débitos duplicados más recientes. El débito neto queda igual al monto.";
    }
    if (reversals) {
      return "Se registraron reversos para dejar el débito neto en cero. El pago queda REJECTED.";
    }
    if (debits && entries.size() == 1) {
      return missingDebitReason(entries.get(0).amount());
    }
    throw new IllegalStateException("La corrección planificada no tiene un motivo definido");
  }

  private String singleDuplicateReversalReason(BigDecimal amount) {
    return "Se registró un reverso de " + plain(amount)
        + " sobre el débito duplicado más reciente. El débito neto queda igual al monto.";
  }

  private String missingDebitReason(BigDecimal amount) {
    return "Se registró un débito de " + plain(amount)
        + " porque la evidencia es APPROVED y el débito neto no cubría el monto. El pago queda CONFIRMED.";
  }

  private String confirmedWithMatchingNetReason() {
    return "El pago queda CONFIRMED. La evidencia es APPROVED y el débito neto ya es igual al monto.";
  }

  private String rejectedWithZeroNetReason() {
    return "El pago queda REJECTED. La evidencia es DECLINED y el débito neto ya es cero.";
  }

  private String consistentReason() {
    return "El pago ya está consistente con la evidencia. No se requieren cambios.";
  }

  private String contradictoryReason() {
    return "Hay eventos APPROVED y DECLINED para el mismo pago. No se aplica corrección.";
  }

  private String insufficientReason() {
    return "No hay eventos del proveedor. No se aplica corrección.";
  }

  private String rejectedWithApprovalReason() {
    return "El pago está REJECTED y la evidencia es APPROVED. No se reabre el pago.";
  }

  private String unmatchedLedgerReason() {
    return "El ajuste del débito neto no coincide con movimientos completos. No se aplica corrección.";
  }

  private String plain(BigDecimal amount) {
    return amount.stripTrailingZeros().toPlainString();
  }

  private enum EvidenceKind {
    APPROVED,
    DECLINED,
    CONTRADICTORY,
    INSUFFICIENT
  }
}
