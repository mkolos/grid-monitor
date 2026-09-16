package com.smartgrid.consumer.db;

import com.smartgrid.consumer.model.Reading;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes go through plain JdbcTemplate, not JPA, so the SQL stays visible
 * and there's no ORM session/thread-affinity to reason about across the
 * worker pool. {@code district} is used directly as {@code district_id}
 * because districts are seeded with id == district_number (see
 * V2__seed_districts.sql) — no lookup query needed per write.
 */
@Repository
public class ReadingRepository {

	private static final String INSERT_HISTORY = """
			INSERT INTO electricity_data (meter_id, district_id, power_consumption_kw, voltage, timestamp)
			VALUES (?, ?, ?, ?, ?)
			""";

	private static final String UPSERT_LATEST = """
			INSERT INTO latest_reading (meter_id, district_id, power_consumption_kw, voltage, timestamp)
			VALUES (?, ?, ?, ?, ?)
			ON CONFLICT (meter_id) DO UPDATE SET
				district_id = excluded.district_id,
				power_consumption_kw = excluded.power_consumption_kw,
				voltage = excluded.voltage,
				timestamp = excluded.timestamp
			WHERE excluded.timestamp > latest_reading.timestamp
			""";

	private final JdbcTemplate jdbcTemplate;

	public ReadingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	@Transactional
	public void save(Reading reading) {
		jdbcTemplate.update(INSERT_HISTORY,
				reading.meterId(), reading.district(), reading.powerConsumptionKW(), reading.voltage(), reading.timestamp());
		jdbcTemplate.update(UPSERT_LATEST,
				reading.meterId(), reading.district(), reading.powerConsumptionKW(), reading.voltage(), reading.timestamp());
	}

}
