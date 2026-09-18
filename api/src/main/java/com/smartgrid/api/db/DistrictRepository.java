package com.smartgrid.api.db;

import com.smartgrid.api.model.DistrictLive;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * LEFT JOIN (not INNER) so a district with no readings yet — e.g. right
 * after startup, before every meter has produced its first message —
 * still appears in the result with totalPowerKw = 0, instead of being
 * silently omitted.
 */
@Repository
public class DistrictRepository {

	private static final String SELECT_LIVE_TOTALS = """
			SELECT d.district_number, d.name, COALESCE(SUM(lr.power_consumption_kw), 0) AS total_power_kw
			FROM districts d
			LEFT JOIN latest_reading lr ON lr.district_id = d.id
			GROUP BY d.district_number, d.name
			ORDER BY d.district_number
			""";

	private final JdbcTemplate jdbcTemplate;

	public DistrictRepository(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	public List<DistrictLive> findLiveTotals() {
		return jdbcTemplate.query(SELECT_LIVE_TOTALS, (rs, rowNum) -> new DistrictLive(
				rs.getInt("district_number"),
				rs.getString("name"),
				rs.getDouble("total_power_kw")));
	}

}
