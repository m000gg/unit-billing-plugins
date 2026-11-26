package com.m000gg.plugins.nbuexchangerate;

import com.m000gg.plugin.api.query.fx.ExchangeRate;
import com.m000gg.plugin.api.spi.fx.ExchangeRateProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link ExchangeRateProvider} backed by an in-memory cached NBU snapshot refreshed in the background.
 * {@link #findRate(String, String)} never performs network I/O and returns empty once cached data exceeds TTL.
 */
final class CachedNbuExchangeRateProvider implements ExchangeRateProvider {

    static final String SOURCE_ID = "nbu";
    private static final Logger log = LoggerFactory.getLogger(CachedNbuExchangeRateProvider.class);

    @FunctionalInterface
    interface RatesFetcher {
        Map<String, BigDecimal> fetch() throws Exception;
    }

    private final RatesFetcher fetcher;
    private final Duration refreshInterval;
    private final Duration cacheTtl;
    private final Clock clock;
    private final AtomicReference<NbuRatesSnapshot> cache = new AtomicReference<>();

    private volatile ScheduledExecutorService scheduler;

    CachedNbuExchangeRateProvider(NbuExchangeRateSettings settings, NbuRateHttpClient client) {
        this(
                () -> NbuResponseParser.parseUahPerUnit(client.fetchJson()),
                settings.refreshInterval(),
                settings.cacheTtl(),
                Clock.systemUTC()
        );
    }

    CachedNbuExchangeRateProvider(
            RatesFetcher fetcher,
            Duration refreshInterval,
            Duration cacheTtl,
            Clock clock
    ) {
        this.fetcher = Objects.requireNonNull(fetcher, "fetcher must not be null");
        this.refreshInterval = Objects.requireNonNull(refreshInterval, "refreshInterval must not be null");
        this.cacheTtl = Objects.requireNonNull(cacheTtl, "cacheTtl must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    void start() {
        ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "plugin-nbu-exchange-rate-refresh");
            thread.setDaemon(true);
            return thread;
        });
        this.scheduler = executor;
        executor.scheduleAtFixedRate(this::refreshSafely, 0L, refreshInterval.toMillis(), TimeUnit.MILLISECONDS);
    }

    void stop() {
        ScheduledExecutorService executor = this.scheduler;
        this.scheduler = null;
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(3, TimeUnit.SECONDS)) {
                log.warn("NBU refresh scheduler did not terminate in time");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while stopping NBU refresh scheduler", e);
        }
    }

    boolean refreshSafely() {
        try {
            Map<String, BigDecimal> parsed = fetcher.fetch();
            Instant now = clock.instant();
            cache.set(new NbuRatesSnapshot(parsed, now));
            log.debug("Refreshed NBU exchange rates ({} currencies)", parsed.size());
            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while refreshing NBU exchange rates");
            return false;
        } catch (Exception e) {
            log.warn("Failed to refresh NBU exchange rates; retaining existing cache if valid: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency) {
        NbuRatesSnapshot snapshot = cache.get();
        if (snapshot == null || isStale(snapshot)) {
            return Optional.empty();
        }
        return snapshot.convert(fromCurrency, toCurrency)
                .map(rate -> new ExchangeRate(rate, SOURCE_ID, snapshot.fetchedAt()));
    }

    private boolean isStale(NbuRatesSnapshot snapshot) {
        return clock.instant().isAfter(snapshot.fetchedAt().plus(cacheTtl));
    }
}
