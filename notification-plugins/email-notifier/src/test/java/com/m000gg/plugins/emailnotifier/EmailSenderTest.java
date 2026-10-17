package com.m000gg.plugins.emailnotifier;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class EmailSenderTest {

    @Test
    void shouldPropagateSmtpDeliveryFailure() {
        SMTPServerConfiguration config = new SMTPServerConfiguration(
                "127.0.0.1",
                1,
                "billing@example.com",
                null,
                null,
                false
        );

        EmailSender sender = new EmailSender(config);

        BalanceNotificationEmailMessage message =
                new BalanceNotificationEmailMessage(
                        "Balance update",
                        "Your balance was updated.",
                        "user@example.com"
                );

        assertThrows(
                Exception.class,
                () -> sender.send(message)
        );
    }
}