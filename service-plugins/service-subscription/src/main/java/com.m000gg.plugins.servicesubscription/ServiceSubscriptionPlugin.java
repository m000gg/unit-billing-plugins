package com.m000gg.plugins.servicesubscription;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public final class ServiceSubscriptionPlugin implements Plugin {
    private static final Logger log =
            LoggerFactory.getLogger(ServiceSubscriptionPlugin.class);

    @Override
    public String getId() {
        return "service-subscription";
    }

    @Override
    public void onEnable(PluginContext context) {
        log.info("Service subscription plugin enabled");

    }

    @Override
    public void onDisable() {
        log.info("Email notifier disabled");
    }
}
