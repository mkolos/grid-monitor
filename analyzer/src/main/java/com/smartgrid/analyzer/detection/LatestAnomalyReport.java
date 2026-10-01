package com.smartgrid.analyzer.detection;

import com.smartgrid.analyzer.model.AnomalyReport;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/**
 * Holds only the most recent scan's result. Starts as {@link AnomalyReport#unscanned()}
 * so callers can tell "never scanned yet" apart from "scanned, all clear".
 */
@Component
public class LatestAnomalyReport {

	private final AtomicReference<AnomalyReport> current = new AtomicReference<>(AnomalyReport.unscanned());

	public AnomalyReport get() {
		return current.get();
	}

	public void set(AnomalyReport report) {
		current.set(report);
	}

}
