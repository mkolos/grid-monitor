package com.smartgrid.api.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.smartgrid.api.model.DistrictLive;
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
class DistrictRepositoryIntegrationTest {

	@Container
	@ServiceConnection
	static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

	@Autowired
	private DistrictRepository repository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Test
	void sumsPowerPerDistrictAndIncludesDistrictsWithNoReadings() {
		jdbcTemplate.update("INSERT INTO districts (id, district_number, name) VALUES (1, 1, 'Innere Stadt')");
		jdbcTemplate.update("INSERT INTO districts (id, district_number, name) VALUES (2, 2, 'Leopoldstadt')");
		jdbcTemplate.update("""
				INSERT INTO latest_reading (meter_id, district_id, power_consumption_kw, voltage, timestamp)
				VALUES ('01-001', 1, 1.5, 230.0, 1000)
				""");
		jdbcTemplate.update("""
				INSERT INTO latest_reading (meter_id, district_id, power_consumption_kw, voltage, timestamp)
				VALUES ('01-002', 1, 2.5, 231.0, 1000)
				""");

		List<DistrictLive> result = repository.findLiveTotals();

		assertThat(result).extracting(DistrictLive::districtNumber, DistrictLive::name, DistrictLive::totalPowerKw)
				.containsExactly(
						tuple(1, "Innere Stadt", 4.0),
						tuple(2, "Leopoldstadt", 0.0));
	}

}
