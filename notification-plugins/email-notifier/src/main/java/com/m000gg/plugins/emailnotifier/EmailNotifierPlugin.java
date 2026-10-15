package com.m000gg.plugins.emailnotifier;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;


public final class EmailNotifierPlugin implements Plugin {

    @Override
    public String getId() {
        return "email-notifier";
    }

    @Override
    public void onEnable(PluginContext context) {
        context.onBalanceChanged(event ->
                System.out.printf(
                        "[email-notifier] Balance changed: user=%s, type=%s, amount=%s %s%n",
                        event.userId(),
                        event.balanceChangeType(),
                        event.amountInUserCurrency(),
                        event.userCurrency()
                )
        );
    }

    @Override
    public void onDisable() {
        System.out.println("[email-notifier] disabled");
    }
}
