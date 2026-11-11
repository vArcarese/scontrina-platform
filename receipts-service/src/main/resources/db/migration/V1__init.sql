-- Copia locale delle abilitazioni (alimentata dagli eventi di Account)
CREATE TABLE organization_view
(
    organization_id CHAR(36)    NOT NULL PRIMARY KEY,
    status          VARCHAR(20) NOT NULL,
    source_version  BIGINT      NOT NULL
);

CREATE TABLE entitlement_view
(
    organization_id CHAR(36)    NOT NULL,
    product         VARCHAR(30) NOT NULL,
    status          VARCHAR(20) NOT NULL,
    valid_from      DATE        NOT NULL,
    valid_to        DATE        NULL,
    source_version  BIGINT      NOT NULL,
    PRIMARY KEY (organization_id, product)
);

-- Numerazione progressiva per punto cassa
CREATE TABLE register_counter
(
    organization_id CHAR(36)    NOT NULL,
    register_id     VARCHAR(50) NOT NULL,
    last_number     BIGINT      NOT NULL,
    PRIMARY KEY (organization_id, register_id)
);

CREATE TABLE receipt
(
    id                CHAR(36)       NOT NULL PRIMARY KEY,
    organization_id   CHAR(36)       NOT NULL,
    register_id       VARCHAR(50)    NOT NULL,
    number            BIGINT         NOT NULL,
    type              VARCHAR(10)    NOT NULL,
    voided_receipt_id CHAR(36)       NULL,
    payment_type      VARCHAR(10)    NOT NULL,
    customer_id       VARCHAR(64)    NULL,
    business_date     DATE           NOT NULL,
    issued_at         DATETIME(6)    NOT NULL,
    total             DECIMAL(12, 2) NOT NULL,
    vat_total         DECIMAL(12, 2) NOT NULL,
    -- rete di sicurezza: anche con un bug nel locking, mai due documenti con lo stesso numero
    CONSTRAINT uk_receipt_number UNIQUE (organization_id, register_id, number),
    -- un documento si annulla una volta sola
    CONSTRAINT uk_receipt_voided UNIQUE (voided_receipt_id),
    CONSTRAINT fk_receipt_voided FOREIGN KEY (voided_receipt_id) REFERENCES receipt (id),
    INDEX idx_receipt_closure (organization_id, register_id, business_date)
);

CREATE TABLE receipt_line
(
    receipt_id  CHAR(36)       NOT NULL,
    line_no     INT            NOT NULL,
    description VARCHAR(255)   NOT NULL,
    quantity    DECIMAL(12, 3) NOT NULL,
    unit_price  DECIMAL(12, 2) NOT NULL,
    vat_rate    VARCHAR(10)    NOT NULL,
    line_total  DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (receipt_id, line_no),
    CONSTRAINT fk_line_receipt FOREIGN KEY (receipt_id) REFERENCES receipt (id)
);

CREATE TABLE receipt_vat
(
    receipt_id CHAR(36)       NOT NULL,
    position   INT            NOT NULL,
    vat_rate   VARCHAR(10)    NOT NULL,
    gross      DECIMAL(12, 2) NOT NULL,
    taxable    DECIMAL(12, 2) NOT NULL,
    vat        DECIMAL(12, 2) NOT NULL,
    PRIMARY KEY (receipt_id, position),
    CONSTRAINT fk_vat_receipt FOREIGN KEY (receipt_id) REFERENCES receipt (id)
);

-- Idempotenza dell'emissione
CREATE TABLE idempotency_record
(
    organization_id CHAR(36)    NOT NULL,
    idempotency_key VARCHAR(64) NOT NULL,
    request_hash    CHAR(64)    NOT NULL,
    receipt_id      CHAR(36)    NOT NULL,
    created_at      DATETIME(6) NOT NULL,
    PRIMARY KEY (organization_id, idempotency_key),
    CONSTRAINT fk_idem_receipt FOREIGN KEY (receipt_id) REFERENCES receipt (id)
);

-- Chiusure giornaliere e stato della trasmissione
CREATE TABLE daily_closure
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id CHAR(36)       NOT NULL,
    register_id     VARCHAR(50)    NOT NULL,
    business_date   DATE           NOT NULL,
    transmission_id CHAR(36)       NOT NULL,
    receipt_count   INT            NOT NULL,
    first_number    BIGINT         NULL,
    last_number     BIGINT         NULL,
    total           DECIMAL(14, 2) NOT NULL,
    status          VARCHAR(20)    NOT NULL,
    attempts        INT            NOT NULL,
    next_attempt_at DATETIME(6)    NOT NULL,
    lease_until     DATETIME(6)    NULL,
    last_error      VARCHAR(1000)  NULL,
    external_ref    VARCHAR(100)   NULL,
    transmitted_at  DATETIME(6)    NULL,
    created_at      DATETIME(6)    NOT NULL,
    updated_at      DATETIME(6)    NOT NULL,
    CONSTRAINT uk_closure_register_day UNIQUE (organization_id, register_id, business_date),
    CONSTRAINT uk_closure_transmission UNIQUE (transmission_id),
    INDEX idx_closure_pending (status, next_attempt_at)
);

CREATE TABLE outbox_event
(
    id             CHAR(36)     NOT NULL PRIMARY KEY,
    topic          VARCHAR(100) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    correlation_id VARCHAR(64)  NULL,
    created_at     DATETIME(6)  NOT NULL,
    published_at   DATETIME(6)  NULL,
    INDEX idx_outbox_pending (published_at, created_at)
);
