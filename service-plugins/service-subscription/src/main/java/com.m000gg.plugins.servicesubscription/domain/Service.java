package com.m000gg.plugins.servicesubscription.domain;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;
public final class Service {

    private static final int NAME_MAX_LENGTH = 200;
    private static final int PRICE_MAX_SCALE = 4;
    private static final int PRICE_MAX_INTEGER_DIGITS = 15;
    private static final int MIN_PERIODS_MAX = 120;

    private final UUID id;
    private String name;
    private String description;
    private final BigDecimal price; // in the base currency of the ledger
    private ServiceStatus serviceStatus;
    /*
    Number of billing periods
     */
    private int minPeriods;
    private final PeriodUnit period;


    private Service(UUID id, String name, String description,
                    BigDecimal price, ServiceStatus serviceStatus,
                    int minPeriods, PeriodUnit period) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.serviceStatus = serviceStatus;
        this.minPeriods = minPeriods;
        this.period = period;
    }


    public static Service create(UUID id, String name, String description, BigDecimal price, int minPeriods) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(price, "price");
        validatePrice(price);
        validateEditableFields(name, description, minPeriods);
        return new Service(id, name, description, price, ServiceStatus.ACTIVE, minPeriods, PeriodUnit.MONTH);
    }

    public void update(String newName, String newDescription, int newMinPeriods) {
        if (serviceStatus == ServiceStatus.DELETED) {
            throw new IllegalStateException("Deleted service can not be updated.");
        }
        validateEditableFields(newName, newDescription, newMinPeriods);
        name = newName;
        description = newDescription;
        minPeriods = newMinPeriods;
    }

    public void delete() {
        if (serviceStatus.equals(ServiceStatus.DELETED)) {
            throw new IllegalStateException("It is not possible to soft delete already deleted Service!");
        }
        serviceStatus = ServiceStatus.DELETED;

    }

    public boolean isAvailableForSubscription() {
        return serviceStatus == ServiceStatus.ACTIVE;
    }

    private static void validateEditableFields(String name, String description, int minPeriods) {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(description, "description");
        if (name.isBlank()) {
            throw new IllegalArgumentException("Name can not be blank.");
        }
        if (name.length() > NAME_MAX_LENGTH) {
            throw new IllegalArgumentException("Name can not be longer than " + NAME_MAX_LENGTH + " characters.");
        }
        if (minPeriods < 0 || minPeriods > MIN_PERIODS_MAX) {
            throw new IllegalArgumentException("minPeriods must be between 0 and " + MIN_PERIODS_MAX + ": " + minPeriods);
        }
    }

    private static void validatePrice(BigDecimal price) {
        if (price.signum() <= 0) {
            throw new IllegalArgumentException("Price must be greater than zero: " + price);
        }
        BigDecimal stripped = price.stripTrailingZeros();
        if (stripped.scale() > PRICE_MAX_SCALE
                || stripped.precision() - stripped.scale() > PRICE_MAX_INTEGER_DIGITS) {
            throw new IllegalArgumentException("Price does not fit into NUMERIC(19,4): " + price);
        }
    }

    public UUID id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public BigDecimal price() {
        return price;
    }

    public int minPeriods() {
        return minPeriods;
    }

    public PeriodUnit period() {
        return period;
    }

    public ServiceStatus serviceStatus() {
        return serviceStatus;
    }
}
