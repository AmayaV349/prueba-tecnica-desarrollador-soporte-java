package com.pagoclaro.support.api;

import com.pagoclaro.support.service.PaymentNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(PaymentNotFoundException.class)
  public ResponseEntity<ReconciliationResponse> notFound(PaymentNotFoundException exception) {
    log.info("Pago no encontrado externalRef={}", exception.externalRef());
    return ResponseEntity.status(404).body(new ReconciliationResponse(
        exception.externalRef(),
        "NOT_FOUND",
        null,
        null,
        "No existe un pago con esa referencia."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<ReconciliationResponse> unexpected(Exception exception) {
    log.error("Fallo al conciliar", exception);
    return ResponseEntity.status(500).body(new ReconciliationResponse(
        null,
        "ERROR",
        null,
        null,
        "No fue posible conciliar el pago. Reintente o escale el caso."));
  }
}
