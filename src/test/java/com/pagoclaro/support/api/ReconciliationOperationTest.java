package com.pagoclaro.support.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.pagoclaro.support.conciliacion.LedgerEntryType;
import com.pagoclaro.support.conciliacion.PaymentStatus;
import com.pagoclaro.support.conciliacion.ProviderStatus;
import com.pagoclaro.support.persistence.LedgerEntryEntity;
import com.pagoclaro.support.persistence.LedgerEntryRepository;
import com.pagoclaro.support.persistence.PaymentEntity;
import com.pagoclaro.support.persistence.PaymentRepository;
import com.pagoclaro.support.persistence.ProviderEventEntity;
import com.pagoclaro.support.persistence.ProviderEventRepository;
import com.pagoclaro.support.persistence.SupportAuditRepository;
import com.pagoclaro.support.service.ReconciliationService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ReconciliationOperationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private PaymentRepository payments;

  @Autowired
  private LedgerEntryRepository ledger;

  @Autowired
  private SupportAuditRepository audits;

  @Autowired
  private ProviderEventRepository events;

  @Autowired
  private ReconciliationService reconciliations;

  @Autowired
  private PlatformTransactionManager transactionManager;

  @Test
  @DisplayName("PAY-BX17 reversa el débito 5003 una sola vez")
  void payBx17DoesNotRepeatTheReversal() throws Exception {
    mockMvc.perform(post("/api/support/payments/{externalRef}/reconciliation", "PAY-BX17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RECONCILED"))
        .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.netDebit").value(89000.0))
        .andExpect(jsonPath("$.reason").value(
            "Se registró un reverso de 89000 sobre el débito duplicado más reciente. El débito neto queda igual al monto."));

    mockMvc.perform(post("/api/support/payments/{externalRef}/reconciliation", "PAY-BX17"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("ALREADY_CONSISTENT"))
        .andExpect(jsonPath("$.netDebit").value(89000.0));

    PaymentEntity payment = payments.findByExternalRef("PAY-BX17").orElseThrow();
    List<LedgerEntryEntity> entries = ledger.findByPaymentId(payment.getId());
    assertEquals(3, entries.size());
    assertEquals(1, audits.findByPaymentId(payment.getId()).size());

    LedgerEntryEntity original = entries.stream()
        .filter(entry -> entry.getId().equals(5003L))
        .findFirst()
        .orElseThrow();
    assertEquals(LedgerEntryType.DEBIT, original.getEntryType());
    assertNull(original.getRelatedEntryId());
    assertEquals(0, original.getAmount().compareTo(new BigDecimal("89000.00")));

    LedgerEntryEntity reversal = entries.stream()
        .filter(entry -> entry.getEntryType() == LedgerEntryType.REVERSAL)
        .findFirst()
        .orElseThrow();
    assertEquals(5003L, reversal.getRelatedEntryId());
    assertEquals(0, reversal.getAmount().compareTo(new BigDecimal("89000.00")));
  }

  @Test
  @DisplayName("PAY-AX91 queda CONFIRMED sin un débito nuevo")
  void payAx91ConfirmsWithoutANewDebit() throws Exception {
    mockMvc.perform(post("/api/support/payments/{externalRef}/reconciliation", "PAY-AX91"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("RECONCILED"))
        .andExpect(jsonPath("$.paymentStatus").value("CONFIRMED"))
        .andExpect(jsonPath("$.netDebit").value(125000.0));

    PaymentEntity payment = payments.findByExternalRef("PAY-AX91").orElseThrow();
    assertEquals(PaymentStatus.CONFIRMED, payment.getStatus());
    List<LedgerEntryEntity> entries = ledger.findByPaymentId(payment.getId());
    assertEquals(1, entries.size());
    assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
  }

  @Test
  @DisplayName("PAY-CQ22 no se toca y la revisión no se repite")
  void payCq22StaysUntouched() throws Exception {
    mockMvc.perform(post("/api/support/payments/{externalRef}/reconciliation", "PAY-CQ22"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("MANUAL_REVIEW"))
        .andExpect(jsonPath("$.paymentStatus").value("PENDING"))
        .andExpect(jsonPath("$.netDebit").value(0));

    mockMvc.perform(post("/api/support/payments/{externalRef}/reconciliation", "PAY-CQ22"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.outcome").value("MANUAL_REVIEW"));

    PaymentEntity payment = payments.findByExternalRef("PAY-CQ22").orElseThrow();
    assertEquals(PaymentStatus.PENDING, payment.getStatus());
    assertTrue(ledger.findByPaymentId(payment.getId()).isEmpty());
    assertEquals(1, audits.findByPaymentId(payment.getId()).size());
  }

  @Test
  @DisplayName("Una referencia desconocida responde 404")
  void unknownReferenceReturnsNotFound() throws Exception {
    mockMvc.perform(post("/api/support/payments/{externalRef}/reconciliation", "PAY-ZZ99"))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.outcome").value("NOT_FOUND"))
        .andExpect(jsonPath("$.reason").value("No existe un pago con esa referencia."));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  @DisplayName("Dos conciliaciones a la vez dejan un solo reverso")
  void twoCloseCallsLeaveOneReversal() throws Exception {
    String externalRef = "PAY-RACE";
    TransactionTemplate transactions = new TransactionTemplate(transactionManager);
    Long paymentId = transactions.execute(status -> {
      PaymentEntity payment = payments.save(new PaymentEntity(
          externalRef,
          PaymentStatus.CONFIRMED,
          new BigDecimal("100.00"),
          Instant.parse("2026-10-13T10:00:00Z")));
      events.save(new ProviderEventEntity(
          payment.getId(),
          "EVT-RACE",
          ProviderStatus.APPROVED,
          Instant.parse("2026-10-13T10:00:01Z")));
      ledger.save(new LedgerEntryEntity(
          payment.getId(),
          LedgerEntryType.DEBIT,
          new BigDecimal("100.00"),
          null,
          Instant.parse("2026-10-13T10:00:02Z")));
      ledger.save(new LedgerEntryEntity(
          payment.getId(),
          LedgerEntryType.DEBIT,
          new BigDecimal("100.00"),
          null,
          Instant.parse("2026-10-13T10:00:03Z")));
      return payment.getId();
    });

    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch ready = new CountDownLatch(2);
    CountDownLatch start = new CountDownLatch(1);
    try {
      Callable<ReconciliationResponse> call = () -> {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
          throw new IllegalStateException("No arrancó la conciliación simultánea");
        }
        return reconciliations.reconcile(externalRef);
      };
      Future<ReconciliationResponse> first = executor.submit(call);
      Future<ReconciliationResponse> second = executor.submit(call);
      assertTrue(ready.await(5, TimeUnit.SECONDS));
      start.countDown();
      List<String> outcomes = List.of(
          first.get(10, TimeUnit.SECONDS).outcome(),
          second.get(10, TimeUnit.SECONDS).outcome());
      assertTrue(outcomes.contains("RECONCILED"));
      assertTrue(outcomes.contains("ALREADY_CONSISTENT"));
      long reversals = ledger.findByPaymentId(paymentId).stream()
          .filter(entry -> entry.getEntryType() == LedgerEntryType.REVERSAL)
          .count();
      assertEquals(1, reversals);
    } finally {
      executor.shutdownNow();
    }
  }
}
