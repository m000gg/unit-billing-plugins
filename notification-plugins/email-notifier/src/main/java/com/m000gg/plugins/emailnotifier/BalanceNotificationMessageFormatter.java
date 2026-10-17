package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.event.BalanceChangeDirection;
import com.m000gg.plugin.api.event.BalanceChangeType;

import java.math.BigDecimal;
import java.time.Instant;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

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
            Instant transactionTime
    ) {
        String operation = switch (changeType) {
            case TOP_UP -> "Top-up";
            case CHARGE -> "Charge";
            case CORRECTION -> "Balance correction";
            case REFUND -> "Refund";
        };

        String directionLabel = direction.name()
                .toLowerCase(Locale.ROOT)
                .replace('_', ' ');

        String descriptionText = description == null || description.isBlank()
                ? "Not provided"
                : description.trim();

        String transactionTimeText = TRANSACTION_TIME_FORMAT.format(transactionTime);
        String subject = "Balance update: " + operation;

        String body = """
                Hello,
                
                There has been an update to your Unit Billing balance.
                
                TRANSACTION DETAILS
                ------------------
                Type:        %s
                Direction:   %s
                Description: %s
                Amount:      %s %s
                New balance: %s %s
                Date:        %s
                
                If you have questions about this transaction, please contact our support team.
                
                Best regards,
                Unit Billing Team
                """.formatted(
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
}