package com.m000gg.plugins.nbuexchangerate;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Lightweight dependency-free parser for NBU JSON rate feeds
 * (such as {@code https://bank.gov.ua/NBU_Exchange/exchange_site?json}).
 */
final class NbuResponseParser {

    private static final int NORMALIZATION_SCALE = 10;

    private final String json;
    private int pos;

    private NbuResponseParser(String json) {
        this.json = json;
        this.pos = 0;
    }

    static Map<String, BigDecimal> parseUahPerUnit(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("NBU response body must not be blank");
        }
        return new NbuResponseParser(json).parseRootArray();
    }

    private Map<String, BigDecimal> parseRootArray() {
        skipWhitespace();
        expect('[');
        Map<String, BigDecimal> rates = new HashMap<>();

        skipWhitespace();
        if (peek(']')) {
            pos++;
            throw new IllegalArgumentException("NBU response contained no exchange rates");
        }

        while (true) {
            skipWhitespace();
            expect('{');
            parseEntry(rates);
            skipWhitespace();
            if (peek(',')) {
                pos++;
            } else {
                expect(']');
                break;
            }
        }

        skipWhitespace();
        if (pos != json.length()) {
            throw new IllegalArgumentException("Unexpected trailing content in NBU response");
        }
        if (rates.isEmpty()) {
            throw new IllegalArgumentException("NBU response contained no valid positive rates");
        }
        return rates;
    }

    private void parseEntry(Map<String, BigDecimal> rates) {
        String code = null;
        BigDecimal ratePerUnit = null;
        BigDecimal rate = null;
        BigDecimal units = null;

        skipWhitespace();
        if (peek('}')) {
            pos++;
            return;
        }

        while (true) {
            skipWhitespace();
            String key = readString();
            skipWhitespace();
            expect(':');
            skipWhitespace();

            switch (key) {
                case "cc" -> code = readNullableString();
                case "rate_per_unit" -> ratePerUnit = readNullableNumber();
                case "rate" -> rate = readNullableNumber();
                case "units" -> units = readNullableNumber();
                default -> skipValue();
            }

            skipWhitespace();
            if (peek(',')) {
                pos++;
            } else {
                expect('}');
                break;
            }
        }

        if (code == null || code.isBlank()) {
            return;
        }
        BigDecimal resolved = resolvePerUnitRate(ratePerUnit, rate, units);
        if (resolved != null && resolved.signum() > 0) {
            rates.put(code.trim().toUpperCase(Locale.ROOT), resolved);
        }
    }

    private static BigDecimal resolvePerUnitRate(BigDecimal ratePerUnit, BigDecimal rate, BigDecimal units) {
        if (ratePerUnit != null && ratePerUnit.signum() > 0) {
            return ratePerUnit;
        }
        if (rate == null || rate.signum() <= 0) {
            return null;
        }
        if (units != null && units.signum() > 0 && units.compareTo(BigDecimal.ONE) != 0) {
            return rate.divide(units, NORMALIZATION_SCALE, RoundingMode.HALF_UP);
        }
        return rate;
    }

    private String readNullableString() {
        if (tryConsumeLiteral("null")) {
            return null;
        }
        return readString();
    }

    private BigDecimal readNullableNumber() {
        if (tryConsumeLiteral("null")) {
            return null;
        }
        int start = pos;
        if (peek('-')) {
            pos++;
        }
        boolean hasDigits = false;
        while (pos < json.length()) {
            char c = json.charAt(pos);
            if ((c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                if (c >= '0' && c <= '9') {
                    hasDigits = true;
                }
                pos++;
            } else {
                break;
            }
        }
        if (!hasDigits) {
            throw new IllegalArgumentException("Expected JSON number at position " + start);
        }
        try {
            return new BigDecimal(json.substring(start, pos));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid JSON number at position " + start, e);
        }
    }

    private String readString() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (pos < json.length()) {
            char c = json.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= json.length()) {
                    throw new IllegalArgumentException("Unterminated escape sequence in JSON string");
                }
                char esc = json.charAt(pos++);
                switch (esc) {
                    case '"', '\\', '/' -> sb.append(esc);
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > json.length()) {
                            throw new IllegalArgumentException("Invalid unicode escape in JSON string");
                        }
                        int codePoint = Integer.parseInt(json.substring(pos, pos + 4), 16);
                        sb.append((char) codePoint);
                        pos += 4;
                    }
                    default -> throw new IllegalArgumentException("Unsupported JSON escape: \\" + esc);
                }
            } else {
                sb.append(c);
            }
        }
        throw new IllegalArgumentException("Unterminated JSON string");
    }

    private void skipValue() {
        skipWhitespace();
        if (pos >= json.length()) {
            throw new IllegalArgumentException("Unexpected end of JSON");
        }
        char c = json.charAt(pos);
        switch (c) {
            case '"' -> readString();
            case '{' -> skipBalanced('{', '}');
            case '[' -> skipBalanced('[', ']');
            case 't' -> expectLiteral("true");
            case 'f' -> expectLiteral("false");
            case 'n' -> expectLiteral("null");
            default -> readNullableNumber();
        }
    }

    private void skipBalanced(char open, char close) {
        expect(open);
        int depth = 1;
        while (pos < json.length() && depth > 0) {
            char c = json.charAt(pos);
            if (c == '"') {
                readString();
            } else if (c == open) {
                depth++;
                pos++;
            } else if (c == close) {
                depth--;
                pos++;
            } else {
                pos++;
            }
        }
        if (depth != 0) {
            throw new IllegalArgumentException("Unbalanced JSON container starting with " + open);
        }
    }

    private void skipWhitespace() {
        while (pos < json.length() && Character.isWhitespace(json.charAt(pos))) {
            pos++;
        }
    }

    private boolean peek(char expected) {
        return pos < json.length() && json.charAt(pos) == expected;
    }

    private void expect(char expected) {
        if (pos >= json.length() || json.charAt(pos) != expected) {
            throw new IllegalArgumentException("Expected '" + expected + "' at position " + pos);
        }
        pos++;
    }

    private boolean tryConsumeLiteral(String literal) {
        if (json.startsWith(literal, pos)) {
            pos += literal.length();
            return true;
        }
        return false;
    }

    private void expectLiteral(String literal) {
        if (!tryConsumeLiteral(literal)) {
            throw new IllegalArgumentException("Expected '" + literal + "' at position " + pos);
        }
    }
}
