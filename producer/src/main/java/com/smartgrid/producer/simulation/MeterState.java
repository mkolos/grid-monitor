package com.smartgrid.producer.simulation;

import com.smartgrid.producer.model.Reading;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Mutable per-meter state. Deliberately not thread-safe: a given instance is
 * only ever ticked by one thread at a time, because it is driven by a single
 * fixed-rate scheduled task that never overlaps itself.
 */
final class MeterState {

	private static final double MIN_POWER_KW = 0.05;
	private static final double MAX_POWER_KW = 6.0;
	private static final double MIN_VOLTAGE = 220.0;
	private static final double MAX_VOLTAGE = 240.0;
	private static final double NOMINAL_VOLTAGE = 230.0;

	private final String meterId;
	private final int districtNumber;
	private double powerKw;
	private double voltage;

	private MeterState(String meterId, int districtNumber, double powerKw, double voltage) {
		this.meterId = meterId;
		this.districtNumber = districtNumber;
		this.powerKw = powerKw;
		this.voltage = voltage;
	}

	static MeterState startingAt(String meterId, int districtNumber) {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		double initialPowerKw = random.nextDouble(0.2, 4.0);
		double initialVoltage = NOMINAL_VOLTAGE + random.nextDouble(-2.0, 2.0);
		return new MeterState(meterId, districtNumber, initialPowerKw, initialVoltage);
	}

	String meterId() {
		return meterId;
	}

	Reading nextReading() {
		ThreadLocalRandom random = ThreadLocalRandom.current();
		powerKw = clamp(powerKw + random.nextGaussian() * 0.05, MIN_POWER_KW, MAX_POWER_KW);
		voltage = clamp(voltage + random.nextGaussian() * 0.3, MIN_VOLTAGE, MAX_VOLTAGE);
		return new Reading(meterId, districtNumber, round2(powerKw), round2(voltage), System.currentTimeMillis());
	}

	private static double clamp(double value, double min, double max) {
		return Math.max(min, Math.min(max, value));
	}

	private static double round2(double value) {
		return Math.round(value * 100.0) / 100.0;
	}

}
