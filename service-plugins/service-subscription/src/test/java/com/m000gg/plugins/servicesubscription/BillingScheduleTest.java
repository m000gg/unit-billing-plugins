package com.m000gg.plugins.servicesubscription;

import com.m000gg.plugins.servicesubscription.domain.BillingSchedule;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BillingScheduleTest {

    private static final Instant JAN_31 = Instant.parse("2027-01-31T10:15:00Z");

    @Test
    void zeroPeriodsReturnsTheAnchor() {
        assertThat(BillingSchedule.boundary(JAN_31, 0)).isEqualTo(JAN_31);
    }

    @Test
    void endOfMonthDoesNotDrift() {
        assertThat(BillingSchedule.boundary(JAN_31, 1)).isEqualTo(Instant.parse("2027-02-28T10:15:00Z"));
        assertThat(BillingSchedule.boundary(JAN_31, 2)).isEqualTo(Instant.parse("2027-03-31T10:15:00Z"));
        assertThat(BillingSchedule.boundary(JAN_31, 3)).isEqualTo(Instant.parse("2027-04-30T10:15:00Z"));
    }

    @Test
    void leapYearIsHandled() {
        assertThat(BillingSchedule.boundary(Instant.parse("2028-01-31T10:15:00Z"), 1))
                .isEqualTo(Instant.parse("2028-02-29T10:15:00Z"));
        assertThat(BillingSchedule.boundary(Instant.parse("2028-02-29T10:15:00Z"), 12))
                .isEqualTo(Instant.parse("2029-02-28T10:15:00Z"));
    }

    @Test
    void negativePeriodsAreRejected() {
        assertThatThrownBy(() -> BillingSchedule.boundary(JAN_31, -1))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullAnchorIsRejected() {
        assertThatThrownBy(() -> BillingSchedule.boundary(null, 1))
                .isInstanceOf(NullPointerException.class);
    }
}
