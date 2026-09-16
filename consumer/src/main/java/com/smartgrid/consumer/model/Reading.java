package com.smartgrid.consumer.model;

public record Reading(
		String meterId,
		int district,
		double powerConsumptionKW,
		double voltage,
		long timestamp) {
}
