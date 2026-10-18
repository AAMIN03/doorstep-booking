CREATE TABLE services (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    duration_minutes INT NOT NULL,
    required_skill VARCHAR(32) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE zones (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL
) ENGINE=InnoDB;

CREATE TABLE zone_pincodes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    zone_id BIGINT NOT NULL,
    pincode VARCHAR(6) NOT NULL,
    CONSTRAINT uk_zone_pincodes_pincode UNIQUE (pincode),
    CONSTRAINT fk_zone_pincodes_zone FOREIGN KEY (zone_id) REFERENCES zones (id)
) ENGINE=InnoDB;

CREATE INDEX idx_zone_pincodes_zone ON zone_pincodes (zone_id);

CREATE TABLE technicians (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(128) NOT NULL,
    zone_id BIGINT NOT NULL,
    shift_start_minute INT NOT NULL,
    shift_end_minute INT NOT NULL,
    CONSTRAINT fk_technicians_zone FOREIGN KEY (zone_id) REFERENCES zones (id)
) ENGINE=InnoDB;

CREATE INDEX idx_technicians_zone ON technicians (zone_id);

CREATE TABLE technician_skills (
    technician_id BIGINT NOT NULL,
    skill VARCHAR(32) NOT NULL,
    PRIMARY KEY (technician_id, skill),
    CONSTRAINT fk_technician_skills_technician FOREIGN KEY (technician_id) REFERENCES technicians (id)
) ENGINE=InnoDB;

CREATE INDEX idx_technician_skills_skill ON technician_skills (skill);

CREATE TABLE bookings (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_phone VARCHAR(15) NOT NULL,
    service_id BIGINT NOT NULL,
    pincode VARCHAR(6) NOT NULL,
    start_time DATETIME(6) NOT NULL,
    end_time DATETIME(6) NOT NULL,
    technician_id BIGINT NOT NULL,
    status VARCHAR(16) NOT NULL,
    CONSTRAINT fk_bookings_service FOREIGN KEY (service_id) REFERENCES services (id),
    CONSTRAINT fk_bookings_technician FOREIGN KEY (technician_id) REFERENCES technicians (id)
) ENGINE=InnoDB;

CREATE INDEX idx_bookings_technician_start ON bookings (technician_id, start_time);
CREATE INDEX idx_bookings_pincode ON bookings (pincode);
CREATE INDEX idx_bookings_status_start ON bookings (status, start_time);

CREATE TABLE idempotency_records (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(128) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    booking_id BIGINT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key),
    CONSTRAINT fk_idempotency_booking FOREIGN KEY (booking_id) REFERENCES bookings (id)
) ENGINE=InnoDB;
