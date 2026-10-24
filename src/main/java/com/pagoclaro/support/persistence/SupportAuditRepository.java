package com.pagoclaro.support.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportAuditRepository extends JpaRepository<SupportAuditEntity, Long> {

  List<SupportAuditEntity> findByPaymentId(Long paymentId);
}
