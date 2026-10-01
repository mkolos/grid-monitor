package com.smartgrid.analyzer.model;

import java.time.Instant;
import java.util.List;

public record AnomalyReport(
		Instant generatedAt,
		List<AnomalyEvent> events,
		String narrative) {

	public static AnomalyReport unscanned() {
		return new AnomalyReport(null, List.of(), "");
	}

}
