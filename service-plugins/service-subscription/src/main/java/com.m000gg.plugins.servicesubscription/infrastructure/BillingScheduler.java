package com.m000gg.plugins.servicesubscription.infrastructure;

import com.m000gg.plugin.api.query.settings.PluginSettings;
import com.m000gg.plugins.servicesubscription.application.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class BillingScheduler {

    private static final Logger log = LoggerFactory.getLogger(BillingScheduler.class);

    private static final long DEFAULT_INTERVAL_SECONDS = 60L;
    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5L;

    private final SubscriptionService subscriptionService;
    private final long intervalSeconds;
    private final int batchSize;
    private ScheduledExecutorService executor;

    public BillingScheduler(SubscriptionService subscriptionService, PluginSettings settings) {
        this.subscriptionService = Objects.requireNonNull(subscriptionService, "subscriptionService");
        Objects.requireNonNull(settings, "settings");
        this.intervalSeconds = parsePositiveLong(
                settings.get("billing-interval-seconds").orElse(null),
                DEFAULT_INTERVAL_SECONDS,
                "billing-interval-seconds"
        );
        this.batchSize = parsePositiveInt(
                settings.get("billing-batch-size").orElse(null),
                DEFAULT_BATCH_SIZE,
                "billing-batch-size"
        );
    }

    public synchronized void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "service-subscription-billing");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(
                this::runTick,
                intervalSeconds,
                intervalSeconds,
                TimeUnit.SECONDS
        );
        log.info("Billing scheduler started (interval={}s, batchSize={})", intervalSeconds, batchSize);
    }

    public synchronized void stop() {
        if (executor == null) {
            return;
        }
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        } finally {
            executor = null;
        }
        log.info("Billing scheduler stopped");
    }

    void runTick() {
        try {
            int processed = subscriptionService.processDueSubscriptions(Instant.now(), batchSize);
            if (processed > 0) {
                log.info("Billing tick processed {} subscription(s)", processed);
            }
        } catch (Exception e) {
            log.error("Billing tick failed", e);
        }
    }

    private static long parsePositiveLong(String raw, long defaultValue, String key) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        long value = Long.parseLong(raw.trim());
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be >= 1, got: " + value);
        }
        return value;
    }

    private static int parsePositiveInt(String raw, int defaultValue, String key) {
        if (raw == null || raw.isBlank()) {
            return defaultValue;
        }
        int value = Integer.parseInt(raw.trim());
        if (value < 1) {
            throw new IllegalArgumentException(key + " must be >= 1, got: " + value);
        }
        return value;
    }
}
