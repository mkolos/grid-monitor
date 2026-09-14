package com.smartgrid.producer.model;

public record Reading(
		String meterId,
		int district,
		double powerConsumptionKW,
		double voltage,
		long timestamp) {
}
