package com.smartgrid.analyzer.detection;

import com.smartgrid.analyzer.config.AnalyzerProperties;
import com.smartgrid.analyzer.model.AnomalyEvent;
import com.smartgrid.analyzer.model.AnomalyType;
import com.smartgrid.analyzer.model.MeterSnapshot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Deterministic and cheap by design, so an LLM is only ever used downstream
 * to explain what this class already found, never to do the finding itself.
 * No Spring types appear in its logic, so it's instantiated directly with
 * {@code new} in unit tests.
 */
@Component
public class AnomalyDetector {

	public List<AnomalyEvent> evaluate(MeterSnapshot snapshot, AnalyzerProperties properties) {
		List<AnomalyEvent> events = new ArrayList<>();

		checkVoltage(snapshot, properties).ifPresent(events::add);
		checkPower(snapshot, properties).ifPresent(events::add);

		return events;
	}

	private Optional<AnomalyEvent> checkVoltage(MeterSnapshot snapshot, AnalyzerProperties properties) {
		double tolerance = properties.voltageNominalV() * properties.voltageTolerancePct() / 100.0;
		double low = properties.voltageNominalV() - tolerance;
		double high = properties.voltageNominalV() + tolerance;

		if (snapshot.voltage() < low) {
			return Optional.of(new AnomalyEvent(
					snapshot.meterId(), snapshot.districtNumber(), snapshot.districtName(),
					AnomalyType.UNDER_VOLTAGE, snapshot.voltage(), low,
					"Voltage %.1fV is below the expected minimum of %.1fV".formatted(snapshot.voltage(), low)));
		}
		if (snapshot.voltage() > high) {
			return Optional.of(new AnomalyEvent(
					snapshot.meterId(), snapshot.districtNumber(), snapshot.districtName(),
					AnomalyType.OVER_VOLTAGE, snapshot.voltage(), high,
					"Voltage %.1fV is above the expected maximum of %.1fV".formatted(snapshot.voltage(), high)));
		}
		return Optional.empty();
	}

	private Optional<AnomalyEvent> checkPower(MeterSnapshot snapshot, AnalyzerProperties properties) {
		Double avg = snapshot.avgPowerKw();
		Double stddev = snapshot.stddevPowerKw();
		if (avg == null || stddev == null || stddev == 0.0) {
			return Optional.empty();
		}

		double zScore = (snapshot.powerKw() - avg) / stddev;
		double threshold = properties.powerZscoreThreshold();

		if (zScore > threshold) {
			return Optional.of(new AnomalyEvent(
					snapshot.meterId(), snapshot.districtNumber(), snapshot.districtName(),
					AnomalyType.POWER_SPIKE, snapshot.powerKw(), avg,
					"Power draw %.2fkW is %.1f standard deviations above its recent average of %.2fkW"
							.formatted(snapshot.powerKw(), zScore, avg)));
		}
		if (zScore < -threshold) {
			return Optional.of(new AnomalyEvent(
					snapshot.meterId(), snapshot.districtNumber(), snapshot.districtName(),
					AnomalyType.POWER_DROP, snapshot.powerKw(), avg,
					"Power draw %.2fkW is %.1f standard deviations below its recent average of %.2fkW"
							.formatted(snapshot.powerKw(), -zScore, avg)));
		}
		return Optional.empty();
	}

}
