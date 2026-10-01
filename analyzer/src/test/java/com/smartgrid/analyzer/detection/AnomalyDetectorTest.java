package com.smartgrid.analyzer.detection;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrid.analyzer.config.AnalyzerProperties;
import com.smartgrid.analyzer.model.AnomalyEvent;
import com.smartgrid.analyzer.model.AnomalyType;
import com.smartgrid.analyzer.model.MeterSnapshot;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class AnomalyDetectorTest {

	private static final AnalyzerProperties PROPERTIES = new AnalyzerProperties(
			Duration.ofSeconds(30), Duration.ofMinutes(15), 230.0, 10.0, 3.0, 5);

	private final AnomalyDetector detector = new AnomalyDetector();

	@Test
	void returnsNoEventsForANormalReading() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 230.0, 5.0, 5.0, 1.0);

		assertThat(detector.evaluate(snapshot, PROPERTIES)).isEmpty();
	}

	@Test
	void flagsUnderVoltage() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 200.0, 5.0, 5.0, 1.0);

		List<AnomalyEvent> events = detector.evaluate(snapshot, PROPERTIES);

		assertThat(events).extracting(AnomalyEvent::type).containsExactly(AnomalyType.UNDER_VOLTAGE);
	}

	@Test
	void flagsOverVoltage() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 260.0, 5.0, 5.0, 1.0);

		List<AnomalyEvent> events = detector.evaluate(snapshot, PROPERTIES);

		assertThat(events).extracting(AnomalyEvent::type).containsExactly(AnomalyType.OVER_VOLTAGE);
	}

	@Test
	void flagsAPowerSpikeWellAboveHistoricalAverage() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 230.0, 9.0, 5.0, 1.0);

		List<AnomalyEvent> events = detector.evaluate(snapshot, PROPERTIES);

		assertThat(events).extracting(AnomalyEvent::type).containsExactly(AnomalyType.POWER_SPIKE);
	}

	@Test
	void flagsAPowerDropWellBelowHistoricalAverage() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 230.0, 1.0, 5.0, 1.0);

		List<AnomalyEvent> events = detector.evaluate(snapshot, PROPERTIES);

		assertThat(events).extracting(AnomalyEvent::type).containsExactly(AnomalyType.POWER_DROP);
	}

	@Test
	void skipsThePowerCheckWhenThereIsNotEnoughHistoryButStillChecksVoltage() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 200.0, 500.0, null, null);

		List<AnomalyEvent> events = detector.evaluate(snapshot, PROPERTIES);

		assertThat(events).extracting(AnomalyEvent::type).containsExactly(AnomalyType.UNDER_VOLTAGE);
	}

	@Test
	void skipsThePowerCheckWhenHistoricalStddevIsZero() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 230.0, 50.0, 5.0, 0.0);

		assertThat(detector.evaluate(snapshot, PROPERTIES)).isEmpty();
	}

	@Test
	void canFlagBothVoltageAndPowerAnomaliesOnTheSameMeter() {
		MeterSnapshot snapshot = new MeterSnapshot("01-001", 1, "Innere Stadt", 260.0, 9.0, 5.0, 1.0);

		List<AnomalyEvent> events = detector.evaluate(snapshot, PROPERTIES);

		assertThat(events).extracting(AnomalyEvent::type)
				.containsExactlyInAnyOrder(AnomalyType.OVER_VOLTAGE, AnomalyType.POWER_SPIKE);
	}

}
