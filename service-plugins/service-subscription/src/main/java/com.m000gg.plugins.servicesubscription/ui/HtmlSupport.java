package com.m000gg.plugins.servicesubscription.ui;

import com.m000gg.plugin.api.ui.PluginPageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
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

    static UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing identifier");
        }
        return UUID.fromString(raw.trim());
    }
}
