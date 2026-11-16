package com.m000gg.plugins.servicesubscription.infrastructure;

import com.m000gg.plugin.api.db.PluginDatabase;
import com.m000gg.plugin.api.db.RowView;
import com.m000gg.plugins.servicesubscription.domain.PeriodUnit;
import com.m000gg.plugins.servicesubscription.domain.Service;
import com.m000gg.plugins.servicesubscription.domain.ServiceStatus;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ServiceRepository {

    private final PluginDatabase db;

    public ServiceRepository(PluginDatabase db) {
        this.db = Objects.requireNonNull(db, "db");
    }

    public void insert(Service service, Instant now) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(now, "now");
        db.execute(
                """
                INSERT INTO service (id, name, description, price, period_unit, min_periods, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                service.id(),
                service.name(),
                service.description(),
                service.price(),
                service.period().name(),
                service.minPeriods(),
                service.serviceStatus().name(),
                now,
                now
        );
    }

    public Optional<Service> findById(UUID id) {
        Objects.requireNonNull(id, "id");
        return db.queryOne(
                """
                SELECT id, name, description, price, period_unit, min_periods, status
                FROM service
                WHERE id = ?
                """,
                ServiceRepository::mapRow,
                id
        );
    }

    public List<Service> findAllActive() {
        return db.query(
                """
                SELECT id, name, description, price, period_unit, min_periods, status
                FROM service
                WHERE status = ?
                ORDER BY created_at ASC
                """,
                ServiceRepository::mapRow,
                ServiceStatus.ACTIVE.name()
        );
    }

    public List<Service> findAll() {
        return db.query(
                """
                SELECT id, name, description, price, period_unit, min_periods, status
                FROM service
                ORDER BY created_at ASC
                """,
                ServiceRepository::mapRow
        );
    }

    public boolean update(Service service, Instant now) {
        Objects.requireNonNull(service, "service");
        Objects.requireNonNull(now, "now");
        int updated = db.execute(
                """
                UPDATE service
                SET name = ?, description = ?, min_periods = ?, status = ?, updated_at = ?
                WHERE id = ?
                """,
                service.name(),
                service.description(),
                service.minPeriods(),
                service.serviceStatus().name(),
                now,
                service.id()
        );
        return updated == 1;
    }

    private static Service mapRow(RowView row) {
        return Service.fromDb(
                row.getUuid("id"),
                row.getString("name"),
                row.getString("description"),
                row.getBigDecimal("price"),
                ServiceStatus.valueOf(row.getString("status")),
                row.getInt("min_periods"),
                PeriodUnit.valueOf(row.getString("period_unit"))
        );
    }
}
