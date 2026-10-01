package com.smartgrid.analyzer.model;

public record MeterSnapshot(
		String meterId,
		int districtNumber,
		String districtName,
		double voltage,
		double powerKw,
		Double avgPowerKw,
		Double stddevPowerKw) {
}
