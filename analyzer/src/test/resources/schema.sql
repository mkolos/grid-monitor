-- Test-only subset of consumer's V1__create_schema.sql. The analyzer module
-- doesn't own migrations, so this must be kept in sync by hand.
-- Dropped first since @Sql reruns this script before every test method
-- against the same Testcontainers Postgres instance.
DROP TABLE IF EXISTS electricity_data;
DROP TABLE IF EXISTS latest_reading;
DROP TABLE IF EXISTS districts;

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
