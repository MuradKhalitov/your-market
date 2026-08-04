package ru.murad.yourmarket.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j @Component @RequiredArgsConstructor
class PaymentsConfigurationLog {
    private final PaymentsProperties payments;
    @PostConstruct void logConfiguration() { log.info("payments.enabled={}", payments.isEnabled()); }
}
