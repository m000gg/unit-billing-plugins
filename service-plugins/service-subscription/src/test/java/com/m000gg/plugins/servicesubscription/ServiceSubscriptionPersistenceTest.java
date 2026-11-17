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
import com.m000gg.plugin.api.ui.PluginUi;
import com.m000gg.plugins.servicesubscription.application.SubscriptionService;
import com.m000gg.plugins.servicesubscription.domain.PeriodUnit;
import com.m000gg.plugins.servicesubscription.domain.Service;
import com.m000gg.plugins.servicesubscription.domain.ServiceStatus;
import com.m000gg.plugins.servicesubscription.domain.Subscription;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceSubscriptionPersistenceTest {

    @Test
    void pluginWiresDatabaseAndCoreApisOnEnable() {
        RecordingPluginDatabase db = new RecordingPluginDatabase();
        UserDirectory users = id -> Optional.empty();
        RecordingBalanceCharger charger = new RecordingBalanceCharger(null);

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(db, users, charger));

        assertThat(plugin.subscriptionService()).isNotNull();

        plugin.onDisable();
        assertThat(plugin.subscriptionService()).isNull();
    }

    @Test
    void subscribeAndActivateUsesPluginDatabaseForOwnTablesAndCoreApiForUserAndBalance() {
        UUID subscriberId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();
        UUID ledgerEntryId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-16T22:00:00Z");

        RowView serviceRow = mapBackedRow(Map.of(
                "id", serviceId,
                "name", "VPN Pro",
                "description", "Monthly VPN",
                "price", new BigDecimal("9.99"),
                "status", ServiceStatus.ACTIVE.name(),
                "min_periods", 1,
                "period_unit", PeriodUnit.MONTH.name()
        ));

        RecordingPluginDatabase db = new RecordingPluginDatabase(serviceRow);
        UserDirectory users = id -> Optional.of(new UserContact("user@example.com", "en"));
        RecordingBalanceCharger charger = new RecordingBalanceCharger(
                new ChargeResult(ChargeStatus.SUCCESS, ledgerEntryId)
        );

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(db, users, charger));

        SubscriptionService appService = plugin.subscriptionService();
        Subscription result = appService.subscribeAndActivate(subscriberId, serviceId, now);

        assertThat(result.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(result.startedAt()).isEqualTo(now);
        assertThat(db.executedSql).hasSize(3);
        assertThat(db.executedSql).noneMatch(sql -> sql.contains("core.") || sql.contains("public."));
        assertThat(charger.requests).hasSize(1);
        assertThat(charger.requests.getFirst().subscriberId()).isEqualTo(subscriberId);
        assertThat(charger.requests.getFirst().amount()).isEqualByComparingTo("9.99");
    }

    @Test
    void subscribeThrowsWhenSubscriberDoesNotExistInCoreDirectory() {
        RecordingPluginDatabase db = new RecordingPluginDatabase();
        UserDirectory users = id -> Optional.empty();
        RecordingBalanceCharger charger = new RecordingBalanceCharger(null);

        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(db, users, charger));

        assertThatThrownBy(() -> plugin.subscriptionService()
                .subscribeAndActivate(UUID.randomUUID(), UUID.randomUUID(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Subscriber not found");

        assertThat(charger.requests).isEmpty();
        assertThat(db.executedSql).isEmpty();
    }

    @Test
    void createServicePersistsInPluginDatabaseInsideTransaction() {
        RecordingPluginDatabase db = new RecordingPluginDatabase();
        ServiceSubscriptionPlugin plugin = new ServiceSubscriptionPlugin();
        plugin.onEnable(contextWith(db, id -> Optional.empty(), new RecordingBalanceCharger(null)));

        Service created = plugin.subscriptionService().createService(
                "Cloud Backup",
                "100 GB storage",
                new BigDecimal("4.50"),
                0,
                Instant.parse("2026-11-16T22:00:00Z")
        );

        assertThat(created.serviceStatus()).isEqualTo(ServiceStatus.ACTIVE);
        assertThat(db.transactions).isEqualTo(1);
        assertThat(db.executedSql).hasSize(1);
        assertThat(db.executedSql.getFirst()).contains("INSERT INTO service");
    }

    private static RowView mapBackedRow(Map<String, Object> values) {
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
            BalanceCharger charger
    ) {
        PluginSettings emptySettings = key -> Optional.empty();
        PluginUi noopUi = new PluginUi() {
            @Override
            public void registerAdminPages(PluginPageHandler handler) {
            }

            @Override
            public void registerClientPages(PluginPageHandler handler) {
            }

            @Override
            public void registerAdminNavigation(String title, String icon) {
            }

            @Override
            public void registerClientDashboardSection(ClientDashboardSection section) {
            }
        };

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
                return emptySettings;
            }

            @Override
            public BalanceCharger balanceCharger() {
                return charger;
            }

            @Override
            public PluginUi ui() {
                return noopUi;
            }

            @Override
            public PluginDatabase database() {
                return db;
            }
        };
    }

    private static final class RecordingBalanceCharger implements BalanceCharger {
        private final ChargeResult result;
        private final List<ChargeRequest> requests = new ArrayList<>();

        private RecordingBalanceCharger(ChargeResult result) {
            this.result = result;
        }

        @Override
        public ChargeResult charge(ChargeRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static final class RecordingPluginDatabase implements PluginDatabase {
        private final RowView singleRow;
        private final List<String> executedSql = new ArrayList<>();
        private int transactions;

        private RecordingPluginDatabase() {
            this(null);
        }

        private RecordingPluginDatabase(RowView singleRow) {
            this.singleRow = singleRow;
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> mapper, Object... params) {
            if (singleRow == null) {
                return List.of();
            }
            return List.of(mapper.map(singleRow));
        }

        @Override
        public <T> Optional<T> queryOne(String sql, RowMapper<T> mapper, Object... params) {
            if (singleRow == null || !sql.contains("FROM service")) {
                return Optional.empty();
            }
            return Optional.ofNullable(mapper.map(singleRow));
        }

        @Override
        public int execute(String sql, Object... params) {
            executedSql.add(sql);
            return 1;
        }

        @Override
        public <T> T inTransaction(Supplier<T> action) {
            transactions++;
            return action.get();
        }

        @Override
        public void inTransaction(Runnable action) {
            transactions++;
            action.run();
        }
    }
}
