package com.smartgrid.producer.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grid.producer")
public record ProducerProperties(
		String topic,
		int topicPartitions,
		int districtCount,
		int metersPerDistrict,
		int schedulerPoolSize,
		Duration interval) {
}
