package com.m000gg.plugins.nbuexchangerate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable snapshot of official NBU rates expressed as UAH per 1 unit of each currency.
 */
final class NbuRatesSnapshot {

    static final int RATE_SCALE = 6;
    static final String BASE_FEED_CURRENCY = "UAH";

    private final Map<String, BigDecimal> uahPerUnit;
    private final Instant fetchedAt;

    NbuRatesSnapshot(Map<String, BigDecimal> parsedUahPerUnit, Instant fetchedAt) {
        Objects.requireNonNull(parsedUahPerUnit, "parsedUahPerUnit must not be null");
        this.fetchedAt = Objects.requireNonNull(fetchedAt, "fetchedAt must not be null");

        Map<String, BigDecimal> copy = new HashMap<>();
        parsedUahPerUnit.forEach((code, rate) -> {
            if (code != null && !code.isBlank() && rate != null && rate.signum() > 0) {
                copy.put(code.trim().toUpperCase(Locale.ROOT), rate);
            }
        });
        copy.put(BASE_FEED_CURRENCY, BigDecimal.ONE);
        this.uahPerUnit = Collections.unmodifiableMap(copy);
    }

    Instant fetchedAt() {
        return fetchedAt;
    }

    Optional<BigDecimal> convert(String fromCurrency, String toCurrency) {
        if (fromCurrency == null || fromCurrency.isBlank() || toCurrency == null || toCurrency.isBlank()) {
            return Optional.empty();
        }
        String from = fromCurrency.trim().toUpperCase(Locale.ROOT);
        String to = toCurrency.trim().toUpperCase(Locale.ROOT);

        BigDecimal fromRate = uahPerUnit.get(from);
        BigDecimal toRate = uahPerUnit.get(to);
        if (fromRate == null || toRate == null) {
            return Optional.empty();
        }
        if (from.equals(to)) {
            return Optional.of(BigDecimal.ONE);
        }
        if (BASE_FEED_CURRENCY.equals(to)) {
            return Optional.of(fromRate);
        }
        BigDecimal converted = fromRate.divide(toRate, RATE_SCALE, RoundingMode.HALF_UP);
        return converted.signum() > 0 ? Optional.of(converted) : Optional.empty();
    }
}
