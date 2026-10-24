package com.pagoclaro.support.persistence;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntryEntity, Long> {

  List<LedgerEntryEntity> findByPaymentId(Long paymentId);
}
