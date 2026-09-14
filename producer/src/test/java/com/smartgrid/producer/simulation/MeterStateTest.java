package com.smartgrid.producer.simulation;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrid.producer.model.Reading;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class MeterStateTest {

	@Test
	void reportsGivenMeterIdAndDistrict() {
		MeterState meter = MeterState.startingAt("01-001", 1);

		Reading reading = meter.nextReading();

		assertThat(reading.meterId()).isEqualTo("01-001");
		assertThat(reading.district()).isEqualTo(1);
	}

	@RepeatedTest(20)
	void keepsValuesWithinRealisticBoundsUnderRepeatedTicks() {
		MeterState meter = MeterState.startingAt("05-042", 5);

		for (int i = 0; i < 200; i++) {
			Reading reading = meter.nextReading();
			assertThat(reading.powerConsumptionKW()).isBetween(0.05, 6.0);
			assertThat(reading.voltage()).isBetween(220.0, 240.0);
		}
	}

}
