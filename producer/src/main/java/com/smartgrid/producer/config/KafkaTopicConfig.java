package com.smartgrid.producer.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

	@Bean
	public NewTopic electricityReadingsTopic(ProducerProperties properties) {
		return TopicBuilder.name(properties.topic())
				.partitions(properties.topicPartitions())
				.replicas(1)
				.build();
	}

}
