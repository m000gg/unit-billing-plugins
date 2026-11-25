package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.event.BalanceChangeDirection;
import com.m000gg.plugin.api.event.BalanceChangeType;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.ResourceBundle;

public class BalanceNotificationMessageFormatter {

    private static final DateTimeFormatter TRANSACTION_TIME_FORMAT =
            DateTimeFormatter.ofPattern("dd MMM uuuu, HH:mm 'UTC'", Locale.ENGLISH)
                    .withZone(ZoneOffset.UTC);

    public BalanceNotificationEmailMessage formNewEmailMessage(
            String to,
            BalanceChangeType changeType,
            BalanceChangeDirection direction,
            BigDecimal balanceAfterTransaction,
            BigDecimal amountInUserCurrency,
            String description,
            String userCurrency,
            Instant transactionTime,
            String userPreferredLang
    ) {
        Locale locale = resolveLocale(userPreferredLang);
        ResourceBundle messages = ResourceBundle.getBundle("i18n.email.messages", locale);

        String operationKey = changeType.name().toLowerCase(Locale.ROOT);
        String directionKey = direction.name().toLowerCase(Locale.ROOT);

        String operation = messages.getString("operation." + operationKey);
        String directionLabel = messages.getString("direction." + directionKey);

        String descriptionText = description == null || description.isBlank()
                ? messages.getString("description.not-provided")
                : description.trim();

        String transactionTimeText = TRANSACTION_TIME_FORMAT
                .withLocale(locale)
                .format(transactionTime);

        String subject = messages.getString("subject").formatted(operation);

        String body = loadBodyTemplate(locale.getLanguage())
                .strip()
                .formatted(
                        operation,
                        directionLabel,
                        descriptionText,
                        amountInUserCurrency.toPlainString(),
                        userCurrency,
                        balanceAfterTransaction.toPlainString(),
                        userCurrency,
                        transactionTimeText
                );

        return new BalanceNotificationEmailMessage(subject, body, to);
    }

    private Locale resolveLocale(String preferredLanguage) {
        String language = preferredLanguage == null
                ? ""
                : Locale.forLanguageTag(preferredLanguage.trim()).getLanguage();

        return switch (language) {
            case "ru" -> Locale.forLanguageTag("ru");
            case "uk" -> Locale.forLanguageTag("uk");
            case "fr" -> Locale.FRENCH;
            case "de" -> Locale.GERMAN;
            default -> Locale.ENGLISH;
        };
    }

    private String loadBodyTemplate(String language) {
        String resourcePath = "/email-templates/balance-change/" + language + ".txt";

        try (InputStream input = getClass().getResourceAsStream(resourcePath)) {
            if (input != null) {
                return new String(input.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read email template: " + resourcePath, e);
        }

        if (!"en".equals(language)) {
            return loadBodyTemplate("en");
        }

        throw new IllegalStateException("English email template is missing");
    }
}