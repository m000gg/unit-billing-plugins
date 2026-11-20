package com.m000gg.plugins.nbuexchangerate;

import com.m000gg.plugin.api.query.fx.ExchangeRate;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CachedNbuExchangeRateProviderTest {

    @Test
    void servesFromCacheWithoutBlockingOnFetchAndEnforcesStaleTtlAndOutageFallback() {
        AtomicReference<Instant> currentTime = new AtomicReference<>(Instant.parse("2026-11-19T10:00:00Z"));
        Clock mutableClock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return currentTime.get();
            }
        };

        AtomicInteger fetchCount = new AtomicInteger();
        AtomicBoolean failNextFetch = new AtomicBoolean(false);
        CachedNbuExchangeRateProvider provider = new CachedNbuExchangeRateProvider(
                () -> {
                    fetchCount.incrementAndGet();
                    if (failNextFetch.get()) {
                        throw new IOException("NBU unreachable");
                    }
                    return Map.of(
                            "USD", new BigDecimal("41.5000"),
                            "EUR", new BigDecimal("44.2000")
                    );
                },
                Duration.ofMinutes(15),
                Duration.ofHours(1),
                mutableClock
        );

        assertThat(provider.findRate("USD", "EUR")).isEmpty();
        assertThat(fetchCount.get()).isZero();

        assertThat(provider.refreshSafely()).isTrue();
        assertThat(fetchCount.get()).isEqualTo(1);

        Optional<ExchangeRate> rate = provider.findRate("usd", "uah");
        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("41.5000");
        assertThat(rate.get().source()).isEqualTo(CachedNbuExchangeRateProvider.SOURCE_ID);
        assertThat(rate.get().fetchedAt()).isEqualTo(Instant.parse("2026-11-19T10:00:00Z"));
        assertThat(fetchCount.get()).isEqualTo(1);

        // Failed refresh 30m later must preserve existing valid snapshot within 1h TTL.
        currentTime.set(Instant.parse("2026-11-19T10:30:00Z"));
        failNextFetch.set(true);
        assertThat(provider.refreshSafely()).isFalse();
        assertThat(provider.findRate("USD", "EUR")).isPresent();

        // Past 1h TTL without a successful refresh -> return empty instead of stale rate.
        currentTime.set(Instant.parse("2026-11-19T11:00:01Z"));
        assertThat(provider.findRate("USD", "EUR")).isEmpty();

        // Recovery after outage restores availability.
        failNextFetch.set(false);
        assertThat(provider.refreshSafely()).isTrue();
        assertThat(provider.findRate("USD", "EUR")).isPresent();
    }
}
