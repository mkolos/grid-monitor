package com.smartgrid.consumer.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "grid.consumer")
public record ConsumerProperties(
		String topic,
		String groupId,
		int workerCount,
		int queueCapacity) {
}
