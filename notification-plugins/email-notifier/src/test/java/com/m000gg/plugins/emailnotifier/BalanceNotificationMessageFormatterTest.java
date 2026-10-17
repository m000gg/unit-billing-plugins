package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.event.BalanceChangeDirection;
import com.m000gg.plugin.api.event.BalanceChangeType;
import org.junit.jupiter.api.Test;

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
                        Instant.parse("2026-10-15T09:30:00Z")
                );

        assertEquals("user@example.com", message.to());
        assertEquals("Balance update: Top-up", message.subject());

        assertAll(
                () -> assertTrue(message.body().contains("Top-up")),
                () -> assertTrue(message.body().contains("increase")),
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
                        Instant.parse("2026-10-15T09:30:00Z")
                );

        assertTrue(message.subject().contains("Charge"));
        assertTrue(message.body().contains("decrease"));
        assertTrue(message.body().contains("Service charge"));
        assertTrue(message.body().contains("10.00 EUR"));
        assertTrue(message.body().contains("90.00 EUR"));
    }
}