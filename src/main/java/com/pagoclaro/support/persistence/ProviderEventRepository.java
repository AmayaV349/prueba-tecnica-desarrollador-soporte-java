package com.pagoclaro.support.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProviderEventRepository extends JpaRepository<ProviderEventEntity, Long> {

  List<ProviderEventEntity> findByPaymentId(Long paymentId);
}
