package com.smartgrid.analyzer.detection;

import com.smartgrid.analyzer.config.AnalyzerProperties;
import com.smartgrid.analyzer.db.AnomalyReadingRepository;
import com.smartgrid.analyzer.model.AnomalyEvent;
import com.smartgrid.analyzer.model.AnomalyReport;
import com.smartgrid.analyzer.model.MeterSnapshot;
import com.smartgrid.analyzer.narration.AnomalyNarrator;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AnomalyScanner {

	private final AnomalyReadingRepository repository;
	private final AnomalyDetector detector;
	private final AnomalyNarrator narrator;
	private final LatestAnomalyReport latestReport;
	private final AnalyzerProperties properties;

	public AnomalyScanner(
			AnomalyReadingRepository repository,
			AnomalyDetector detector,
			AnomalyNarrator narrator,
			LatestAnomalyReport latestReport,
			AnalyzerProperties properties) {
		this.repository = repository;
		this.detector = detector;
		this.narrator = narrator;
		this.latestReport = latestReport;
		this.properties = properties;
	}

	@Scheduled(fixedDelayString = "${grid.analyzer.scan-interval}")
	public void scan() {
		List<MeterSnapshot> snapshots = repository.findSnapshots(
				properties.historyWindow(), properties.minHistorySamples());

		List<AnomalyEvent> events = new ArrayList<>();
		for (MeterSnapshot snapshot : snapshots) {
			events.addAll(detector.evaluate(snapshot, properties));
		}

		String narrative = events.isEmpty() ? "" : narrator.narrate(events);
		latestReport.set(new AnomalyReport(Instant.now(), events, narrative));
	}

}
