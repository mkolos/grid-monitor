CREATE TABLE districts (
    id INT PRIMARY KEY,
    district_number INT NOT NULL UNIQUE,
    name VARCHAR(100) NOT NULL
);

CREATE TABLE electricity_data (
    id BIGSERIAL PRIMARY KEY,
    meter_id VARCHAR(20) NOT NULL,
    district_id INT NOT NULL REFERENCES districts (id),
    power_consumption_kw DOUBLE PRECISION NOT NULL,
    voltage DOUBLE PRECISION NOT NULL,
    timestamp BIGINT NOT NULL
);

CREATE TABLE latest_reading (
    meter_id VARCHAR(20) PRIMARY KEY,
    district_id INT NOT NULL REFERENCES districts (id),
    power_consumption_kw DOUBLE PRECISION NOT NULL,
    voltage DOUBLE PRECISION NOT NULL,
    timestamp BIGINT NOT NULL
);
