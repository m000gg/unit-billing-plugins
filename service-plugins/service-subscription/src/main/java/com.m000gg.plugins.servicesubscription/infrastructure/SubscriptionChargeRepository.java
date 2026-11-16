package com.m000gg.plugins.servicesubscription.infrastructure;

import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.db.RowView;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionCharge;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionChargeStatus;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

public final class SubscriptionChargeRepository {

    private final PluginDatabase db;

    public SubscriptionChargeRepository(PluginDatabase db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public void insert(SubscriptionCharge charge) {
        Objects.requireNonNull(charge, "charge");
        db.execute(
                """
                INSERT INTO subscription_charge (
                    id, subscription_id, period_no, period_start, amount,
                    status, ledger_entry_id, attempted_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                charge.id(),
                charge.subscriptionId(),
                charge.periodNo(),
                charge.periodStart(),
                charge.amount(),
                charge.status().name(),
                charge.ledgerEntryId(),
                charge.attemptedAt()
        );
    }

    public List<SubscriptionCharge> findBySubscriptionId(UUID subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        return db.query(
                """
                SELECT id, subscription_id, period_no, period_start, amount,
                       status, ledger_entry_id, attempted_at
                FROM subscription_charge
                WHERE subscription_id = ?
                ORDER BY attempted_at DESC
                """,
                SubscriptionChargeRepository::mapRow,
                subscriptionId
        );
    }

    private static SubscriptionCharge mapRow(RowView row) {
        return new SubscriptionCharge(
                row.getUuid("id"),
                row.getUuid("subscription_id"),
                row.getInt("period_no"),
                row.getInstant("period_start"),
                row.getBigDecimal("amount"),
                SubscriptionChargeStatus.valueOf(row.getString("status")),
                row.getUuid("ledger_entry_id"),
                row.getInstant("attempted_at")
        );
    }
}
