-- FleetIQ schema (PostgreSQL). Owned by Flyway; Hibernate does not alter it.

CREATE TABLE zones (
    id          VARCHAR(16) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL,
    latitude    DOUBLE PRECISION NOT NULL,
    longitude   DOUBLE PRECISION NOT NULL,
    map_x       INT NOT NULL,
    map_y       INT NOT NULL
);

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    email           VARCHAR(150) NOT NULL UNIQUE,
    phone           VARCHAR(15),
    license_number  VARCHAR(40),
    password_hash   VARCHAR(100) NOT NULL,
    role            VARCHAR(20)  NOT NULL CHECK (role IN ('CUSTOMER', 'ADMIN')),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE vehicles (
    id                     BIGSERIAL PRIMARY KEY,
    registration_number    VARCHAR(20)  NOT NULL UNIQUE,
    name                   VARCHAR(100) NOT NULL,
    type                   VARCHAR(20)  NOT NULL CHECK (type IN ('SUV', 'SEDAN', 'HATCHBACK', 'EV', 'BIKE')),
    seats                  INT NOT NULL,
    fuel                   VARCHAR(20) NOT NULL,
    transmission           VARCHAR(20) NOT NULL,
    base_rate              NUMERIC(10, 2) NOT NULL,
    zone_id                VARCHAR(16) NOT NULL REFERENCES zones (id),
    status                 VARCHAR(20) NOT NULL CHECK (status IN ('AVAILABLE', 'RENTED', 'MAINTENANCE')),
    odometer_km            INT NOT NULL,
    purchase_date          DATE NOT NULL,
    last_service_date      DATE NOT NULL,
    last_service_odometer  INT NOT NULL,
    health_score           INT,
    version                BIGINT NOT NULL DEFAULT 0
);
CREATE INDEX idx_vehicles_zone_status ON vehicles (zone_id, status);

CREATE TABLE bookings (
    id                  BIGSERIAL PRIMARY KEY,
    customer_id         BIGINT NOT NULL REFERENCES users (id),
    vehicle_id          BIGINT NOT NULL REFERENCES vehicles (id),
    start_time          TIMESTAMPTZ NOT NULL,
    end_time            TIMESTAMPTZ NOT NULL,
    actual_return_time  TIMESTAMPTZ,
    days                INT NOT NULL,
    base_rate           NUMERIC(10, 2) NOT NULL,
    multiplier          NUMERIC(4, 2)  NOT NULL,
    subtotal            NUMERIC(10, 2) NOT NULL,
    tax                 NUMERIC(10, 2) NOT NULL,
    total               NUMERIC(10, 2) NOT NULL,
    deposit             NUMERIC(10, 2) NOT NULL,
    late_fee            NUMERIC(10, 2) NOT NULL DEFAULT 0,
    status              VARCHAR(20) NOT NULL
        CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'ACTIVE', 'COMPLETED', 'CANCELLED')),
    payment_status      VARCHAR(20) NOT NULL CHECK (payment_status IN ('PENDING', 'PAID', 'REFUNDED', 'FAILED')),
    hold_expires_at     TIMESTAMPTZ,
    late_notified       BOOLEAN NOT NULL DEFAULT FALSE,
    returned_late       BOOLEAN NOT NULL DEFAULT FALSE,
    damage_reported     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_booking_range CHECK (end_time > start_time)
);
ALTER SEQUENCE bookings_id_seq RESTART WITH 1001;
CREATE INDEX idx_bookings_vehicle_range ON bookings (vehicle_id, start_time, end_time);
CREATE INDEX idx_bookings_customer ON bookings (customer_id);
CREATE INDEX idx_bookings_status_end ON bookings (status, end_time);

CREATE TABLE payments (
    id                BIGSERIAL PRIMARY KEY,
    reference         VARCHAR(40) NOT NULL UNIQUE,
    booking_id        BIGINT NOT NULL REFERENCES bookings (id),
    amount            NUMERIC(10, 2) NOT NULL,
    method            VARCHAR(20) NOT NULL,
    status            VARCHAR(20) NOT NULL CHECK (status IN ('CREATED', 'SUCCESS', 'FAILED', 'REFUNDED')),
    gateway_order_id  VARCHAR(60),
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_payments_booking ON payments (booking_id);
CREATE INDEX idx_payments_status_created ON payments (status, created_at);

CREATE TABLE maintenance_records (
    id                             BIGSERIAL PRIMARY KEY,
    vehicle_id                     BIGINT NOT NULL REFERENCES vehicles (id),
    component                      VARCHAR(60) NOT NULL,
    predicted_failure_probability  DOUBLE PRECISION,
    scheduled_for                  DATE NOT NULL,
    status                         VARCHAR(20) NOT NULL CHECK (status IN ('SCHEDULED', 'COMPLETED')),
    completed_at                   TIMESTAMPTZ,
    created_at                     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_maintenance_vehicle ON maintenance_records (vehicle_id, status);

-- Raw search log: training data for the demand model
CREATE TABLE search_events (
    id            BIGSERIAL PRIMARY KEY,
    user_id       BIGINT,
    zone_id       VARCHAR(16),
    vehicle_type  VARCHAR(20),
    start_time    TIMESTAMPTZ,
    end_time      TIMESTAMPTZ,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_search_events_created ON search_events (created_at);

CREATE TABLE allocation_moves (
    id             BIGSERIAL PRIMARY KEY,
    vehicle_id     BIGINT NOT NULL REFERENCES vehicles (id),
    from_zone_id   VARCHAR(16) NOT NULL REFERENCES zones (id),
    to_zone_id     VARCHAR(16) NOT NULL REFERENCES zones (id),
    expected_gain  NUMERIC(10, 2) NOT NULL,
    reason         VARCHAR(255) NOT NULL,
    status         VARCHAR(20) NOT NULL CHECK (status IN ('SUGGESTED', 'DISPATCHED')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE notifications (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT REFERENCES users (id),
    audience    VARCHAR(10) NOT NULL CHECK (audience IN ('USER', 'ADMINS')),
    type        VARCHAR(30) NOT NULL,
    message     VARCHAR(500) NOT NULL,
    is_read     BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user ON notifications (user_id, created_at DESC);
CREATE INDEX idx_notifications_audience ON notifications (audience, created_at DESC);
