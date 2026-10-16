package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.event.BalanceChangeDirection;
import com.m000gg.plugin.api.event.BalanceChangeType;

import java.math.BigDecimal;

public class BalanceNotificationMessageFormatter {

    public BalanceNotificationEmailMessage formNewEmailMessage(
            String to,
            BalanceChangeType changeType,
            BigDecimal balanceAfterTransaction,
            BigDecimal amountInUserCurrency,
            String userCurrency
    ) {
        String operation = switch (changeType) {
            case TOP_UP -> "Top-up";
            case CHARGE -> "Charge";
            case CORRECTION -> "Balance correction";
            case REFUND -> "Refund";
        };

        String subject = "Your balance has been updated — " + operation;

        String body = """
            Hello,

            Your balance has been updated.

            Operation: %s
            Amount: %s %s
            Balance after transaction: %s %s

            If you have questions about this transaction, please contact support,
            Your Unit Billing Team
            """.formatted(
                operation,
                amountInUserCurrency.toPlainString(),
                userCurrency,
                balanceAfterTransaction.toPlainString(),
                userCurrency
        );

        return new BalanceNotificationEmailMessage(subject, body, to);
    }
}
