package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.user.UserContact;

import java.util.Optional;


public final class EmailNotifierPlugin implements Plugin {

    @Override
    public String getId() {
        return "email-notifier";
    }

    @Override
    public void onEnable(PluginContext context) {
        SMTPServerConfiguration smtpConfig =
                SMTPServerConfiguration.from(context);

        EmailSender sender = new EmailSender(smtpConfig);

        BalanceNotificationMessageFormatter balanceNotificationMessageFormatter = new BalanceNotificationMessageFormatter();
        context.onBalanceChanged(event -> {
                    try {
                        Optional<UserContact> contact = context.users().findContact(event.userId());
                        if (contact.isEmpty()) {
                            return;
                        }
                        String sendTo = contact.get().email();
                        BalanceNotificationEmailMessage message = balanceNotificationMessageFormatter.formNewEmailMessage(sendTo, event.balanceChangeType(),  event.balanceAfterTransaction(), event.amountInUserCurrency(), event.userCurrency());
                        sender.send(message);
                    } catch (Exception e) {
                        System.err.println("[email-notifier] failed to send email: " + e);
                    }
                });
    }

    @Override
    public void onDisable() {
        System.out.println("[email-notifier] disabled");
    }
}
