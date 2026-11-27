package com.m000gg.plugins.servicesubscription.domain;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Objects;

public final class BillingSchedule {

    private BillingSchedule() {
    }

    public static Instant boundary(Instant anchor, int periods) {
        Objects.requireNonNull(anchor, "anchor");
        if (periods < 0) {
            throw new IllegalArgumentException("periods must not be negative: " + periods);
        }
        return anchor.atZone(ZoneOffset.UTC).plusMonths(periods).toInstant();
    }
}
