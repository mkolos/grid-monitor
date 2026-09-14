package com.smartgrid.producer.kafka;

import com.smartgrid.producer.config.ProducerProperties;
import com.smartgrid.producer.model.Reading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReadingPublisher {

	private static final Logger log = LoggerFactory.getLogger(ReadingPublisher.class);

	private final KafkaTemplate<String, Reading> kafkaTemplate;
	private final String topic;

	public ReadingPublisher(KafkaTemplate<String, Reading> kafkaTemplate, ProducerProperties properties) {
		this.kafkaTemplate = kafkaTemplate;
		this.topic = properties.topic();
	}

	public void publish(Reading reading) {
		kafkaTemplate.send(topic, reading.meterId(), reading)
				.exceptionally(ex -> {
					log.warn("Failed to publish reading for meter {}", reading.meterId(), ex);
					return null;
				});
	}

}
