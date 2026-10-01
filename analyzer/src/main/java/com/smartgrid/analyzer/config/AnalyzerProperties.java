package com.smartgrid.analyzer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grid.analyzer")
public record AnalyzerProperties(
		Duration scanInterval,
		Duration historyWindow,
		double voltageNominalV,
		double voltageTolerancePct,
		double powerZscoreThreshold,
		int minHistorySamples) {
}
