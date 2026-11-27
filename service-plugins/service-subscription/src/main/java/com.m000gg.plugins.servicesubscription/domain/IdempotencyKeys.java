package com.m000gg.plugins.servicesubscription.domain;

import java.util.UUID;

public final class IdempotencyKeys {

    private static final int MAX_LENGTH = 255;

    private IdempotencyKeys() {
    }

    public static String charge(UUID subscriptionId, int periodNo) {
        if (subscriptionId == null) {
            throw new IllegalArgumentException("subscriptionId cannot be null");
        }

        if (periodNo < 1) {
            throw new IllegalArgumentException("periodNo must be greater than or equal to 1");
        }
        String key = PluginId.VALUE + ":" + subscriptionId + ":" + periodNo;
        if (key.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("Generated key exceeds maximum allowed length of " + MAX_LENGTH);
        }
        return key;
    }
}
