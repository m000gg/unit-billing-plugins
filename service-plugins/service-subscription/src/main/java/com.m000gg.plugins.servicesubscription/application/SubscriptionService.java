package com.m000gg.plugins.servicesubscription.application;

import com.m000gg.plugin.api.command.balance.BalanceCharger;
import com.m000gg.plugin.api.command.balance.ChargeRequest;
import com.m000gg.plugin.api.command.balance.ChargeResult;
import com.m000gg.plugin.api.command.balance.ChargeStatus;
import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.query.user.UserDirectory;
import com.m000gg.plugins.servicesubscription.domain.IdempotencyKeys;
import com.m000gg.plugins.servicesubscription.domain.Service;
import com.m000gg.plugins.servicesubscription.domain.Subscription;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionCharge;
import com.m000gg.plugins.servicesubscription.domain.SubscriptionChargeStatus;
import com.m000gg.plugins.servicesubscription.infrastructure.ServiceRepository;
import com.m000gg.plugins.servicesubscription.infrastructure.SubscriptionChargeRepository;
import com.m000gg.plugins.servicesubscription.infrastructure.SubscriptionRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Coordinates plugin-owned persistence via {@link PluginDatabase} with core
 * subscriber validation ({@link UserDirectory}) and charging ({@link BalanceCharger}).
 */
public final class SubscriptionService {

    private final PluginDatabase db;
    private final ServiceRepository serviceRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final SubscriptionChargeRepository chargeRepository;
    private final UserDirectory users;
    private final BalanceCharger balanceCharger;

    public SubscriptionService(PluginDatabase db, ServiceRepository serviceRepository, SubscriptionRepository subscriptionRepository, SubscriptionChargeRepository chargeRepository, UserDirectory users, BalanceCharger balanceCharger ){
        this.db = Objects.requireNonNull(db, "db");
        this.serviceRepository = Objects.requireNonNull(serviceRepository, "serviceRepository");
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository, "subscriptionRepository");
        this.chargeRepository = Objects.requireNonNull(chargeRepository, "chargeRepository");
        this.users = Objects.requireNonNull(users, "users");
        this.balanceCharger = Objects.requireNonNull(balanceCharger, "balanceCharger");
    }

    public Service createService(String name, String description, BigDecimal price, int minPeriods, Instant now) {
        Service service = Service.create(UUID.randomUUID(), name, description, price, minPeriods);
        db.inTransaction(() -> serviceRepository.insert(service, now));
        return service;
    }

    public List<Service> listActiveServices() {
        return serviceRepository.findAllActive();
    }

    public Optional<Service> findService(UUID serviceId) {
        return serviceRepository.findById(serviceId);
    }

    //
    public Subscription subscribeAndActivate(UUID subscriberId, UUID serviceId, Instant now) {
        Objects.requireNonNull(subscriberId, "subscriberId");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(now, "now");

        // Core user verification goes through UserDirectory API, never SQL to core schema.
        users.findContact(subscriberId)
                .orElseThrow(() -> new IllegalArgumentException("Subscriber not found: " + subscriberId));

        Service service = serviceRepository.findById(serviceId)
                .filter(Service::isAvailableForSubscription)
                .orElseThrow(() -> new IllegalArgumentException("Active service not found: " + serviceId));

        Subscription subscription = Subscription.createNew(UUID.randomUUID(), subscriberId, serviceId);
        db.inTransaction(() -> subscriptionRepository.insert(subscription, now));

        ChargeResult chargeResult = balanceCharger.charge(new ChargeRequest(
                subscriberId,
                service.price(),
                IdempotencyKeys.charge(subscription.id(), subscription.nextPeriodNo()),
                "Subscription: " + service.name()
        ));

        return db.inTransaction(() -> {
            if (chargeResult.status() == ChargeStatus.SUCCESS) {
                chargeRepository.insert(new SubscriptionCharge(
                        UUID.randomUUID(),
                        subscription.id(),
                        1,
                        now,
                        service.price(),
                        SubscriptionChargeStatus.SUCCESS,
                        chargeResult.ledgerEntryId(),
                        now
                ));
                subscription.activate(now, service.minPeriods());
                if (!subscriptionRepository.update(subscription, now)) {
                    throw new IllegalStateException("Concurrent update on subscription " + subscription.id());
                }
            } else {
                chargeRepository.insert(new SubscriptionCharge(
                        UUID.randomUUID(),
                        subscription.id(),
                        1,
                        now,
                        service.price(),
                        SubscriptionChargeStatus.FAILED,
                        null,
                        now
                ));
            }
            return subscription;
        });
    }
}
