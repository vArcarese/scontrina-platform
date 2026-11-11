CREATE TABLE organization
(
    id         CHAR(36)     NOT NULL PRIMARY KEY,
    vat_number VARCHAR(11)  NOT NULL,
    legal_name VARCHAR(255) NOT NULL,
    status     VARCHAR(20)  NOT NULL,
    version    BIGINT       NOT NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_organization_vat UNIQUE (vat_number)
);

CREATE TABLE entitlement
(
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    organization_id CHAR(36)     NOT NULL,
    product         VARCHAR(30)  NOT NULL,
    status          VARCHAR(20)  NOT NULL,
    valid_from      DATE         NOT NULL,
    valid_to        DATE         NULL,
    version         BIGINT       NOT NULL,
    updated_at      DATETIME(6) NOT NULL,
    CONSTRAINT uk_entitlement_org_product UNIQUE (organization_id, product),
    CONSTRAINT fk_entitlement_org FOREIGN KEY (organization_id) REFERENCES organization (id)
);

CREATE TABLE outbox_event
(
    id             CHAR(36)     NOT NULL PRIMARY KEY,
    topic          VARCHAR(100) NOT NULL,
    message_key    VARCHAR(100) NOT NULL,
    event_type     VARCHAR(100) NOT NULL,
    payload        JSON         NOT NULL,
    correlation_id VARCHAR(64)  NULL,
    created_at     DATETIME(6) NOT NULL,
    published_at   DATETIME(6) NULL,
    INDEX idx_outbox_pending (published_at, created_at)
);
