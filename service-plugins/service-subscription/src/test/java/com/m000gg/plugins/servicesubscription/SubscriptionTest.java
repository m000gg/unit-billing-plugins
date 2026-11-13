package com.m000gg.plugins.servicesubscription;

import com.m000gg.plugins.servicesubscription.domain.Subscription;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

class SubscriptionTest {
    private static final Instant JAN_31 = Instant.parse("2027-01-31T10:15:00Z");
    private static final Instant FEB_1 = Instant.parse("2027-02-01T09:00:00Z");
    private static final Instant FEB_28 = Instant.parse("2027-02-28T10:15:00Z");
    private static final Instant MAR_31 = Instant.parse("2027-03-31T10:15:00Z");
    private static final Instant APR_5 = Instant.parse("2027-04-05T08:00:00Z");
    private static final Instant APR_30 = Instant.parse("2027-04-30T10:15:00Z");
    private static final Instant MAY_5 = Instant.parse("2027-05-05T08:00:00Z");

    private static Subscription newSubscription(){
        return Subscription.createNew(UUID.randomUUID(),UUID.randomUUID(), UUID.randomUUID());
    }

    private static Subscription activeSince(Instant now, int minPeriods) {
        Subscription subscription = newSubscription();
        subscription.activate(now, minPeriods);
        return subscription;
    }

    private static Subscription canceledSubscription() {
        Subscription subscription = newSubscription();
        subscription.cancel(JAN_31);
        return subscription;
    }

    @Test
    void newSubscriptionIsSuspendedAndNeverPaid() {
        Subscription subscription = newSubscription();
        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(subscription.neverPaid()).isTrue();
        assertThat(subscription.nextPeriodNo()).isEqualTo(1);
        assertThat(subscription.nextBillingAt()).isNull();
        assertThat(subscription.startedAt()).isNull();

    }

    @Test
    void createNewKeepsTheIds() {
        UUID id = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID serviceId = UUID.randomUUID();

        Subscription subscription = Subscription.createNew(id,userId,serviceId);

        assertThat(subscription.id()).isEqualTo(id);
        assertThat(subscription.subscriberId()).isEqualTo(userId);
        assertThat(subscription.serviceId()).isEqualTo(serviceId);


    }


    @Test
    void activateStartsTheFirstPeriod() {
        Subscription subscription = newSubscription();

        subscription.activate(JAN_31, 3);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.neverPaid()).isFalse();
        assertThat(subscription.startedAt()).isEqualTo(JAN_31);
        assertThat(subscription.anchorAt()).isEqualTo(JAN_31);
        assertThat(subscription.periodsSinceAnchor()).isEqualTo(1);
        assertThat(subscription.nextPeriodNo()).isEqualTo(2);
        assertThat(subscription.nextBillingAt()).isEqualTo(FEB_28);
        assertThat(subscription.minTermEndsAt()).isEqualTo(APR_30);


    }

    @Test
    void activateWithZeroMinPeriodsHasNoMinimumTerm() {
        Subscription subscription = activeSince(JAN_31, 0);


    }

    @Test
    void activateRejectsNegativeMinPeriods() {
        Subscription subscription = newSubscription();


    }

    @Test
    void activateRejectsNullNow() {
        Subscription subscription = newSubscription();

    }
}
