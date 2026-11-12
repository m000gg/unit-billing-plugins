package com.m000gg.plugins.servicesubscription.domain;

import java.time.Instant;
import java.util.UUID;

public final class Subscription {

    private final UUID id;
    private final UUID subscriberId;
    private final UUID serviceId;
    private SubscriptionStatus status;
    private int nextPeriodNo;
    private Instant nextBillingAt;

    private Subscription(UUID id, UUID subscriberId, UUID serviceId,
                         SubscriptionStatus status, int nextPeriodNo, Instant nextBillingAt) {
        this.id = id;
        this.subscriberId = subscriberId;
        this.serviceId = serviceId;
        this.status = status;
        this.nextPeriodNo = nextPeriodNo;
        this.nextBillingAt = nextBillingAt;
    }


    public static Subscription createNewSubscription(UUID id, UUID subscriberId, UUID serviceId,  SubscriptionStatus status, int nextPeriodNo, Instant nextBillingAt){
        return new Subscription(id, subscriberId, serviceId, SubscriptionStatus.SUSPENDED, 1, null);
    }

    /** The only way to move from ACTIVE to SUSPENDED. Keeps nextPeriodNo and nextBillingAt untouched. */
    public void suspend(){
        if (status != SubscriptionStatus.ACTIVE){
            throw new IllegalStateException(
                    "Only an ACTIVE subscription can be suspended, but it is " + status);
        }
        status = SubscriptionStatus.SUSPENDED;
    }

    public UUID id() {
        return id;
    }

    public SubscriptionStatus status() {
        return status;
    }




}
