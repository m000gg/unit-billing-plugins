package com.m000gg.plugins.servicesubscription.ui;

import com.m000gg.plugin.api.query.fx.ExchangeRate;
import com.m000gg.plugin.api.query.fx.ExchangeRateDirectory;
import com.m000gg.plugin.api.ui.PluginPageRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

final class HtmlSupport {

    private static final DateTimeFormatter UTC_DATE_TIME =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    private HtmlSupport() {
    }

    //XSS defence
    static String escape(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '&' -> out.append("&amp;");
                case '<' -> out.append("&lt;");
                case '>' -> out.append("&gt;");
                case '"' -> out.append("&quot;");
                case '\'' -> out.append("&#39;");
                default -> out.append(c);
            }
        }
        return out.toString();
    }

    //CSRF
    static String csrfInput(PluginPageRequest request) {
        return "<input type=\"hidden\" name=\""
                + escape(request.csrfParameterName())
                + "\" value=\""
                + escape(request.csrfToken())
                + "\">";
    }

    static String formatInstant(Instant instant) {
        return instant == null ? "—" : UTC_DATE_TIME.format(instant);
    }

    static String formatPrice(BigDecimal price) {
        return price == null ? "—" : price.stripTrailingZeros().toPlainString();
    }

    static String formatMoney(BigDecimal amount, String currency) {
        if (amount == null) {
            return "—";
        }
        String formatted = formatPrice(amount);
        String code = normalizeCurrency(currency);
        return code == null ? formatted : formatted + " " + code;
    }

    static String formatRate(BigDecimal rate) {
        if (rate == null) {
            return "—";
        }
        return rate.setScale(4, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    static String normalizeCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            return null;
        }
        return currency.trim().toUpperCase(Locale.ROOT);
    }

    static String resolveBaseCurrency(ExchangeRateDirectory exchangeRates) {
        if (exchangeRates == null) {
            return null;
        }
        try {
            return normalizeCurrency(exchangeRates.baseCurrency());
        } catch (IllegalStateException ex) {
            return null;
        }
    }

    static Optional<ExchangeRate> safeFindRate(ExchangeRateDirectory exchangeRates,
                                               String fromCurrency,
                                               String toCurrency) {
        String from = normalizeCurrency(fromCurrency);
        String to = normalizeCurrency(toCurrency);
        if (exchangeRates == null || from == null || to == null) {
            return Optional.empty();
        }
        try {
            return exchangeRates.findRate(from, to)
                    .filter(q -> q.rate() != null && q.rate().compareTo(BigDecimal.ZERO) > 0);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    static String formatPriceForUser(BigDecimal basePrice,
                                       String baseCurrency,
                                       ExchangeRate quote,
                                       String userCurrency) {
        if (basePrice == null) {
            return "—";
        }
        String base = normalizeCurrency(baseCurrency);
        String target = normalizeCurrency(userCurrency);
        if (quote != null && quote.rate() != null && quote.rate().compareTo(BigDecimal.ZERO) > 0 && target != null) {
            if (base != null && base.equals(target)) {
                return formatMoney(basePrice, base);
            }
            BigDecimal converted = basePrice.multiply(quote.rate()).setScale(2, RoundingMode.HALF_UP);
            return formatMoney(converted, target);
        }
        return formatMoney(basePrice, base);
    }

    static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing identifier");
        }
        return UUID.fromString(raw.trim());
    }
}
