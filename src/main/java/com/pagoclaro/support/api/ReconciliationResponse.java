package com.pagoclaro.support.api;

import java.math.BigDecimal;

public record ReconciliationResponse(
    String externalRef,
    String outcome,
    String paymentStatus,
    BigDecimal netDebit,
    String reason) {}
