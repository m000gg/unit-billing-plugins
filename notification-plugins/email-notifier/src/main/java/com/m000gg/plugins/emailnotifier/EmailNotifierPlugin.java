package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.query.user.UserContact;

import java.util.Optional;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EmailNotifierPlugin implements Plugin {

    private static final Logger log =
            LoggerFactory.getLogger(EmailNotifierPlugin.class);

    @Override
    public String getId() {
        return "email-notifier";
    }

    @Override
    public void onEnable(PluginContext context) {
        final SMTPServerConfiguration smtpConfig;

        try {
            smtpConfig = SMTPServerConfiguration.from(context);
        } catch (IllegalArgumentException e) {
            log.error("Email notifier disabled: invalid SMTP configuration: {}",
                    e.getMessage());
            return;
        }

        EmailSender sender = new EmailSender(smtpConfig);
        BalanceNotificationMessageFormatter formatter =
                new BalanceNotificationMessageFormatter();

        context.onBalanceChanged(event -> {
            try {
                Optional<UserContact> contact =
                        context.users().findContact(event.userId());

                if (contact.isEmpty()) {
                    log.debug("Skipping balance notification: no contact for userId={}",
                            event.userId());
                    return;
                }

                BalanceNotificationEmailMessage message =
                        formatter.formNewEmailMessage(
                                contact.get().email(),
                                event.balanceChangeType(),
                                event.balanceChangeDirection(),
                                event.balanceAfterTransaction(),
                                event.amountInUserCurrency(),
                                event.description(),
                                event.userCurrency(),
                                event.transactionTime()
                        );

                sender.send(message);
                log.debug("Balance notification sent for userId={}", event.userId());
            } catch (Exception e) {
                log.warn("Failed to send balance notification for userId={}",
                        event.userId(), e);
            }
        });

        log.info("Email notifier enabled");
    }

    @Override
    public void onDisable() {
        log.info("Email notifier disabled");
    }
}
