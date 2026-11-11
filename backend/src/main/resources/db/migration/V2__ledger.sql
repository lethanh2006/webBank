-- Ledger module: accounts, double-entry journal and postings.
--
-- Sign convention: balance = sum(credits) - sum(debits) for every account. Customer deposits are
-- liabilities of the bank, so they are positive; internal asset accounts such as the cash vault
-- go negative. With one convention for all accounts the global invariant is simply:
--     SELECT sum(ledger_balance) FROM account  -- always 0

CREATE SEQUENCE account_no_seq START WITH 1;

CREATE TABLE account (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    account_no        VARCHAR(20)   NOT NULL,
    customer_id       BIGINT,       -- owned by the customer module: no cross-module foreign key
    name              VARCHAR(150)  NOT NULL,
    type              VARCHAR(20)   NOT NULL,
    currency          VARCHAR(3)    NOT NULL,
    status            VARCHAR(20)   NOT NULL,
    ledger_balance    NUMERIC(19,4) NOT NULL DEFAULT 0,
    available_balance NUMERIC(19,4) NOT NULL DEFAULT 0,
    version           BIGINT        NOT NULL DEFAULT 0,
    created_at        TIMESTAMPTZ   NOT NULL,
    updated_at        TIMESTAMPTZ   NOT NULL,
    CONSTRAINT uq_account_no UNIQUE (account_no),
    CONSTRAINT ck_account_type CHECK (type IN ('CURRENT', 'INTERNAL')),
    CONSTRAINT ck_account_status CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    CONSTRAINT ck_account_owner CHECK ((type = 'INTERNAL') = (customer_id IS NULL)),
    -- Last line of defence: a customer account never goes below zero, even if the code has a bug.
    CONSTRAINT ck_account_no_overdraft CHECK (type = 'INTERNAL' OR available_balance >= 0)
);

CREATE INDEX ix_account_customer ON account (customer_id) WHERE customer_id IS NOT NULL;

CREATE TABLE journal_entry (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference      VARCHAR(40)  NOT NULL,
    type           VARCHAR(30)  NOT NULL,
    description    VARCHAR(255) NOT NULL,
    business_date  DATE         NOT NULL,
    reversal_of_id BIGINT       REFERENCES journal_entry (id),
    created_at     TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_journal_entry_reference UNIQUE (reference)
);

-- An entry can be reversed at most once.
CREATE UNIQUE INDEX uq_journal_entry_reversal ON journal_entry (reversal_of_id) WHERE reversal_of_id IS NOT NULL;

CREATE TABLE posting (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    journal_entry_id BIGINT        NOT NULL REFERENCES journal_entry (id),
    account_id       BIGINT        NOT NULL REFERENCES account (id),
    direction        VARCHAR(6)    NOT NULL,
    amount           NUMERIC(19,4) NOT NULL,
    currency         VARCHAR(3)    NOT NULL,
    balance_after    NUMERIC(19,4) NOT NULL,
    created_at       TIMESTAMPTZ   NOT NULL,
    CONSTRAINT ck_posting_direction CHECK (direction IN ('DEBIT', 'CREDIT')),
    CONSTRAINT ck_posting_amount_positive CHECK (amount > 0)
);

-- Account statement, newest first, keyset pagination on id.
CREATE INDEX ix_posting_account_history ON posting (account_id, id DESC);
CREATE INDEX ix_posting_journal_entry ON posting (journal_entry_id);

-- Journal entries and postings are immutable: mistakes are corrected with a reversal entry.
CREATE FUNCTION forbid_update_and_delete() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'Table % is append-only: % is not allowed', TG_TABLE_NAME, TG_OP;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_journal_entry_append_only
    BEFORE UPDATE OR DELETE ON journal_entry
    FOR EACH ROW EXECUTE FUNCTION forbid_update_and_delete();

CREATE TRIGGER trg_posting_append_only
    BEFORE UPDATE OR DELETE ON posting
    FOR EACH ROW EXECUTE FUNCTION forbid_update_and_delete();

-- Every journal entry must balance (debits = credits per currency). The check is deferred to
-- COMMIT, when all postings of the entry exist, so the database enforces double entry even if
-- the application code is wrong.
CREATE FUNCTION check_journal_entry_balanced() RETURNS trigger AS $$
DECLARE
    unbalanced_currency VARCHAR(3);
BEGIN
    SELECT currency INTO unbalanced_currency
    FROM posting
    WHERE journal_entry_id = NEW.journal_entry_id
    GROUP BY currency
    HAVING sum(CASE WHEN direction = 'CREDIT' THEN amount ELSE -amount END) <> 0
    LIMIT 1;

    IF unbalanced_currency IS NOT NULL THEN
        RAISE EXCEPTION 'Journal entry % is not balanced in %', NEW.journal_entry_id, unbalanced_currency;
    END IF;
    RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE CONSTRAINT TRIGGER trg_posting_balanced
    AFTER INSERT ON posting
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION check_journal_entry_balanced();

-- Internal (general ledger) accounts.
INSERT INTO account (account_no, customer_id, name, type, currency, status, created_at, updated_at)
VALUES ('GL-CASH-VND', NULL, 'Quỹ tiền mặt VND', 'INTERNAL', 'VND', 'ACTIVE', now(), now());
