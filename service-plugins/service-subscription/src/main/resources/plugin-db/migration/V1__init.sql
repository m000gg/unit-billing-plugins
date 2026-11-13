CREATE TABLE service (
                         id          UUID PRIMARY KEY,
                         name        VARCHAR(200)  NOT NULL,
                         description TEXT          NOT NULL DEFAULT '',
                         price       NUMERIC(19,4) NOT NULL CHECK (price > 0),
                         period_unit VARCHAR(16)   NOT NULL DEFAULT 'MONTH' CHECK (period_unit IN ('MONTH')),
                         min_periods INT           NOT NULL DEFAULT 0 CHECK (min_periods >= 0),
                         status      VARCHAR(16)   NOT NULL CHECK (status IN ('ACTIVE', 'DELETED')),
                         created_at  TIMESTAMPTZ   NOT NULL DEFAULT now(),
                         updated_at  TIMESTAMPTZ   NOT NULL DEFAULT now()
);

CREATE TABLE subscription (
                              id                  UUID PRIMARY KEY,
                              subscriber_id       UUID         NOT NULL,
                              service_id          UUID         NOT NULL REFERENCES service (id),
                              status              VARCHAR(16)  NOT NULL
                                  CHECK (status IN ('ACTIVE', 'SUSPENDED', 'CANCELED')),
                              started_at          TIMESTAMPTZ,
                              min_term_ends_at    TIMESTAMPTZ,
                              ends_at             TIMESTAMPTZ,
                              anchor_at           TIMESTAMPTZ,
                              periods_since_anchor INT         NOT NULL DEFAULT 0 CHECK (periods_since_anchor >= 0),
                              next_period_no      INT          NOT NULL DEFAULT 1 CHECK (next_period_no >= 1),
                              next_billing_at     TIMESTAMPTZ,
                              cancel_at_period_end BOOLEAN     NOT NULL DEFAULT FALSE,
                              version             BIGINT       NOT NULL DEFAULT 0,
                              created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
                              updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_subscription_due ON subscription (status, next_billing_at);
CREATE INDEX idx_subscription_subscriber ON subscription (subscriber_id, status);
CREATE UNIQUE INDEX uq_subscription_live
    ON subscription (subscriber_id, service_id) WHERE status <> 'CANCELED';

CREATE TABLE subscription_charge (
                                     id              UUID PRIMARY KEY,
                                     subscription_id UUID          NOT NULL REFERENCES subscription (id),
                                     period_no       INT           NOT NULL CHECK (period_no >= 1),
                                     period_start    TIMESTAMPTZ   NOT NULL,
                                     amount          NUMERIC(19,4) NOT NULL CHECK (amount > 0),
                                     status          VARCHAR(16)   NOT NULL CHECK (status IN ('SUCCESS', 'FAILED')),
                                     failure_reason  VARCHAR(32),
                                     ledger_entry_id UUID,
                                     attempted_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                     CONSTRAINT chk_charge_success CHECK (status <> 'SUCCESS' OR ledger_entry_id IS NOT NULL),
                                     CONSTRAINT chk_charge_failed  CHECK (status <> 'FAILED'
                                         OR (failure_reason IS NOT NULL AND ledger_entry_id IS NULL))
);

CREATE UNIQUE INDEX uq_charge_success
    ON subscription_charge (subscription_id, period_no) WHERE status = 'SUCCESS';
CREATE INDEX idx_charge_subscription ON subscription_charge (subscription_id, attempted_at);