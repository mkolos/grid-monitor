package com.smartgrid.producer.kafka;

import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrid.producer.config.ProducerProperties;
import com.smartgrid.producer.model.Reading;
import java.util.Map;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@EmbeddedKafka(partitions = 1, topics = "electricity-readings", bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@TestPropertySource(properties = "grid.producer.autostart=false")
class ReadingPublisherIntegrationTest {

	@Autowired
	private ReadingPublisher publisher;

	@Autowired
	private ProducerProperties properties;

	@Autowired
	private EmbeddedKafkaBroker broker;

	@Test
	void publishedReadingCanBeConsumedBackWithTheSameShape() {
		Map<String, Object> consumerProps = KafkaTestUtils.consumerProps("test-group", "true", broker);
		consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
		consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
		consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "com.smartgrid.producer.model");

		try (Consumer<String, Reading> consumer = new DefaultKafkaConsumerFactory<String, Reading>(consumerProps)
				.createConsumer()) {
			broker.consumeFromAnEmbeddedTopic(consumer, properties.topic());

			Reading sent = new Reading("01-001", 1, 1.23, 231.5, System.currentTimeMillis());
			publisher.publish(sent);

			ConsumerRecord<String, Reading> record = KafkaTestUtils.getSingleRecord(consumer, properties.topic());

			assertThat(record.key()).isEqualTo("01-001");
			assertThat(record.value().district()).isEqualTo(1);
			assertThat(record.value().powerConsumptionKW()).isEqualTo(1.23);
			assertThat(record.value().voltage()).isEqualTo(231.5);
		}
	}

}
