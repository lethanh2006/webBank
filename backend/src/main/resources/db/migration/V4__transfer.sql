-- Transfer module: the business document of a money transfer. Money movement itself lives in
-- the ledger (journal_entry.reference = transfer.reference).
CREATE SEQUENCE transfer_ref_seq START WITH 1;

CREATE TABLE transfer (
    id                     BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reference              VARCHAR(30)   NOT NULL,
    customer_id            BIGINT        NOT NULL,
    type                   VARCHAR(20)   NOT NULL,
    status                 VARCHAR(20)   NOT NULL,
    source_account_no      VARCHAR(20)   NOT NULL,
    destination_account_no VARCHAR(20)   NOT NULL,
    destination_name       VARCHAR(150)  NOT NULL,
    amount                 NUMERIC(19,4) NOT NULL,
    currency               VARCHAR(3)    NOT NULL,
    description            VARCHAR(140)  NOT NULL,
    created_at             TIMESTAMPTZ   NOT NULL,
    completed_at           TIMESTAMPTZ,
    version                BIGINT        NOT NULL DEFAULT 0,
    CONSTRAINT uq_transfer_reference UNIQUE (reference),
    CONSTRAINT ck_transfer_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_transfer_type CHECK (type IN ('INTERNAL')),
    CONSTRAINT ck_transfer_status CHECK (status IN ('COMPLETED'))
);

CREATE INDEX ix_transfer_customer ON transfer (customer_id, id DESC);
