package com.smartgrid.analyzer.db;

import com.smartgrid.analyzer.model.MeterSnapshot;
import java.time.Duration;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * LEFT JOIN against the historical aggregate so a meter with less than
 * {@code minHistorySamples} readings in the window still appears in the
 * result with null avg/stddev, instead of being silently dropped — the
 * detector treats that as "not enough history yet" rather than "normal".
 */
@Repository
public class AnomalyReadingRepository {

	private static final String SELECT_SNAPSHOTS = """
			SELECT lr.meter_id, d.district_number, d.name AS district_name,
			       lr.voltage, lr.power_consumption_kw,
			       hist.avg_power, hist.stddev_power
			FROM latest_reading lr
			JOIN districts d ON d.id = lr.district_id
			LEFT JOIN (
			    SELECT meter_id,
			           AVG(power_consumption_kw) AS avg_power,
			           STDDEV_POP(power_consumption_kw) AS stddev_power
			    FROM electricity_data
			    WHERE timestamp > ?
			    GROUP BY meter_id
			    HAVING COUNT(*) >= ?
			) hist ON hist.meter_id = lr.meter_id
			""";

	private final JdbcTemplate jdbcTemplate;

	public AnomalyReadingRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<MeterSnapshot> findSnapshots(Duration historyWindow, int minHistorySamples) {
		long windowStart = System.currentTimeMillis() - historyWindow.toMillis();
		return jdbcTemplate.query(SELECT_SNAPSHOTS,
				(rs, rowNum) -> new MeterSnapshot(
						rs.getString("meter_id"),
						rs.getInt("district_number"),
						rs.getString("district_name"),
						rs.getDouble("voltage"),
						rs.getDouble("power_consumption_kw"),
						rs.getObject("avg_power", Double.class),
						rs.getObject("stddev_power", Double.class)),
				windowStart, minHistorySamples);
	}

}
