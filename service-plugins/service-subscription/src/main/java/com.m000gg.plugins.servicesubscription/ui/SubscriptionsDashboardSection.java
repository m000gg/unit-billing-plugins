package com.m000gg.plugins.servicesubscription.ui;

import com.m000gg.plugin.api.ui.ClientDashboardSection;

import java.util.List;

public final class SubscriptionsDashboardSection implements ClientDashboardSection {

    @Override
    public String getId() {
        return "subscriptions-overview";
    }

    @Override
    public String getTitle() {
        return "{{i18n:dashboard.title}}";
    }

    @Override
    public int getOrder() {
        return 20;
    }

    @Override
    public String getTemplate() {
        return "templates/client/subscriptions-dashboard.html";
    }

    @Override
    public List<String> getStylesheets() {
        return List.of("dashboard.css");
    }
}
