package com.m000gg.plugins.servicesubscription.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record SubscriptionCharge(
        UUID id,
        UUID subscriptionId,
        int periodNo,
        Instant periodStart,
        BigDecimal amount,
        SubscriptionChargeStatus status,
        UUID ledgerEntryId,
        Instant attemptedAt
) {
    public SubscriptionCharge {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(periodStart, "periodStart");
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(attemptedAt, "attemptedAt");
        if (periodNo < 1) {
            throw new IllegalArgumentException("periodNo must be >= 1");
        }
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be > 0");
        }
        if (status == SubscriptionChargeStatus.SUCCESS && ledgerEntryId == null) {
            throw new IllegalArgumentException("ledgerEntryId is required for SUCCESS");
        }
        if (status == SubscriptionChargeStatus.FAILED && ledgerEntryId != null) {
            throw new IllegalArgumentException("ledgerEntryId must be null for FAILED");
        }
    }
}
