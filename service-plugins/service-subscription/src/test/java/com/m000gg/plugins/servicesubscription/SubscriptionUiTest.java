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
import com.m000gg.plugin.api.query.fx.ExchangeRate;
import com.m000gg.plugin.api.query.fx.ExchangeRateDirectory;
import com.m000gg.plugin.api.query.settings.PluginSettings;
import com.m000gg.plugin.api.spi.fx.ExchangeRateProvider;
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
    void adminPageEscapesHtmlHandlesCreateAndRendersAllSubscriptions() {
        CapturingUi ui = new CapturingUi();
        UUID subscriberId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        RowView xssService = row(Map.of(
                "id", serviceId,
                "name", "<script>alert(1)</script>",
                "description", "<b>desc</b>",
                "price", new BigDecimal("12.00"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 3,
                "period_unit", "MONTH"
        ));
        RowView activeSub = row(Map.ofEntries(
                Map.entry("id", subId),
                Map.entry("subscriber_id", subscriberId),
                Map.entry("service_id", serviceId),
                Map.entry("status", SubscriptionStatus.ACTIVE.name()),
                Map.entry("started_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("min_term_ends_at", Instant.parse("2027-02-01T00:00:00Z")),
                Map.entry("anchor_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("periods_since_anchor", 1),
                Map.entry("next_period_no", 2),
                Map.entry("next_billing_at", Instant.parse("2026-12-01T00:00:00Z")),
                Map.entry("cancel_at_period_end", false),
                Map.entry("version", 0L)
        ));

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(
                new TableAwareStubDatabase(List.of(xssService), List.of(activeSub)),
                id -> id.equals(subscriberId)
                        ? Optional.of(new UserContact("sub@example.com", "en"))
                        : Optional.empty(),
                req -> null,
                ui
        ));

        try {
            PluginPageResponse getResponse = ui.adminHandler.handle(pageRequest("GET", "/", UUID.randomUUID(), Map.of()));
            assertThat(getResponse).isInstanceOf(PluginPageResponse.Html.class);
            PluginPageResponse.Html html = (PluginPageResponse.Html) getResponse;
            assertThat(html.stylesheets()).containsExactly("/plugin-assets/service-subscription/admin.css");
            assertThat(html.body()).contains("&lt;script&gt;alert(1)&lt;/script&gt;");
            assertThat(html.body()).doesNotContain("<script>alert(1)</script>");
            assertThat(html.body()).contains("name=\"_csrf\" value=\"tok-123\"");
            assertThat(html.body()).contains("{{i18n:admin.subs.heading}}");
            assertThat(html.body()).contains("sub@example.com");

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

            PluginPageResponse cancelResp = ui.adminHandler.handle(pageRequest(
                    "POST",
                    "/subscriptions/cancel",
                    UUID.randomUUID(),
                    Map.of("subscriptionId", List.of(subId.toString()))
            ));
            assertThat(cancelResp).isEqualTo(PluginPageResponse.redirect("/?status=sub_canceled"));
        } finally {
            plugin.onDisable();
        }
    }

    @Test
    void clientPageRequiresLegalCheckboxOnSubscribe() {
        CapturingUi ui = new CapturingUi();
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        RowView svcRow = row(Map.of(
                "id", serviceId,
                "name", "Fiber 100",
                "description", "Fast internet",
                "price", new BigDecimal("25.00"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 12,
                "period_unit", "MONTH"
        ));

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(
                new TableAwareStubDatabase(List.of(svcRow), List.of()),
                id -> Optional.of(new UserContact("a@b.c", "en")),
                req -> new ChargeResult(ChargeStatus.SUCCESS, UUID.randomUUID()),
                ui
        ));

        try {
            PluginPageResponse.Html home = (PluginPageResponse.Html) ui.clientHandler.handle(
                    pageRequest("GET", "/", userId, Map.of()));
            assertThat(home.body()).contains("name=\"termsAccepted\"");
            assertThat(home.body()).contains("{{i18n:client.legal.checkbox}}");

            PluginPageResponse rejected = ui.clientHandler.handle(pageRequest(
                    "POST", "/subscribe", userId, Map.of("serviceId", List.of(serviceId.toString()))
            ));
            assertThat(rejected).isEqualTo(PluginPageResponse.redirect("/?status=terms_required"));

            PluginPageResponse accepted = ui.clientHandler.handle(pageRequest(
                    "POST",
                    "/subscribe",
                    userId,
                    Map.of(
                            "serviceId", List.of(serviceId.toString()),
                            "termsAccepted", List.of("true")
                    )
            ));
            assertThat(accepted).isEqualTo(PluginPageResponse.redirect("/?status=activated"));
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

    @Test
    void clientPageShowsConvertedPriceWhenExchangeRateAvailable() {
        CapturingUi ui = new CapturingUi();
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        RowView svcRow = row(Map.of(
                "id", serviceId,
                "name", "Cloud VPS",
                "description", "Fast VPS",
                "price", new BigDecimal("10.00"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 1,
                "period_unit", "MONTH"
        ));
        RowView activeSub = row(Map.ofEntries(
                Map.entry("id", subId),
                Map.entry("subscriber_id", userId),
                Map.entry("service_id", serviceId),
                Map.entry("status", SubscriptionStatus.ACTIVE.name()),
                Map.entry("started_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("min_term_ends_at", Instant.parse("2026-12-01T00:00:00Z")),
                Map.entry("anchor_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("periods_since_anchor", 1),
                Map.entry("next_period_no", 2),
                Map.entry("next_billing_at", Instant.parse("2026-12-01T00:00:00Z")),
                Map.entry("cancel_at_period_end", false),
                Map.entry("version", 0L)
        ));

        RowView snapshotChargeRow = row(Map.ofEntries(
                Map.entry("id", UUID.randomUUID()),
                Map.entry("subscription_id", subId),
                Map.entry("period_no", 1),
                Map.entry("period_start", Instant.parse("2026-10-01T00:00:00Z")),
                Map.entry("amount", new BigDecimal("10.00")),
                Map.entry("charged_amount", new BigDecimal("15.30")),
                Map.entry("charged_currency", "GBP"),
                Map.entry("exchange_rate", new BigDecimal("1.53")),
                Map.entry("status", "SUCCESS"),
                Map.entry("ledger_entry_id", UUID.randomUUID()),
                Map.entry("attempted_at", Instant.parse("2026-10-01T00:00:00Z"))
        ));
        RowView legacyChargeRow = row(Map.of(
                "id", UUID.randomUUID(),
                "subscription_id", subId,
                "period_no", 2,
                "period_start", Instant.parse("2026-11-01T00:00:00Z"),
                "amount", new BigDecimal("10.00"),
                "status", "SUCCESS",
                "ledger_entry_id", UUID.randomUUID(),
                "attempted_at", Instant.parse("2026-11-01T00:00:00Z")
        ));

        ExchangeRateDirectory fx = new ExchangeRateDirectory() {
            @Override
            public String baseCurrency() {
                return "USD";
            }

            @Override
            public Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency) {
                if ("USD".equals(fromCurrency) && "UAH".equals(toCurrency)) {
                    return Optional.of(new ExchangeRate(
                            new BigDecimal("41.50"),
                            "nbu-exchange-rate",
                            Instant.parse("2026-11-26T10:00:00Z")));
                }
                return Optional.empty();
            }
        };

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(
                new TableAwareStubDatabase(
                        List.of(svcRow),
                        List.of(activeSub),
                        List.of(snapshotChargeRow, legacyChargeRow)),
                id -> Optional.of(new UserContact("a@b.c", "en", "UAH")),
                req -> new ChargeResult(ChargeStatus.SUCCESS, UUID.randomUUID()),
                ui,
                fx
        ));

        try {
            PluginPageResponse.Html home = (PluginPageResponse.Html) ui.clientHandler.handle(
                    pageRequest("GET", "/", userId, Map.of()));
            assertThat(home.body()).contains("415 UAH / {{i18n:unit.month}}");
            assertThat(home.body()).doesNotContain("USD");

            PluginPageResponse.Html charges = (PluginPageResponse.Html) ui.clientHandler.handle(
                    pageRequest("GET", "/charges", userId, Map.of("subscriptionId", List.of(subId.toString()))));
            assertThat(charges.body()).contains("15.3 GBP");
            assertThat(charges.body()).contains("415 UAH");
            assertThat(charges.body()).doesNotContain("USD");
        } finally {
            plugin.onDisable();
        }
    }

    @Test
    void clientPageFallsBackToBaseCurrencyWhenExchangeRateUnavailable() {
        CapturingUi ui = new CapturingUi();
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();

        RowView svcRow = row(Map.of(
                "id", serviceId,
                "name", "Cloud VPS",
                "description", "Fast VPS",
                "price", new BigDecimal("10.00"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 1,
                "period_unit", "MONTH"
        ));
        RowView activeSub = row(Map.ofEntries(
                Map.entry("id", subId),
                Map.entry("subscriber_id", userId),
                Map.entry("service_id", serviceId),
                Map.entry("status", SubscriptionStatus.ACTIVE.name()),
                Map.entry("started_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("min_term_ends_at", Instant.parse("2026-12-01T00:00:00Z")),
                Map.entry("anchor_at", Instant.parse("2026-11-01T00:00:00Z")),
                Map.entry("periods_since_anchor", 1),
                Map.entry("next_period_no", 2),
                Map.entry("next_billing_at", Instant.parse("2026-12-01T00:00:00Z")),
                Map.entry("cancel_at_period_end", false),
                Map.entry("version", 0L)
        ));
        RowView chargeRow = row(Map.of(
                "id", UUID.randomUUID(),
                "subscription_id", subId,
                "period_no", 1,
                "period_start", Instant.parse("2026-11-01T00:00:00Z"),
                "amount", new BigDecimal("10.00"),
                "status", "SUCCESS",
                "ledger_entry_id", UUID.randomUUID(),
                "attempted_at", Instant.parse("2026-11-01T00:00:00Z")
        ));

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(
                new TableAwareStubDatabase(List.of(svcRow), List.of(activeSub), List.of(chargeRow)),
                id -> Optional.of(new UserContact("a@b.c", "en", "UAH")),
                req -> new ChargeResult(ChargeStatus.SUCCESS, UUID.randomUUID()),
                ui
        ));

        try {
            PluginPageResponse.Html home = (PluginPageResponse.Html) ui.clientHandler.handle(
                    pageRequest("GET", "/", userId, Map.of()));
            assertThat(home.body()).contains("10 USD");
            assertThat(home.body()).doesNotContain("UAH");

            PluginPageResponse.Html charges = (PluginPageResponse.Html) ui.clientHandler.handle(
                    pageRequest("GET", "/charges", userId, Map.of("subscriptionId", List.of(subId.toString()))));
            assertThat(charges.body()).contains("10 USD");
            assertThat(charges.body()).doesNotContain("UAH");
        } finally {
            plugin.onDisable();
        }
    }

    @Test
    void adminPageDisplaysBaseCurrencyAndExchangeRates() {
        CapturingUi ui = new CapturingUi();
        UUID serviceId = UUID.randomUUID();

        RowView svcRow = row(Map.of(
                "id", serviceId,
                "name", "Cloud VPS",
                "description", "Fast VPS",
                "price", new BigDecimal("12.00"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 1,
                "period_unit", "MONTH"
        ));

        ExchangeRateDirectory fx = new ExchangeRateDirectory() {
            @Override
            public String baseCurrency() {
                return "USD";
            }

            @Override
            public Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency) {
                Instant fetchedAt = Instant.parse("2026-11-26T10:00:00Z");
                if ("USD".equals(fromCurrency) && "UAH".equals(toCurrency)) {
                    return Optional.of(new ExchangeRate(new BigDecimal("41.50"), "nbu-exchange-rate", fetchedAt));
                }
                if ("USD".equals(fromCurrency) && "EUR".equals(toCurrency)) {
                    return Optional.of(new ExchangeRate(new BigDecimal("0.92"), "nbu-exchange-rate", fetchedAt));
                }
                return Optional.empty();
            }
        };

        ServiceSubscriptionPlugin pluginWithFx = new ServiceSubscriptionPlugin();
        pluginWithFx.onEnable(contextWith(
                new TableAwareStubDatabase(List.of(svcRow), List.of()),
                id -> Optional.empty(),
                req -> null,
                ui,
                fx
        ));

        try {
            PluginPageResponse.Html html = (PluginPageResponse.Html) ui.adminHandler.handle(
                    pageRequest("GET", "/", UUID.randomUUID(), Map.of()));
            assertThat(html.body()).contains("{{i18n:admin.fx.heading}}");
            assertThat(html.body()).contains("USD");
            assertThat(html.body()).contains("nbu-exchange-rate");
            assertThat(html.body()).contains("1 USD = 41.5 UAH");
            assertThat(html.body()).contains("1 USD = 0.92 EUR");
            assertThat(html.body()).contains("12 USD / {{i18n:unit.month}}");
        } finally {
            pluginWithFx.onDisable();
        }

        CapturingUi uiWithoutFx = new CapturingUi();
        ServiceSubscriptionPlugin pluginWithoutFx = new ServiceSubscriptionPlugin();
        pluginWithoutFx.onEnable(contextWith(
                new TableAwareStubDatabase(List.of(svcRow), List.of()),
                id -> Optional.empty(),
                req -> null,
                uiWithoutFx
        ));

        try {
            PluginPageResponse.Html html = (PluginPageResponse.Html) uiWithoutFx.adminHandler.handle(
                    pageRequest("GET", "/", UUID.randomUUID(), Map.of()));
            assertThat(html.body()).contains("{{i18n:admin.fx.unavailable}}");
        } finally {
            pluginWithoutFx.onDisable();
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
        ExchangeRateDirectory defaultFx = new ExchangeRateDirectory() {
            @Override
            public String baseCurrency() {
                return "USD";
            }

            @Override
            public Optional<ExchangeRate> findRate(String fromCurrency, String toCurrency) {
                return Optional.empty();
            }
        };
        return contextWith(db, users, charger, ui, defaultFx);
    }

    private static PluginContext contextWith(
            PluginDatabase db,
            UserDirectory users,
            BalanceCharger charger,
            PluginUi ui,
            ExchangeRateDirectory exchangeRates
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

            @Override
            public ExchangeRateDirectory exchangeRates() {
                return exchangeRates;
            }

            @Override
            public void registerExchangeRateProvider(ExchangeRateProvider provider) {
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

    private static final class TableAwareStubDatabase implements PluginDatabase {
        private final List<RowView> serviceRows;
        private final List<RowView> subscriptionRows;
        private final List<RowView> chargeRows;

        private TableAwareStubDatabase(List<RowView> serviceRows, List<RowView> subscriptionRows) {
            this(serviceRows, subscriptionRows, List.of());
        }

        private TableAwareStubDatabase(
                List<RowView> serviceRows,
                List<RowView> subscriptionRows,
                List<RowView> chargeRows
        ) {
            this.serviceRows = serviceRows;
            this.subscriptionRows = subscriptionRows;
            this.chargeRows = chargeRows;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
            return selectRows(sql).stream().map(mapper::map).toList();
        }

        @Override
        public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
            List<RowView> source = selectRows(sql);
            return source.isEmpty() ? Optional.empty() : Optional.ofNullable(mapper.map(source.getFirst()));
        }

        private List<RowView> selectRows(String sql) {
            if (sql.contains("FROM subscription_charge")) {
                return chargeRows;
            }
            if (sql.contains("FROM service")) {
                return serviceRows;
            }
            return subscriptionRows;
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
