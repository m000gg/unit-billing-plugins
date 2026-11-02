package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.event.BalanceChangeDirection;
import com.m000gg.plugin.api.event.BalanceChangeType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;

import java.math.BigDecimal;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class BalanceNotificationMessageFormatterTest {

    private final BalanceNotificationMessageFormatter formatter =
            new BalanceNotificationMessageFormatter();

    @Test
    void shouldFormatTopUpNotification() {
        BalanceNotificationEmailMessage message =
                formatter.formNewEmailMessage(
                        "user@example.com",
                        BalanceChangeType.TOP_UP,
                        BalanceChangeDirection.INCREASE,
                        new BigDecimal("150.00"),
                        new BigDecimal("50.00"),
                        "Monthly deposit",
                        "EUR",
                        Instant.parse("2026-10-15T09:30:00Z"),
                        "en"
                );

        assertEquals("user@example.com", message.to());
        assertEquals(
                "Your balance has been updated - Top-up",
                message.subject()
        );

        assertAll(
                () -> assertTrue(message.body().contains("Top-up")),
                () -> assertTrue(message.body().contains("Increase")),
                () -> assertTrue(message.body().contains("50.00 EUR")),
                () -> assertTrue(message.body().contains("150.00 EUR")),
                () -> assertTrue(message.body().contains("Monthly deposit")),
                () -> assertTrue(message.body().contains("2026"))
        );
    }

    @Test
    void shouldFormatChargeNotification() {
        BalanceNotificationEmailMessage message =
                formatter.formNewEmailMessage(
                        "user@example.com",
                        BalanceChangeType.CHARGE,
                        BalanceChangeDirection.DECREASE,
                        new BigDecimal("90.00"),
                        new BigDecimal("10.00"),
                        "Service charge",
                        "EUR",
                        Instant.parse("2026-10-15T09:30:00Z"),
                        "en"
                );

        assertEquals(
                "Your balance has been updated - Charge",
                message.subject()
        );

        assertAll(
                () -> assertTrue(message.body().contains("Charge")),
                () -> assertTrue(message.body().contains("Decrease")),
                () -> assertTrue(message.body().contains("Service charge")),
                () -> assertTrue(message.body().contains("10.00 EUR")),
                () -> assertTrue(message.body().contains("90.00 EUR"))
        );
    }

    @ParameterizedTest
    @MethodSource("localizedMessageCases")
    void shouldUseLocalizedMessages(
            String language,
            String expectedSubject,
            String expectedOperation,
            String expectedDirection
    ) {
        BalanceNotificationEmailMessage message =
                formatter.formNewEmailMessage(
                        "user@example.com",
                        BalanceChangeType.TOP_UP,
                        BalanceChangeDirection.INCREASE,
                        new BigDecimal("150.00"),
                        new BigDecimal("50.00"),
                        "Monthly deposit",
                        "EUR",
                        Instant.parse("2026-10-15T09:30:00Z"),
                        language
                );

        assertEquals(expectedSubject, message.subject());

        assertAll(
                () -> assertTrue(message.body().contains(expectedOperation)),
                () -> assertTrue(message.body().contains(expectedDirection)),
                () -> assertTrue(message.body().contains("Monthly deposit"))
        );
    }

    private static Stream<Arguments> localizedMessageCases() {
        return Stream.of(
                Arguments.of(
                        "de",
                        "Ihr Guthaben wurde aktualisiert - Aufladung",
                        "Aufladung",
                        "Gutschrift"
                ),
                Arguments.of(
                        "uk",
                        "Баланс оновлено - Поповнення",
                        "Поповнення",
                        "Збільшення"
                ),
                Arguments.of(
                        "ru",
                        "Баланс обновлён - Пополнение",
                        "Пополнение",
                        "Увеличение"
                ),
                Arguments.of(
                        "fr",
                        "Votre solde a été mis à jour - Approvisionnement",
                        "Approvisionnement",
                        "Crédit"
                ),
                Arguments.of(
                        (String) null,
                        "Your balance has been updated - Top-up",
                        "Top-up",
                        "Increase"
                ),
                Arguments.of(
                        "es",
                        "Your balance has been updated - Top-up",
                        "Top-up",
                        "Increase"
                )
        );
    }
}