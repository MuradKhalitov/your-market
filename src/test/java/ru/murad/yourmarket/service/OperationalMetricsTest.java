package ru.murad.yourmarket.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OperationalMetricsTest {
    @Test
    void metricTagsRemainLowCardinality() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        OperationalMetrics metrics = new OperationalMetrics(registry);
        metrics.invoiceClaim("CLAIMED");
        metrics.telegramError("429");
        metrics.successfulUpdate();

        assertTrue(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains(UUID.randomUUID().toString())));
        assertFalse(registry.getMeters().stream().flatMap(meter -> meter.getId().getTags().stream())
                .anyMatch(tag -> tag.getKey().matches(".*(payment|advertisement|user).*id.*")));
    }
}
