package com.m000gg.plugins.nbuexchangerate;

import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.settings.PluginSettings;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * Validated runtime configuration for the NBU exchange rate plugin read from {@link PluginSettings}.
 */
public record NbuExchangeRateSettings(
        URI apiUri,
        Duration refreshInterval,
        Duration connectTimeout,
        Duration requestTimeout,
        Duration cacheTtl
) {

    public static final String DEFAULT_API_URL =
            "https://bank.gov.ua/NBU_Exchange/exchange_site?json";
    public static final long DEFAULT_REFRESH_INTERVAL_SECONDS = 3600L;
    public static final long DEFAULT_CONNECT_TIMEOUT_MS = 3000L;
    public static final long DEFAULT_REQUEST_TIMEOUT_MS = 5000L;
    public static final long DEFAULT_CACHE_TTL_SECONDS = 86400L;

    public NbuExchangeRateSettings {
        Objects.requireNonNull(apiUri, "apiUri must not be null");
        String scheme = apiUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new IllegalArgumentException("api-url must use http or https: " + apiUri);
        }
        requirePositive(refreshInterval, "refreshInterval");
        requirePositive(connectTimeout, "connectTimeout");
        requirePositive(requestTimeout, "requestTimeout");
        requirePositive(cacheTtl, "cacheTtl");
    }

    public static NbuExchangeRateSettings from(PluginContext context) {
        Objects.requireNonNull(context, "context must not be null");
        PluginSettings settings = Objects.requireNonNull(context.settings(), "context.settings() must not be null");

        String rawUrl = settings.get("api-url")
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .orElse(DEFAULT_API_URL);
        URI uri = URI.create(rawUrl);

        long refreshSeconds = parsePositiveLong(settings, "refresh-interval-seconds", DEFAULT_REFRESH_INTERVAL_SECONDS);
        long connectMs = parsePositiveLong(settings, "connect-timeout-ms", DEFAULT_CONNECT_TIMEOUT_MS);
        long requestMs = parsePositiveLong(settings, "request-timeout-ms", DEFAULT_REQUEST_TIMEOUT_MS);
        long ttlSeconds = parsePositiveLong(settings, "cache-ttl-seconds", DEFAULT_CACHE_TTL_SECONDS);

        return new NbuExchangeRateSettings(
                uri,
                Duration.ofSeconds(refreshSeconds),
                Duration.ofMillis(connectMs),
                Duration.ofMillis(requestMs),
                Duration.ofSeconds(ttlSeconds)
        );
    }

    private static long parsePositiveLong(PluginSettings settings, String key, long defaultValue) {
        return settings.get(key)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(raw -> {
                    long parsed;
                    try {
                        parsed = Long.parseLong(raw);
                    } catch (NumberFormatException e) {
                        throw new IllegalArgumentException("Invalid numeric setting '" + key + "': " + raw, e);
                    }
                    if (parsed <= 0) {
                        throw new IllegalArgumentException("Setting '" + key + "' must be positive: " + raw);
                    }
                    return parsed;
                })
                .orElse(defaultValue);
    }

    private static void requirePositive(Duration duration, String name) {
        Objects.requireNonNull(duration, name + " must not be null");
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }
}
