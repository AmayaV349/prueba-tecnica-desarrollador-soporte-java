package com.pagoclaro.support.conciliacion;

public record ProviderEvent(String providerEventId, ProviderStatus status) {

  public ProviderEvent {
    if (providerEventId == null || providerEventId.isBlank() || status == null) {
      throw new IllegalArgumentException("El evento del proveedor está incompleto");
    }
  }
}
