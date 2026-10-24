package com.pagoclaro.support;

import com.pagoclaro.support.conciliacion.ReconciliationPolicy;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ReconciliationConfig {

  @Bean
  ReconciliationPolicy reconciliationPolicy() {
    return new ReconciliationPolicy();
  }

  @Bean
  Clock clock() {
    return Clock.systemUTC();
  }
}
