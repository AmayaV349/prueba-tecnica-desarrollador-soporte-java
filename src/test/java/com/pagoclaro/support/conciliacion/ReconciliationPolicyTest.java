package com.pagoclaro.support.conciliacion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class ReconciliationPolicyTest {

  private static final String CONFIRMED_MATCHING_NET =
      "El pago queda CONFIRMED. La evidencia es APPROVED y el débito neto ya es igual al monto.";
  private static final String DUPLICATE_REVERSAL =
      "Se registró un reverso de 89000 sobre el débito duplicado más reciente. El débito neto queda igual al monto.";
  private static final String CONTRADICTORY =
      "Hay eventos APPROVED y DECLINED para el mismo pago. No se aplica corrección.";
  private static final String ALREADY_CONSISTENT =
      "El pago ya está consistente con la evidencia. No se requieren cambios.";

  private final ReconciliationPolicy policy = new ReconciliationPolicy();

  @Nested
  @DisplayName("Primera ejecución de los pagos de muestra")
  class FirstRun {

    @Test
    @DisplayName("PAY-AX91 solo pasa a CONFIRMED y conserva su débito")
    void payAx91ConfirmsWithoutNewEntries() {
      ReconciliationDecision decision = policy.decide(payAx91());

      assertEquals(ReconciliationOutcome.RECONCILED, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
      assertEquals(applied(CONFIRMED_MATCHING_NET), decision.auditToRecord());
    }

    @Test
    @DisplayName("PAY-BX17 reversa el débito más reciente y conserva los dos débitos")
    void payBx17ReversesTheNewestDebit() {
      ReconciliationDecision decision = policy.decide(payBx17());

      assertEquals(ReconciliationOutcome.RECONCILED, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertEquals(List.of(reversal("89000", 5003L)), decision.entriesToAdd());
      assertEquals(applied(DUPLICATE_REVERSAL), decision.auditToRecord());
    }

    @Test
    @DisplayName("PAY-CQ22 no cambia estado ni ledger")
    void payCq22StaysUntouched() {
      ReconciliationDecision decision = policy.decide(payCq22());

      assertEquals(ReconciliationOutcome.MANUAL_REVIEW, decision.outcome());
      assertEquals(PaymentStatus.PENDING, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
      assertEquals(review(CONTRADICTORY), decision.auditToRecord());
    }

    @Test
    @DisplayName("PAY-DK44 ya está sano y solo deja constancia")
    void payDk44IsAlreadyConsistent() {
      ReconciliationDecision decision = policy.decide(payDk44());

      assertEquals(ReconciliationOutcome.ALREADY_CONSISTENT, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
      assertEquals(noChange(ALREADY_CONSISTENT), decision.auditToRecord());
    }
  }

  @Nested
  @DisplayName("Segunda ejecución, después de aplicar la primera decisión")
  class SecondRun {

    @Test
    @DisplayName("PAY-AX91 no vuelve a escribir")
    void payAx91SecondRunWritesNothing() {
      assertSecondRunWritesNothing(payAx91(), ReconciliationOutcome.ALREADY_CONSISTENT);
    }

    @Test
    @DisplayName("PAY-BX17 no crea otro reverso ni otra auditoría")
    void payBx17SecondRunWritesNothing() {
      PaymentEvidence corrected = after(payBx17(), policy.decide(payBx17()));

      assertEquals(3, corrected.entries().size());
      assertEquals(LedgerEntryType.REVERSAL, corrected.entries().get(2).type());
      assertEquals(5003L, corrected.entries().get(2).relatedEntryId());
      assertSecondRunWritesNothing(payBx17(), ReconciliationOutcome.ALREADY_CONSISTENT);
    }

    @Test
    @DisplayName("PAY-CQ22 sigue en revisión y no repite la auditoría")
    void payCq22SecondRunWritesNothing() {
      assertSecondRunWritesNothing(payCq22(), ReconciliationOutcome.MANUAL_REVIEW);
    }

    @Test
    @DisplayName("PAY-DK44 no repite la auditoría de sin cambios")
    void payDk44SecondRunWritesNothing() {
      assertSecondRunWritesNothing(payDk44(), ReconciliationOutcome.ALREADY_CONSISTENT);
    }
  }

  @Nested
  @DisplayName("Casos que la muestra no trae")
  class Edges {

    @Test
    @DisplayName("Solo DECLINED y neto en cero pasa el pago a REJECTED")
    void declinedWithoutDebitRejectsThePayment() {
      PaymentEvidence evidence = payment(
          "PAY-DL01",
          PaymentStatus.PENDING,
          "54000",
          List.of(event("EVT-1", ProviderStatus.DECLINED)),
          List.of());

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.RECONCILED, decision.outcome());
      assertEquals(PaymentStatus.REJECTED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
    }

    @Test
    @DisplayName("Solo DECLINED reversa los débitos enteros hasta dejar el neto en cero")
    void declinedWithTwoDebitsReversesBothNewestFirst() {
      PaymentEvidence evidence = payment(
          "PAY-DL02",
          PaymentStatus.CONFIRMED,
          "31000",
          List.of(event("EVT-1", ProviderStatus.DECLINED)),
          List.of(
              debit(1L, "31000", "2026-10-13T09:00:00Z"),
              debit(2L, "31000", "2026-10-13T09:05:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.RECONCILED, decision.outcome());
      assertEquals(PaymentStatus.REJECTED, decision.resultingStatus());
      assertEquals(
          List.of(reversal("31000", 2L), reversal("31000", 1L)),
          decision.entriesToAdd());
    }

    @Test
    @DisplayName("Si el débito más reciente no cabe, no se usa uno anterior ni se escribe nada")
    void newestDebitThatDoesNotFitLeavesThePaymentUntouched() {
      PaymentEvidence evidence = approvedConfirmed(
          "PAY-EX01",
          "100.00",
          List.of(
              debit(1L, "40.00", "2026-10-13T09:00:00Z"),
              debit(2L, "100.00", "2026-10-13T09:05:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.MANUAL_REVIEW, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
    }

    @Test
    @DisplayName("Si la hora coincide, el reverso apunta al identificador mayor")
    void equalTimestampUsesTheHigherId() {
      Instant sameTime = Instant.parse("2026-10-13T09:41:25.390Z");
      PaymentEvidence evidence = approvedConfirmed(
          "PAY-EX03",
          "100.00",
          List.of(
              new LedgerEntry(10L, LedgerEntryType.DEBIT, money("100.00"), null, sameTime),
              new LedgerEntry(11L, LedgerEntryType.DEBIT, money("100.00"), null, sameTime)));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(List.of(reversal("100.00", 11L)), decision.entriesToAdd());
    }

    @Test
    @DisplayName("REJECTED con solo APPROVED no se reabre")
    void rejectedWithApprovalStaysRejected() {
      PaymentEvidence evidence = payment(
          "PAY-RJ01",
          PaymentStatus.REJECTED,
          "31000",
          List.of(event("EVT-1", ProviderStatus.APPROVED)),
          List.of(debit(1L, "31000", "2026-10-13T09:00:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.MANUAL_REVIEW, decision.outcome());
      assertEquals(PaymentStatus.REJECTED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
    }

    @Test
    @DisplayName("Sin eventos no se modifica un pago que parece sano")
    void missingEventsDoNotChangeAConsistentPayment() {
      PaymentEvidence evidence = payment(
          "PAY-NE01",
          PaymentStatus.CONFIRMED,
          "31000",
          List.of(),
          List.of(debit(1L, "31000", "2026-10-13T09:00:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.MANUAL_REVIEW, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
    }

    @Test
    @DisplayName("Solo APPROVED, PENDING y sin débito crea un débito por el monto")
    void approvedPendingWithoutDebitCreatesOne() {
      PaymentEvidence evidence = payment(
          "PAY-DB01",
          PaymentStatus.PENDING,
          "125000",
          List.of(event("EVT-1", ProviderStatus.APPROVED)),
          List.of());

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.RECONCILED, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertEquals(List.of(debitPlanned("125000")), decision.entriesToAdd());
    }

    @Test
    @DisplayName("Si ya hay un débito parcial, se agrega solo la diferencia")
    void partialDebitIsCompletedWithTheExactDifference() {
      PaymentEvidence evidence = payment(
          "PAY-DB02",
          PaymentStatus.PENDING,
          "100.00",
          List.of(event("EVT-1", ProviderStatus.APPROVED)),
          List.of(debit(1L, "40.00", "2026-10-13T09:00:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(List.of(debitPlanned("60.00")), decision.entriesToAdd());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
    }

    @Test
    @DisplayName("Un reverso parcial no se completa ni se compensa")
    void partialReversalIsNotCompleted() {
      PaymentEvidence evidence = approvedConfirmed(
          "PAY-RV01",
          "100.00",
          List.of(
              debit(1L, "100.00", "2026-10-13T09:00:00Z"),
              new LedgerEntry(2L, LedgerEntryType.REVERSAL, money("40.00"), 1L, Instant.parse("2026-10-13T09:01:00Z")),
              debit(3L, "100.00", "2026-10-13T09:05:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.MANUAL_REVIEW, decision.outcome());
      assertTrue(decision.entriesToAdd().isEmpty());
    }

    @Test
    @DisplayName("Evidencia contradictoria con débito existente tampoco se toca")
    void contradictoryEvidenceDoesNotReverseAnExistingDebit() {
      PaymentEvidence evidence = payment(
          "PAY-CQ99",
          PaymentStatus.CONFIRMED,
          "54000",
          List.of(
              event("EVT-1", ProviderStatus.APPROVED),
              event("EVT-2", ProviderStatus.DECLINED)),
          List.of(debit(1L, "54000", "2026-10-13T09:00:00Z")));

      ReconciliationDecision decision = policy.decide(evidence);

      assertEquals(ReconciliationOutcome.MANUAL_REVIEW, decision.outcome());
      assertEquals(PaymentStatus.CONFIRMED, decision.resultingStatus());
      assertTrue(decision.entriesToAdd().isEmpty());
    }

    @Test
    @DisplayName("Otro motivo de revisión sí deja una auditoría nueva")
    void aDifferentReviewReasonIsRecorded() {
      PaymentEvidence reviewed = new PaymentEvidence(
          "PAY-CQ22",
          PaymentStatus.PENDING,
          money("54000"),
          List.of(
              event("EVT-1", ProviderStatus.APPROVED),
              event("EVT-2", ProviderStatus.DECLINED)),
          List.of(),
          List.of(new AuditRecord(AuditAction.MANUAL_REVIEW, "Motivo anterior")));

      ReconciliationDecision decision = policy.decide(reviewed);

      assertEquals(review(CONTRADICTORY), decision.auditToRecord());
    }
  }

  private void assertSecondRunWritesNothing(PaymentEvidence original, ReconciliationOutcome expectedOutcome) {
    ReconciliationDecision first = policy.decide(original);
    ReconciliationDecision second = policy.decide(after(original, first));

    assertEquals(expectedOutcome, second.outcome());
    assertTrue(second.entriesToAdd().isEmpty());
    assertEquals(Optional.empty(), second.auditToRecord());
  }

  private PaymentEvidence after(PaymentEvidence evidence, ReconciliationDecision decision) {
    List<LedgerEntry> entries = new ArrayList<>(evidence.entries());
    long nextId = entries.stream().mapToLong(LedgerEntry::id).max().orElse(0L) + 1L;
    Instant recordedAt = Instant.parse("2026-10-13T09:43:08.512Z");
    for (PlannedEntry planned : decision.entriesToAdd()) {
      entries.add(new LedgerEntry(nextId++, planned.type(), planned.amount(), planned.relatedEntryId(), recordedAt));
    }
    List<AuditRecord> audits = new ArrayList<>(evidence.audits());
    decision.auditToRecord().ifPresent(audits::add);
    return new PaymentEvidence(
        evidence.externalRef(),
        decision.resultingStatus(),
        evidence.amount(),
        evidence.events(),
        entries,
        audits);
  }

  private PaymentEvidence payAx91() {
    return payment(
        "PAY-AX91",
        PaymentStatus.PENDING,
        "125000",
        List.of(event("EVT-A1", ProviderStatus.APPROVED)),
        List.of(debit(4101L, "125000", "2026-10-13T09:10:00Z")));
  }

  private PaymentEvidence payBx17() {
    return payment(
        "PAY-BX17",
        PaymentStatus.CONFIRMED,
        "89000",
        List.of(
            event("EVT-B1", ProviderStatus.APPROVED),
            event("EVT-B2", ProviderStatus.APPROVED)),
        List.of(
            debit(5002L, "89000", "2026-10-13T09:41:20Z"),
            debit(5003L, "89000", "2026-10-13T09:41:25.390Z")));
  }

  private PaymentEvidence payCq22() {
    return payment(
        "PAY-CQ22",
        PaymentStatus.PENDING,
        "54000",
        List.of(
            event("EVT-C1", ProviderStatus.APPROVED),
            event("EVT-C2", ProviderStatus.DECLINED)),
        List.of());
  }

  private PaymentEvidence payDk44() {
    return payment(
        "PAY-DK44",
        PaymentStatus.CONFIRMED,
        "31000",
        List.of(event("EVT-D1", ProviderStatus.APPROVED)),
        List.of(debit(4401L, "31000", "2026-10-13T08:00:00Z")));
  }

  private PaymentEvidence approvedConfirmed(String ref, String amount, List<LedgerEntry> entries) {
    return payment(
        ref,
        PaymentStatus.CONFIRMED,
        amount,
        List.of(event("EVT-1", ProviderStatus.APPROVED)),
        entries);
  }

  private PaymentEvidence payment(
      String ref,
      PaymentStatus status,
      String amount,
      List<ProviderEvent> events,
      List<LedgerEntry> entries) {
    return new PaymentEvidence(ref, status, money(amount), events, entries, List.of());
  }

  private ProviderEvent event(String id, ProviderStatus status) {
    return new ProviderEvent(id, status);
  }

  private LedgerEntry debit(long id, String amount, String createdAt) {
    return new LedgerEntry(id, LedgerEntryType.DEBIT, money(amount), null, Instant.parse(createdAt));
  }

  private PlannedEntry reversal(String amount, long relatedEntryId) {
    return new PlannedEntry(LedgerEntryType.REVERSAL, money(amount), relatedEntryId);
  }

  private PlannedEntry debitPlanned(String amount) {
    return new PlannedEntry(LedgerEntryType.DEBIT, money(amount), null);
  }

  private Optional<AuditRecord> applied(String reason) {
    return Optional.of(new AuditRecord(AuditAction.RECONCILIATION_APPLIED, reason));
  }

  private Optional<AuditRecord> review(String reason) {
    return Optional.of(new AuditRecord(AuditAction.MANUAL_REVIEW, reason));
  }

  private Optional<AuditRecord> noChange(String reason) {
    return Optional.of(new AuditRecord(AuditAction.RECONCILIATION_NO_CHANGE, reason));
  }

  private BigDecimal money(String amount) {
    return new BigDecimal(amount).setScale(2);
  }
}
