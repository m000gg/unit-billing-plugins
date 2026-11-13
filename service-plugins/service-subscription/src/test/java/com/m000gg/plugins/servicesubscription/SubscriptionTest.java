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
        assertThat(subscription.minTermEndsAt()).isEqualTo(JAN_31);
    }

    @Test
    void activateRejectsNegativeMinPeriods() {
        Subscription subscription = newSubscription();
        assertThatThrownBy(() -> subscription.activate(JAN_31, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void activateRejectsNullNow() {
        Subscription subscription = newSubscription();
        assertThatThrownBy(() -> subscription.activate(null, 1))
                .isInstanceOf(NullPointerException.class);
    }


    @Test
    void activateTwiceIsRejected() {
        Subscription subscription = activeSince(JAN_31, 0);
        assertThatThrownBy(() -> subscription.activate(JAN_31, 0))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void activateOnPreviouslyPaidSuspendedSubscriptionIsRejected() {
        Subscription subscription = activeSince(JAN_31, 0);
        subscription.suspend();

        assertThatThrownBy(() -> subscription.activate(APR_5, 0))
                .isInstanceOf(IllegalStateException.class);

    }

    @Test
    void recordPaymentMovesTheBillingDateWithoutDrift() {
        Subscription subscription = activeSince(JAN_31, 0);

        subscription.recordPayment();

        assertThat(subscription.nextBillingAt()).isEqualTo(MAR_31);
        assertThat(subscription.nextPeriodNo()).isEqualTo(3);
        assertThat(subscription.periodsSinceAnchor()).isEqualTo(2);

        subscription.recordPayment();

        assertThat(subscription.nextBillingAt()).isEqualTo(APR_30);
        assertThat(subscription.nextPeriodNo()).isEqualTo(4);
        assertThat(subscription.periodsSinceAnchor()).isEqualTo(3);
    }

    @Test
    void recordPaymentOnSuspendedSubscriptionIsRejected() {
        Subscription subscription = newSubscription();

        assertThatThrownBy(subscription::recordPayment)
                .isInstanceOf(IllegalStateException.class);
    }


    @Test
    void subscriptionIsDueFromTheBillingDateOn() {
        Subscription subscription = activeSince(JAN_31, 0);

        assertThat(subscription.isDue(FEB_28.minusSeconds(1))).isFalse();
        assertThat(subscription.isDue(FEB_28)).isTrue();
        assertThat(subscription.isDue(FEB_28.plusSeconds(1))).isTrue();
    }

    @Test
    void suspendedSubscriptionIsNeverDue() {
        Subscription neverPaid = newSubscription();
        Subscription suspended = activeSince(JAN_31, 0);
        suspended.suspend();

        assertThat(neverPaid.isDue(MAY_5)).isFalse();
        assertThat(suspended.isDue(MAY_5)).isFalse();
    }


    @Test
    void suspendKeepsThePeriodNumberAndTheBillingDate() {
        Subscription subscription = activeSince(JAN_31, 0);
        subscription.recordPayment();

        subscription.suspend();

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.SUSPENDED);
        assertThat(subscription.nextPeriodNo()).isEqualTo(3);
        assertThat(subscription.nextBillingAt()).isEqualTo(MAR_31);
    }

    @Test
    void suspendIsRejectedWhenNotActive() {
        Subscription subscription = newSubscription();

        assertThatThrownBy(subscription::suspend)
                .isInstanceOf(IllegalStateException.class);
    }


    @Test
    void resumeStartsANewAnchorAndKeepsTheHistory() {
        Subscription subscription = activeSince(JAN_31, 3);
        subscription.recordPayment();
        subscription.suspend();

        subscription.resume(APR_5);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.anchorAt()).isEqualTo(APR_5);
        assertThat(subscription.periodsSinceAnchor()).isEqualTo(1);
        assertThat(subscription.nextPeriodNo()).isEqualTo(4);
        assertThat(subscription.nextBillingAt()).isEqualTo(MAY_5);
        assertThat(subscription.startedAt()).isEqualTo(JAN_31);
        assertThat(subscription.minTermEndsAt()).isEqualTo(APR_30);
    }

    @Test
    void resumeOnNeverPaidSubscriptionIsRejected() {
        Subscription subscription = newSubscription();

        assertThatThrownBy(() -> subscription.resume(APR_5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resumeOnActiveSubscriptionIsRejected() {
        Subscription subscription = activeSince(JAN_31, 0);

        assertThatThrownBy(() -> subscription.resume(APR_5))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void resumeRejectsNullNow() {
        Subscription subscription = activeSince(JAN_31, 0);
        subscription.suspend();

        assertThatThrownBy(() -> subscription.resume(null))
                .isInstanceOf(NullPointerException.class);
    }


    @Test
    void cancelInTheFirstMonthWaitsForTheMinimumTerm() {
        Subscription subscription = activeSince(JAN_31, 3);

        subscription.cancel(FEB_1);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.ACTIVE);
        assertThat(subscription.cancelAtPeriodEnd()).isTrue();
        assertThat(subscription.endsAt()).isEqualTo(APR_30);
        assertThat(subscription.shouldClose()).isFalse();
    }

    @Test
    void cancelAfterTheMinimumTermEndsAtTheNextBillingDate() {
        Subscription subscription = activeSince(JAN_31, 1);
        subscription.recordPayment();
        subscription.recordPayment();

        subscription.cancel(APR_5);

        assertThat(subscription.endsAt()).isEqualTo(APR_30);
        assertThat(subscription.shouldClose()).isTrue();
    }

    @Test
    void repeatedCancelDoesNotMoveEndsAt() {
        Subscription subscription = activeSince(JAN_31, 0);
        subscription.cancel(FEB_1);
        assertThat(subscription.endsAt()).isEqualTo(FEB_28);

        subscription.recordPayment();
        subscription.cancel(APR_5);

        assertThat(subscription.endsAt()).isEqualTo(FEB_28);
    }

    @Test
    void cancelOfSuspendedSubscriptionCancelsImmediately() {
        Subscription subscription = activeSince(JAN_31, 0);
        subscription.suspend();

        subscription.cancel(APR_5);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.endsAt()).isEqualTo(APR_5);
    }

    @Test
    void cancelOfNeverPaidSubscriptionCancelsImmediately() {
        Subscription subscription = newSubscription();

        subscription.cancel(JAN_31);

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELED);
        assertThat(subscription.endsAt()).isEqualTo(JAN_31);
    }

    @Test
    void cancelRejectsNullNow() {
        Subscription subscription = activeSince(JAN_31, 0);

        assertThatThrownBy(() -> subscription.cancel(null))
                .isInstanceOf(NullPointerException.class);
    }


    @Test
    void shouldCloseIsFalseWithoutCancellation() {
        Subscription subscription = activeSince(JAN_31, 0);

        assertThat(subscription.shouldClose()).isFalse();
    }

    @Test
    void shouldCloseIsFalseOnASubscriptionThatIsNotActive() {
        assertThat(newSubscription().shouldClose()).isFalse();
    }

    @Test
    void shouldCloseBecomesTrueWhenTheMinimumTermIsReached() {
        Subscription subscription = activeSince(JAN_31, 3);
        subscription.cancel(FEB_1);
        assertThat(subscription.shouldClose()).isFalse();

        subscription.recordPayment();
        assertThat(subscription.nextBillingAt()).isEqualTo(MAR_31);
        assertThat(subscription.shouldClose()).isFalse();

        subscription.recordPayment();
        assertThat(subscription.nextBillingAt()).isEqualTo(APR_30);
        assertThat(subscription.shouldClose()).isTrue();
    }

    @Test
    void closeMovesTheSubscriptionToCanceled() {
        Subscription subscription = activeSince(JAN_31, 0);
        subscription.cancel(FEB_1);

        subscription.close();

        assertThat(subscription.status()).isEqualTo(SubscriptionStatus.CANCELED);
    }

    @Test
    void closeBeforeTheEndIsRejected() {
        Subscription subscription = activeSince(JAN_31, 3);
        subscription.cancel(FEB_1);

        assertThatThrownBy(subscription::close)
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void closeWithoutCancellationIsRejected() {
        Subscription subscription = activeSince(JAN_31, 0);

        assertThatThrownBy(subscription::close)
                .isInstanceOf(IllegalStateException.class);
    }


    @Test
    void canceledSubscriptionRejectsEveryTransition() {
        Subscription subscription = canceledSubscription();

        assertThatThrownBy(subscription::suspend).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> subscription.resume(APR_5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> subscription.activate(APR_5, 0)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(subscription::recordPayment).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> subscription.cancel(APR_5)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(subscription::close).isInstanceOf(IllegalStateException.class);
    }
}
