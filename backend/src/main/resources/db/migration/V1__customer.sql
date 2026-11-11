-- Customer module: customer information file (CIF).
CREATE SEQUENCE cif_no_seq START WITH 1000001;

CREATE TABLE customer (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cif_no     VARCHAR(20)  NOT NULL,
    full_name  VARCHAR(150) NOT NULL,
    phone      VARCHAR(20),
    email      VARCHAR(150),
    status     VARCHAR(20)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_customer_cif_no UNIQUE (cif_no),
    CONSTRAINT ck_customer_status CHECK (status IN ('ACTIVE', 'BLOCKED'))
);
