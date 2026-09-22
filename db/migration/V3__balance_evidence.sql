CREATE TABLE IF NOT EXISTS balance_evidence (
    account_last4 VARCHAR(4) NOT NULL,
    occurred_at VARCHAR(40) NOT NULL,
    stated_balance DECIMAL(14, 2) NOT NULL,
    source_message_id VARCHAR(200) PRIMARY KEY
);

CREATE INDEX IF NOT EXISTS idx_balance_account_time
    ON balance_evidence (account_last4, occurred_at);
