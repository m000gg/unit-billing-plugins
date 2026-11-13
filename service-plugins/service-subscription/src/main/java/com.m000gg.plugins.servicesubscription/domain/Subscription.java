package com.m000gg.plugins.servicesubscription.domain;

import java.time.Instant;
import java.util.UUID;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import static com.m000gg.plugins.servicesubscription.domain.BillingSchedule.boundary;

public final class Subscription {

    private final UUID id;
    private final UUID subscriberId;
    private final UUID serviceId;
    private SubscriptionStatus status;
    private Instant startedAt;
    private Instant anchorAt;
    private Instant minTermEndsAt;
    private int periodsSinceAnchor;
    private int nextPeriodNo;
    private Instant nextBillingAt;
    private boolean cancelAtPeriodEnd;
    private Instant endsAt;

    private Subscription(UUID id, UUID subscriberId, UUID serviceId,
                         SubscriptionStatus status, int nextPeriodNo, Instant nextBillingAt) {
        this.id = id;
        this.subscriberId = subscriberId;
        this.serviceId = serviceId;
        this.status = status;
        this.nextPeriodNo = nextPeriodNo;
        this.nextBillingAt = nextBillingAt;
    }

    /** A new subscription that was never paid. It is SUSPENDED until the first payment. */
    public static Subscription createNew(UUID id, UUID subscriberId, UUID serviceId) {
        return new Subscription(id, subscriberId, serviceId, SubscriptionStatus.SUSPENDED, 1, null);
    }

    /**
     * The only way to move from ACTIVE to SUSPENDED. Keeps nextPeriodNo and nextBillingAt untouched.
     */
    public void suspend() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Only an ACTIVE subscription can be suspended, but it is " + status);
        }
        status = SubscriptionStatus.SUSPENDED;
    }

    /** The unpaid period was paid after a suspension: a new anchor starts now. */
    public void resume(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != SubscriptionStatus.SUSPENDED || startedAt == null) {
            throw new IllegalStateException(
                    "Only a previously paid SUSPENDED subscription can be resumed, but it is " + status);
        }
        anchorAt = now;
        periodsSinceAnchor = 1;
        nextPeriodNo++;
        nextBillingAt = boundary(now, 1);
        status = SubscriptionStatus.ACTIVE;
    }

    /** The first payment of a never paid subscription. */
    public void activate(Instant now, int minPeriods) {
        Objects.requireNonNull(now, "now");
        if (minPeriods < 0) {
            throw new IllegalArgumentException("minPeriods must not be negative: " + minPeriods);
        }
        if (status != SubscriptionStatus.SUSPENDED || startedAt != null) {
            throw new IllegalStateException(
                    "Only a never paid SUSPENDED subscription can be activated, but it is " + status);
        }
        startedAt = now;
        anchorAt = now;
        periodsSinceAnchor = 1;
        nextPeriodNo = 2;
        nextBillingAt = boundary(now, 1);
        minTermEndsAt = boundary(now, minPeriods);
        status = SubscriptionStatus.ACTIVE;
    }

    public void recordPayment() {
        if (status != SubscriptionStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Payment can be recorded only for an ACTIVE subscription, but it is " + status);
        }
        nextPeriodNo++;
        periodsSinceAnchor++;
        nextBillingAt = boundary(anchorAt, periodsSinceAnchor);
    }

    public void cancel(Instant now) {
        Objects.requireNonNull(now, "now");
        if (status != SubscriptionStatus.ACTIVE && status != SubscriptionStatus.SUSPENDED) {
            throw new IllegalStateException(
                    "Only an ACTIVE or SUSPENDED subscription can be cancelled, but it is " + status);
        }
        if (status == SubscriptionStatus.ACTIVE) {
            if (cancelAtPeriodEnd) {
                return;
            }
            cancelAtPeriodEnd = true;
            endsAt = nextBillingAt.isAfter(minTermEndsAt) ? nextBillingAt : minTermEndsAt;
        } else {
            endsAt = now;
            status = SubscriptionStatus.CANCELED;
        }
    }

    /** True when the subscription is due for billing but must be closed instead of charged. */
    public boolean shouldClose() {
        return status == SubscriptionStatus.ACTIVE
                && cancelAtPeriodEnd
                && !nextBillingAt.isBefore(endsAt);
    }

    public void close() {
        if (!shouldClose()) {
            throw new IllegalStateException(
                    "Subscription cannot be closed now, status is " + status);
        }
        status = SubscriptionStatus.CANCELED;
    }

    public boolean isDue(Instant now) {
        return status == SubscriptionStatus.ACTIVE && !nextBillingAt.isAfter(now);
    }

    public boolean neverPaid() {
        return startedAt == null;
    }

    public UUID id() {
        return id;
    }

    public UUID subscriberId() {
        return subscriberId;
    }

    public UUID serviceId() {
        return serviceId;
    }

    public SubscriptionStatus status() {
        return status;
    }

    public int nextPeriodNo() {
        return nextPeriodNo;
    }

    public Instant nextBillingAt() {
        return nextBillingAt;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant anchorAt() {
        return anchorAt;
    }

    public Instant minTermEndsAt() {
        return minTermEndsAt;
    }

    public Instant endsAt() {
        return endsAt;
    }

    public int periodsSinceAnchor() {
        return periodsSinceAnchor;
    }

    public boolean cancelAtPeriodEnd() {
        return cancelAtPeriodEnd;
    }
}