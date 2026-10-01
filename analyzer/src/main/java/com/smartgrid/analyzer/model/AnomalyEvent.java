package com.smartgrid.analyzer.model;

public record AnomalyEvent(
		String meterId,
		int districtNumber,
		String districtName,
		AnomalyType type,
		double observedValue,
		double referenceValue,
		String description) {
}
