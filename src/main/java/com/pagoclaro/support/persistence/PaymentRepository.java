package com.pagoclaro.support.persistence;

import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<PaymentEntity, Long> {

  Optional<PaymentEntity> findByExternalRef(String externalRef);

  /** Bloquea la fila hasta que termina la transacción. La segunda conciliación espera y relee. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @QueryHints(@QueryHint(name = "jakarta.persistence.lock.timeout", value = "5000"))
  @Query("select payment from PaymentEntity payment where payment.externalRef = :externalRef")
  Optional<PaymentEntity> findByExternalRefForUpdate(@Param("externalRef") String externalRef);
}
