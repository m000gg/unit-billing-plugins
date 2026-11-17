package com.m000gg.plugins.servicesubscription;

import com.m000gg.plugin.api.PluginContext;
import com.m000gg.plugin.api.command.balance.BalanceCharger;
import com.m000gg.plugin.api.command.balance.ChargeRequest;
import com.m000gg.plugin.api.command.balance.ChargeResult;
import com.m000gg.plugin.api.command.balance.ChargeStatus;
import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.db.RowMapper;
import com.m000gg.plugin.api.db.RowView;
import com.m000gg.plugin.api.event.BalanceChanged;
import com.m000gg.plugin.api.query.settings.PluginSettings;
import com.m000gg.plugin.api.query.user.UserContact;
import com.m000gg.plugin.api.query.user.UserDirectory;
import com.m000gg.plugin.api.ui.ClientDashboardSection;
import com.m000gg.plugin.api.ui.PluginPageHandler;
import com.m000gg.plugin.api.ui.PluginPageRequest;
import com.m000gg.plugin.api.ui.PluginPageResponse;
import com.m000gg.plugin.api.ui.PluginUi;
import com.m000gg.plugins.servicesubscription.domain.ServiceStatus;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionStatus;
import com.m000gg.plugins.servicesubscription.ui.SubscriptionsDashboardSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class SubscriptionUiTest {

    @Test
    void pluginRegistersAdminClientPagesAndDashboardSection() {
        CapturingUi ui = new CapturingUi();
        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();

        plugin.onEnable(contextWith(new StubDatabase(List.of()), id -> Optional.empty(), req -> null, ui));
        try {
            assertThat(ui.adminNavTitle).isEqualTo("{{i18n:admin.nav}}");
            assertThat(ui.adminHandler).isNotNull();
            assertThat(ui.clientHandler).isNotNull();
            assertThat(ui.dashboardSection).isInstanceOf(SubscriptionsDashboardSection.class);
            assertThat(ui.dashboardSection.getTemplate()).isEqualTo("templates/client/subscriptions-dashboard.html");
        } finally {
            plugin.onDisable();
        }
    }

    @Test
    void adminPageEscapesHtmlAndHandlesCreateRedirect() {
        CapturingUi ui = new CapturingUi();
        RowView xssService = row(Map.of(
                "id", UUID.randomUUID(),
                "name", "<script>alert(1)</script>",
                "description", "<b>desc</b>",
                "price", new BigDecimal("12.00"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 1,
                "period_unit", "MONTH"
        ));
        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(new StubDatabase(List.of(xssService)), id -> Optional.empty(), req -> null, ui));

        try {
            PluginPageResponse getResponse = ui.adminHandler.handle(pageRequest("GET", "/", UUID.randomUUID(), Map.of()));
            assertThat(getResponse).isInstanceOf(PluginPageResponse.Html.class);
            PluginPageResponse.Html html = (PluginPageResponse.Html) getResponse;
            assertThat(html.stylesheets()).containsExactly("/plugin-assets/service-subscription/admin.css");
            assertThat(html.body()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
            assertThat(html.body()).doesNotContain("<script>alert(1)</script>");
            assertThat(html.body()).contains("name=\"_csrf\" value=\"tok-123\"");

            PluginPageResponse postResponse = ui.adminHandler.handle(pageRequest(
                    "POST",
                    "/services/create",
                    UUID.randomUUID(),
                    Map.of(
                            "name", List.of("Pro Plan"),
                            "description", List.of("Desc"),
                            "price", List.of("19.99"),
                            "minPeriods", List.of("1")
                    )
            ));
            assertThat(postResponse).isEqualTo(PluginPageResponse.redirect("/?status=created"));
        } finally {
            plugin.onDisable();
        }
    }

    @Test
    void clientPageForbidsViewingOrCancelingAnotherUsersSubscription() {
        CapturingUi ui = new CapturingUi();
        UUID ownerId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        RowView subRow = row(Map.ofEntries(
                Map.entry("id", subId),
                Map.entry("subscriber_id", ownerId),
                Map.entry("service_id", UUID.randomUUID()),
                Map.entry("status", SubscriptionStatus.ACTIVE.name()),
                Map.entry("started_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("min_term_ends_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("anchor_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("periods_since_anchor", 1),
                Map.entry("next_period_no", 2),
                Map.entry("next_billing_at", Instant.parse("2026-12-01T00:00:00Z")),
                Map.entry("cancel_at_period_end", false),
                Map.entry("version", 0L)
        ));

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(
                new StubDatabase(List.of(subRow)),
                id -> Optional.of(new UserContact("a@b.c", "en")),
                req -> new ChargeResult(ChargeStatus.SUCCESS, UUID.randomUUID()),
                ui
        ));

        try {
            PluginPageResponse chargesResp = ui.clientHandler.handle(pageRequest(
                    "GET", "/charges", otherUserId, Map.of("subscriptionId", List.of(subId.toString()))
            ));
            assertThat(chargesResp).isEqualTo(PluginPageResponse.forbidden());

            PluginPageResponse cancelResp = ui.clientHandler.handle(pageRequest(
                    "POST", "/cancel", otherUserId, Map.of("subscriptionId", List.of(subId.toString()))
            ));
            assertThat(cancelResp).isEqualTo(PluginPageResponse.forbidden());
        } finally {
            plugin.onDisable();
        }
    }

    private static PluginPageRequest pageRequest(
            String method,
            String path,
            UUID userId,
            Map<String, List<String>> params
    ) {
        return new PluginPageRequest(
                method,
                path,
                params,
                userId,
                "en",
                "_csrf",
                "tok-123",
                "/plugin-assets/service-subscription"
        );
    }

    private static RowView row(Map<String, Object> values) {
        return new RowView() {
            @Override
            public String getString(String column) {
                return (String) values.get(column);
            }

            @Override
            public Integer getInt(String column) {
                return (Integer) values.get(column);
            }

            @Override
            public Long getLong(String column) {
                return (Long) values.get(column);
            }

            @Override
            public Boolean getBoolean(String column) {
                return (Boolean) values.get(column);
            }

            @Override
            public BigDecimal getBigDecimal(String column) {
                return (BigDecimal) values.get(column);
            }

            @Override
            public UUID getUuid(String column) {
                return (UUID) values.get(column);
            }

            @Override
            public Instant getInstant(String column) {
                return (Instant) values.get(column);
            }
        };
    }

    private static PluginContext contextWith(
            PluginDatabase db,
            UserDirectory users,
            BalanceCharger charger,
            PluginUi ui
    ) {
        return new PluginContext() {
            @Override
            public void onBalanceChanged(Consumer<BalanceChanged> handler) {
            }

            @Override
            public UserDirectory users() {
                return users;
            }

            @Override
            public PluginSettings settings() {
                return key -> Optional.empty();
            }

            @Override
            public BalanceCharger balanceCharger() {
                return charger;
            }

            @Override
            public PluginUi ui() {
                return ui;
            }

            @Override
            public PluginDatabase database() {
                return db;
            }
        };
    }

    private static final class CapturingUi implements PluginUi {
        private PluginPageHandler adminHandler;
        private PluginPageHandler clientHandler;
        private String adminNavTitle;
        private ClientDashboardSection dashboardSection;

        @Override
        public void registerAdminPages(PluginPageHandler handler) {
            this.adminHandler = handler;
        }

        @Override
        public void registerClientPages(PluginPageHandler handler) {
            this.clientHandler = handler;
        }

        @Override
        public void registerAdminNavigation(String title, String icon) {
            this.adminNavTitle = title;
        }

        @Override
        public void registerClientDashboardSection(ClientDashboardSection section) {
            this.dashboardSection = section;
        }
    }

    private static final class StubDatabase implements PluginDatabase {
        private final List<RowView> rows;

        private StubDatabase(List<RowView> rows) {
            this.rows = rows;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
            return rows.stream().map(mapper::map).toList();
        }

        @Override
        public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
            return rows.isEmpty() ? Optional.empty() : Optional.ofNullable(mapper.map(rows.getFirst()));
        }

        @Override
        public int execute(String sql, Object... params) {
            return 1;
        }

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            return action.get();
        }

        @Override
        public void inTransaction(Runnable action) {
            action.run();
        }
    }
}
