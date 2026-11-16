package com.m000gg.plugins.servicesubscription.infrastructure;

import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.db.RowView;
import com.m000gg.plugins.servicesubscription.domain.Subscription;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class SubscriptionRepository {

    private static final String SELECT_COLUMNS = """
            SELECT id, subscriber_id, service_id, status, started_at, min_term_ends_at,
                   ends_at, anchor_at, periods_since_anchor, next_period_no,
                   next_billing_at, cancel_at_period_end, version
            FROM subscription
            """;

    private final PluginDatabase db;

    public SubscriptionRepository(PluginDatabase db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public void insert(Subscription subscription, Instant now) {
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(now, "now");
        db.execute(
                """
                INSERT INTO subscription (
                    id, subscriber_id, service_id, status, started_at, min_term_ends_at,
                    ends_at, anchor_at, periods_since_anchor, next_period_no,
                    next_billing_at, cancel_at_period_end, version, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                subscription.id(),
                subscription.subscriberId(),
                subscription.serviceId(),
                subscription.status().name(),
                subscription.startedAt(),
                subscription.minTermEndsAt(),
                subscription.endsAt(),
                subscription.anchorAt(),
                subscription.periodsSinceAnchor(),
                subscription.nextPeriodNo(),
                subscription.nextBillingAt(),
                subscription.cancelAtPeriodEnd(),
                subscription.version(),
                now,
                now
        );
    }

    public Optional<Subscription> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return db.queryOne(
                SELECT_COLUMNS + " WHERE id = ?",
                SubscriptionRepository::mapRow,
                id
        );
    }

    public List<Subscription> findBySubscriberId(UUID subscriberId) {
        Objects.requireNonNull(subscriberId, "subscriberId");
        return db.query(
                SELECT_COLUMNS + " WHERE subscriber_id = ? ORDER BY created_at DESC",
                SubscriptionRepository::mapRow,
                subscriberId
        );
    }

    public List<Subscription> findDue(Instant now, int limit) {
        Objects.requireNonNull(now, "now");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be >= 1");
        }
        return db.query(
                SELECT_COLUMNS
                        + " WHERE status = ? AND next_billing_at <= ? ORDER BY next_billing_at ASC LIMIT ?",
                SubscriptionRepository::mapRow,
                SubscriptionStatus.ACTIVE.name(),
                now,
                limit
        );
    }

    public boolean update(Subscription subscription, Instant now) {
        Objects.requireNonNull(subscription, "subscription");
        Objects.requireNonNull(now, "now");
        int updated = db.execute(
                """
                UPDATE subscription
                SET status = ?,
                    started_at = ?,
                    min_term_ends_at = ?,
                    ends_at = ?,
                    anchor_at = ?,
                    periods_since_anchor = ?,
                    next_period_no = ?,
                    next_billing_at = ?,
                    cancel_at_period_end = ?,
                    version = version + 1,
                    updated_at = ?
                WHERE id = ? AND version = ?
                """,
                subscription.status().name(),
                subscription.startedAt(),
                subscription.minTermEndsAt(),
                subscription.endsAt(),
                subscription.anchorAt(),
                subscription.periodsSinceAnchor(),
                subscription.nextPeriodNo(),
                subscription.nextBillingAt(),
                subscription.cancelAtPeriodEnd(),
                now,
                subscription.id(),
                subscription.version()
        );
        return updated == 1;
    }

    private static Subscription mapRow(RowView row) {
        return Subscription.fromDb(
                row.getUuid("id"),
                row.getUuid("subscriber_id"),
                row.getUuid("service_id"),
                SubscriptionStatus.valueOf(row.getString("status")),
                row.getInstant("started_at"),
                row.getInstant("min_term_ends_at"),
                row.getInstant("ends_at"),
                row.getInstant("anchor_at"),
                row.getInt("periods_since_anchor"),
                row.getInt("next_period_no"),
                row.getInstant("next_billing_at"),
                Boolean.TRUE.equals(row.getBoolean("cancel_at_period_end")),
                row.getLong("version")
        );
    }

    public Optional<Subscription> findLiveBySubscriberAndService(UUID subscriberId, UUID serviceId) {
        Objects.requireNonNull(subscriberId, "subscriberId");
        Objects.requireNonNull(serviceId, "serviceId");
        return db.queryOne(
                SELECT_COLUMNS + " WHERE subscriber_id = ? AND service_id = ? AND status <> ?",
                SubscriptionRepository::mapRow,
                subscriberId,
                serviceId,
                SubscriptionStatus.CANCELED.name()
        );
    }
}
