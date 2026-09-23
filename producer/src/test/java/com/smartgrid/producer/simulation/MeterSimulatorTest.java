package com.smartgrid.producer.simulation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.smartgrid.producer.config.ProducerProperties;
import com.smartgrid.producer.kafka.ReadingPublisher;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.DefaultApplicationArguments;

class MeterSimulatorTest {

	private MeterSimulator simulator;

	@AfterEach
	void tearDown() {
		if (simulator != null) {
			simulator.shutdown();
		}
	}

	/**
	 * Deliberate regression guard for a design decision, not just a
	 * behavior check: unlike the consumer's DB-writing workers, a tick here
	 * only does CPU work plus a non-blocking {@code KafkaTemplate.send()},
	 * so there's no blocking work for a virtual thread to park cheaply on.
	 * See ARCHITECTURE.md's "Producer concurrency model" section. If this
	 * starts failing, either the tick body started doing blocking work
	 * (worth reconsidering virtual threads for), or someone swapped the
	 * executor without updating that reasoning.
	 */
	@Test
	void ticksRunOnPlatformThreadsNotVirtualThreads() throws InterruptedException {
		ReadingPublisher publisher = mock(ReadingPublisher.class);
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean sawVirtualThread = new AtomicBoolean();
		doAnswer(invocation -> {
			sawVirtualThread.set(Thread.currentThread().isVirtual());
			latch.countDown();
			return null;
		}).when(publisher).publish(any());

		ProducerProperties properties = new ProducerProperties(
				"electricity-readings", 1, 1, 1, 1, Duration.ofMillis(20));
		simulator = new MeterSimulator(properties, publisher);
		simulator.run(new DefaultApplicationArguments());

		assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(sawVirtualThread).isFalse();
	}

}
