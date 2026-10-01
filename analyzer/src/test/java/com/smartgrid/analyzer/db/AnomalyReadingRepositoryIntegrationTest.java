package com.smartgrid.analyzer.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import com.smartgrid.analyzer.model.MeterSnapshot;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@Sql("/schema.sql")
class AnomalyReadingRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@Autowired
	private AnomalyReadingRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void computesHistoricalStatsFromRecentReadingsOnlyAndIgnoresOlderOnes() {
		jdbcTemplate.update("INSERT INTO districts (id, district_number, name) VALUES (1, 1, 'Innere Stadt')");
		jdbcTemplate.update("""
				INSERT INTO latest_reading (meter_id, district_id, power_consumption_kw, voltage, timestamp)
				VALUES ('01-001', 1, 5.0, 230.0, ?)
				""", System.currentTimeMillis());

		long now = System.currentTimeMillis();
		insertReading("01-001", 1, 4.0, now);
		insertReading("01-001", 1, 5.0, now);
		insertReading("01-001", 1, 6.0, now);
		insertReading("01-001", 1, 999.0, now - Duration.ofMinutes(30).toMillis());

		List<MeterSnapshot> snapshots = repository.findSnapshots(Duration.ofMinutes(15), 3);

		assertThat(snapshots).hasSize(1);
		MeterSnapshot snapshot = snapshots.get(0);
		assertThat(snapshot.meterId()).isEqualTo("01-001");
		assertThat(snapshot.districtNumber()).isEqualTo(1);
		assertThat(snapshot.avgPowerKw()).isCloseTo(5.0, within(0.001));
		assertThat(snapshot.stddevPowerKw()).isCloseTo(0.8165, within(0.001));
	}

	@Test
	void returnsNullStatsWhenThereAreFewerThanTheMinimumHistorySamples() {
		jdbcTemplate.update("INSERT INTO districts (id, district_number, name) VALUES (1, 1, 'Innere Stadt')");
		jdbcTemplate.update("""
				INSERT INTO latest_reading (meter_id, district_id, power_consumption_kw, voltage, timestamp)
				VALUES ('01-002', 1, 5.0, 230.0, ?)
				""", System.currentTimeMillis());
		insertReading("01-002", 1, 4.0, System.currentTimeMillis());

		List<MeterSnapshot> snapshots = repository.findSnapshots(Duration.ofMinutes(15), 3);

		assertThat(snapshots).hasSize(1);
		MeterSnapshot snapshot = snapshots.get(0);
		assertThat(snapshot.avgPowerKw()).isNull();
		assertThat(snapshot.stddevPowerKw()).isNull();
	}

	private void insertReading(String meterId, int districtId, double powerKw, long timestamp) {
		jdbcTemplate.update("""
				INSERT INTO electricity_data (meter_id, district_id, power_consumption_kw, voltage, timestamp)
				VALUES (?, ?, ?, 230.0, ?)
				""", meterId, districtId, powerKw, timestamp);
	}

}
