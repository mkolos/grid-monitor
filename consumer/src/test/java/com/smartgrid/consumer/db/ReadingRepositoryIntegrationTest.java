package com.smartgrid.consumer.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrid.consumer.model.Reading;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
class ReadingRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@Autowired
	private ReadingRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void savesToHistoryAndUpsertsLatestReading() {
		Reading reading = new Reading("01-001", 1, 1.5, 230.0, 1000L);

		repository.save(reading);

		int historyCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM electricity_data WHERE meter_id = ?", Integer.class, "01-001");
		assertThat(historyCount).isEqualTo(1);

		Double latestPower = jdbcTemplate.queryForObject(
				"SELECT power_consumption_kw FROM latest_reading WHERE meter_id = ?", Double.class, "01-001");
		assertThat(latestPower).isEqualTo(1.5);
	}

	@Test
	void outOfOrderUpsertIsRejectedByTheTimestampGuard() {
		Reading newer = new Reading("01-002", 1, 3.0, 232.0, 5000L);
		Reading older = new Reading("01-002", 1, 9.9, 239.0, 1000L);

		repository.save(newer);
		repository.save(older);

		Double latestPower = jdbcTemplate.queryForObject(
				"SELECT power_consumption_kw FROM latest_reading WHERE meter_id = ?", Double.class, "01-002");
		assertThat(latestPower).isEqualTo(3.0);

		int historyCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM electricity_data WHERE meter_id = ?", Integer.class, "01-002");
		assertThat(historyCount).isEqualTo(2);
	}

}
