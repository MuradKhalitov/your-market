package ru.murad.yourmarket.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Low-cardinality operational metrics. Never add database IDs, payloads or charge IDs as tags.
 */
@Component
public class OperationalMetrics {
    private final MeterRegistry registry;
    private final AtomicLong lastSuccessfulUpdateEpochSeconds = new AtomicLong();

    public OperationalMetrics(MeterRegistry registry) {
        this.registry = registry;
        Gauge.builder("yourmarket.telegram.updates.last.success.timestamp",
                        lastSuccessfulUpdateEpochSeconds, AtomicLong::doubleValue)
                .description("Unix timestamp of the most recently completed supported Telegram update")
                .register(registry);
    }

    public void invoiceClaim(String result) { count("payment.invoice.claims", "result", result); }
    public void successfulPayment(boolean duplicate) { count("payment.successful", "result", duplicate ? "duplicate" : "processed"); }
    public void refund(String result) { count("payment.refund", "result", result); }
    public void publication(String result) { count("publication.operations", "result", result); }
    public void moderation(String result) { count("moderation.operations", "result", result); }
    public void rateLimitRejected() { count("telegram.rate_limit.rejections", "result", "rejected"); }
    public void updateFailure() { count("telegram.updates.failures", "result", "failure"); }
    public void scheduler(String scheduler, String result) { count("scheduler.runs", "scheduler", scheduler, "result", result); }
    public void telegramError(String errorClass) { count("telegram.api.errors", "error_class", errorClass); }

    public void successfulUpdate() { lastSuccessfulUpdateEpochSeconds.set(Instant.now().getEpochSecond()); }

    private void count(String suffix, String... tags) {
        Counter.builder("yourmarket." + suffix).tags(tags).register(registry).increment();
    }
}
