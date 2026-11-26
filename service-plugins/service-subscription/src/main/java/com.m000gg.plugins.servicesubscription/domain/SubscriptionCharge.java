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
        BigDecimal chargedAmount,
        String chargedCurrency,
        BigDecimal exchangeRate,
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
        if (chargedAmount != null && chargedAmount.signum() <= 0) {
            throw new IllegalArgumentException("chargedAmount must be > 0");
        }
        if (chargedCurrency != null && chargedCurrency.isBlank()) {
            throw new IllegalArgumentException("chargedCurrency must not be blank");
        }
        if (chargedAmount != null && chargedCurrency == null) {
            throw new IllegalArgumentException("chargedCurrency is required when chargedAmount is set");
        }
        if (exchangeRate != null && exchangeRate.signum() <= 0) {
            throw new IllegalArgumentException("exchangeRate must be > 0");
        }
        if (status == SubscriptionChargeStatus.SUCCESS && ledgerEntryId == null) {
            throw new IllegalArgumentException("ledgerEntryId is required for SUCCESS");
        }
        if (status == SubscriptionChargeStatus.FAILED) {
            if (ledgerEntryId != null) {
                throw new IllegalArgumentException("ledgerEntryId must be null for FAILED");
            }
            if (chargedAmount != null || chargedCurrency != null || exchangeRate != null) {
                throw new IllegalArgumentException("charge snapshot must be null for FAILED");
            }
        }
    }

    public SubscriptionCharge(
            UUID id,
            UUID subscriptionId,
            int periodNo,
            Instant periodStart,
            BigDecimal amount,
            SubscriptionChargeStatus status,
            UUID ledgerEntryId,
            Instant attemptedAt
    ) {
        this(id, subscriptionId, periodNo, periodStart, amount, null, null, null, status, ledgerEntryId, attemptedAt);
    }

    public boolean hasChargedSnapshot() {
        return chargedAmount != null && chargedCurrency != null && !chargedCurrency.isBlank();
    }
}
