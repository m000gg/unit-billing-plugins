package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.event.BalanceChangeDirection;
import com.m000gg.plugin.api.event.BalanceChangeType;

import java.math.BigDecimal;

public class BalanceNotificationMessageFormatter {

    public BalanceNotificationEmailMessage formNewEmailMessage(String to, BalanceChangeType changeType, BigDecimal balanceAfterTransaction, BigDecimal amountInUserCurrency, String userCurrency){

        String subject = "";
        String body = "";
        return new BalanceNotificationEmailMessage(subject, body,to );

    }
}
