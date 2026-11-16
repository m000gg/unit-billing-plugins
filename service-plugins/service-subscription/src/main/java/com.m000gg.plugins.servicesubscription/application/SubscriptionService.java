package com.m000gg.plugins.servicesubscription.application;

import com.m000gg.plugin.api.command.balance.BalanceCharger;
import com.m000gg.plugin.api.command.balance.ChargeRequest;
import com.m000gg.plugin.api.command.balance.ChargeResult;
import com.m000gg.plugin.api.command.balance.ChargeStatus;
import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.query.user.UserDirectory;
import com.m000gg.plugins.servicesubscription.domain.*;
import com.m000gg.plugins.servicesubscription.infrastructure.ServiceRepository;
import com.m000gg.plugins.servicesubscription.infrastructure.SubscriptionChargeRepository;
import com.m000gg.plugins.servicesubscription.infrastructure.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    private static final Logger log = LoggerFactory.getLogger(SubscriptionService.class);

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

    // subscribe to a service adn activate if enough money
    public Subscription subscribeAndActivate(UUID subscriberId, UUID serviceId, Instant now) {
        Objects.requireNonNull(subscriberId, "subscriberId");
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(now, "now");

        users.findContact(subscriberId)
                .orElseThrow(() -> new IllegalArgumentException("Subscriber not found: " + subscriberId));

        Service service = serviceRepository.findById(serviceId)
                .filter(Service::isAvailableForSubscription)
                .orElseThrow(() -> new IllegalArgumentException("Active service not found: " + serviceId));

        Subscription subscription = db.inTransaction(() -> {
            Optional<Subscription> existing = subscriptionRepository
                    .findLiveBySubscriberAndService(subscriberId, serviceId);
            if (existing.isPresent()) {
                Subscription current = existing.get();
                if (current.status() == SubscriptionStatus.ACTIVE) {
                    throw new IllegalStateException("Subscriber already has an active subscription for this service");
                }
                return current;
            }
            Subscription created = Subscription.createNew(UUID.randomUUID(), subscriberId, serviceId);
            subscriptionRepository.insert(created, now);
            return created;
        });

        int periodNo = subscription.nextPeriodNo();

        ChargeResult chargeResult = balanceCharger.charge(new ChargeRequest(
                subscriberId,
                service.price(),
                IdempotencyKeys.charge(subscription.id(), periodNo),
                "Subscription: " + service.name()
        ));

        return db.inTransaction(() -> {
            if (chargeResult.status() == ChargeStatus.SUCCESS) {
                chargeRepository.insert(new SubscriptionCharge(
                        UUID.randomUUID(),
                        subscription.id(),
                        periodNo,
                        now,
                        service.price(),
                        SubscriptionChargeStatus.SUCCESS,
                        chargeResult.ledgerEntryId(),
                        now
                ));
                if (subscription.neverPaid()) {
                    subscription.activate(now, service.minPeriods());
                } else {
                    subscription.resume(now);
                }
                if (!subscriptionRepository.update(subscription, now)) {
                    throw new IllegalStateException("Concurrent update on subscription " + subscription.id());
                }
            } else {
                chargeRepository.insert(new SubscriptionCharge(
                        UUID.randomUUID(),
                        subscription.id(),
                        periodNo,
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

    public Service updateService(UUID serviceId,String name, String description, int minPeriods, Instant now) {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(now, "now");

        return db.inTransaction(() -> {
            Service service = serviceRepository.findById(serviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

            service.update(name, description, minPeriods);

            if (!serviceRepository.update(service, now)) {
                throw new IllegalStateException("Service disappeared during update: " + serviceId);
            }
            return service;
        });
    }

    public void deleteService(UUID serviceId, Instant now) {
        Objects.requireNonNull(serviceId, "serviceId");
        Objects.requireNonNull(now, "now");

        db.inTransaction(() -> {
            Service service = serviceRepository.findById(serviceId)
                    .orElseThrow(() -> new IllegalArgumentException("Service not found: " + serviceId));

            service.delete();

            if (!serviceRepository.update(service, now)) {
                throw new IllegalStateException("Service disappeared during delete: " + serviceId);
            }
        });
    }

    public Subscription cancelSubscription(UUID subscriptionId, Instant now) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        Objects.requireNonNull(now, "now");

        return db.inTransaction(() -> {
            Subscription subscription = subscriptionRepository.findById(subscriptionId)
                    .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));

            subscription.cancel(now);

            if (!subscriptionRepository.update(subscription, now)) {
                throw new IllegalStateException("Concurrent update on subscription " + subscriptionId);
            }
            return subscription;
        });
    }

    // Process subscriptios where next_billing_at <= now
    public int processDueSubscriptions(Instant now, int batchSize) {
        Objects.requireNonNull(now, "now");
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be >= 1");
        }

        List<Subscription> dueSubscriptions = subscriptionRepository.findDue(now, batchSize);
        int processed = 0;

        for (Subscription candidate : dueSubscriptions) {
            try {
                if (processSingleDue(candidate.id(), now)) {
                    processed++;
                }
            } catch (RuntimeException e) {
                log.error("Failed to process due subscription {}", candidate.id(), e);
            }
        }
        return processed;
    }

    // process ONE subscription: close, continue OR suspend
    private boolean processSingleDue(UUID subscriptionId, Instant now) {
        Subscription subscription = db.inTransaction(() -> {
            Subscription current = subscriptionRepository.findById(subscriptionId).orElse(null);
            if (current == null || !current.isDue(now)) {
                return null;
            }
            if (current.shouldClose()) {
                current.close();
                if (!subscriptionRepository.update(current, now)) {
                    throw new IllegalStateException("Concurrent update on subscription " + current.id());
                }
                return null;
            }
            return current;
        });

        if (subscription == null) {
            return false;
        }

        Service service = serviceRepository.findById(subscription.serviceId())
                .orElseThrow(() -> new IllegalStateException("Service not found: " + subscription.serviceId()));

        int periodNo = subscription.nextPeriodNo();
        Instant periodStart = subscription.nextBillingAt();


        ChargeResult chargeResult = balanceCharger.charge(new ChargeRequest(
                subscription.subscriberId(),
                service.price(),
                IdempotencyKeys.charge(subscription.id(), periodNo),
                "Subscription: " + service.name()
        ));

        db.inTransaction(() -> {
            if (chargeResult.status() == ChargeStatus.SUCCESS) {
                chargeRepository.insert(new SubscriptionCharge(
                        UUID.randomUUID(),
                        subscription.id(),
                        periodNo,
                        periodStart,
                        service.price(),
                        SubscriptionChargeStatus.SUCCESS,
                        chargeResult.ledgerEntryId(),
                        now
                ));
                subscription.recordPayment();
            } else {
                chargeRepository.insert(new SubscriptionCharge(
                        UUID.randomUUID(),
                        subscription.id(),
                        periodNo,
                        periodStart,
                        service.price(),
                        SubscriptionChargeStatus.FAILED,
                        null,
                        now
                ));
                subscription.suspend();
            }

            if (!subscriptionRepository.update(subscription, now)) {
                throw new IllegalStateException("Concurrent update on subscription " + subscription.id());
            }
        });

        return true;
    }

    public Optional<Subscription> findSubscription(UUID subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        return subscriptionRepository.findById(subscriptionId);
    }

    public List<Subscription> findSubscriptionsBySubscriber(UUID subscriberId) {
        Objects.requireNonNull(subscriberId, "subscriberId");
        return subscriptionRepository.findBySubscriberId(subscriberId);
    }

    public List<SubscriptionCharge> findChargesBySubscription(UUID subscriptionId) {
        Objects.requireNonNull(subscriptionId, "subscriptionId");
        return chargeRepository.findBySubscriptionId(subscriptionId);
    }
}
