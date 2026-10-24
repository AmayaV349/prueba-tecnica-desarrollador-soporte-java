package com.pagoclaro.support.api;

import com.pagoclaro.support.service.ReconciliationService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/support/payments")
public class ReconciliationController {

  private final ReconciliationService reconciliations;

  public ReconciliationController(ReconciliationService reconciliations) {
    this.reconciliations = reconciliations;
  }

  @PostMapping("/{externalRef}/reconciliation")
  public ReconciliationResponse reconcile(@PathVariable String externalRef) {
    return reconciliations.reconcile(externalRef);
  }
}
