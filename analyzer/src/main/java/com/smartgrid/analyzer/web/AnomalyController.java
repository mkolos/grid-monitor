package com.smartgrid.analyzer.web;

import com.smartgrid.analyzer.detection.LatestAnomalyReport;
import com.smartgrid.analyzer.model.AnomalyReport;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AnomalyController {

	private final LatestAnomalyReport latestReport;

	public AnomalyController(LatestAnomalyReport latestReport) {
		this.latestReport = latestReport;
	}

	@GetMapping("/anomalies")
	public AnomalyReport anomalies() {
		return latestReport.get();
	}

}
