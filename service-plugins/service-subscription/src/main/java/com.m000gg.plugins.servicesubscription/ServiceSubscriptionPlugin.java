package com.m000gg.plugins.servicesubscription;

import com.m000gg.plugin.api.Plugin;
import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.command.balance.BalanceCharger;
import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.query.user.UserDirectory;
import com.m000gg.plugins.servicesubscription.application.SubscriptionService;
import com.m000gg.plugins.servicesubscription.domain.PluginId;
import com.m000gg.plugins.servicesubscription.infrastructure.ServiceRepository;
import com.m000gg.plugins.servicesubscription.infrastructure.SubscriptionChargeRepository;
import com.m000gg.plugins.servicesubscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

public final class ServiceSubscriptionPlugin implements Plugin {
    private static final Logger log =
            LoggerFactory.getLogger(ServiceSubscriptionPlugin.class);

    private SubscriptionService subscriptionService;

    @Override
    public String getId() {
        return PluginId.VALUE;
    }

    @Override
    public void onEnable(PluginContext context) {
        Objects.requireNonNull(context, "context");
        PluginDatabase db = Objects.requireNonNull(context.database(), "context.database()");
        UserDirectory users = Objects.requireNonNull(context.users(), "context.users()");
        BalanceCharger balanceCharger = Objects.requireNonNull(context.balanceCharger(), "context.balanceCharger()");

        ServiceRepository serviceRepository = new ServiceRepository(db);
        SubscriptionRepository subscriptionRepository = new SubscriptionRepository(db);
        SubscriptionChargeRepository chargeRepository = new SubscriptionChargeRepository(db);

        this.subscriptionService = new SubscriptionService(
                db,
                serviceRepository,
                subscriptionRepository,
                chargeRepository,
                users,
                balanceCharger
        );

        log.info("Service subscription plugin enabled");
    }

    @Override
    public void onDisable() {
        this.subscriptionService = null;
        log.info("Service subscription plugin disabled");
    }

    public SubscriptionService subscriptionService() {
        return subscriptionService;
    }
}
