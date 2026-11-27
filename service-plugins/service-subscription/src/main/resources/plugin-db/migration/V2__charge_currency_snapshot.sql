ALTER TABLE subscription_charge
    ADD COLUMN charged_amount   NUMERIC(19, 2) CHECK (charged_amount IS NULL OR charged_amount > 0),
    ADD COLUMN charged_currency VARCHAR(16),
    ADD COLUMN exchange_rate    NUMERIC(19, 6) CHECK (exchange_rate IS NULL OR exchange_rate > 0);
