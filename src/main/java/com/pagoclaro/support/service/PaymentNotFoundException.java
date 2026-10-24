package com.pagoclaro.support.service;

public class PaymentNotFoundException extends RuntimeException {

  private final String externalRef;

  public PaymentNotFoundException(String externalRef) {
    super("No existe un pago con esa referencia");
    this.externalRef = externalRef;
  }

  public String externalRef() {
    return externalRef;
  }
}
