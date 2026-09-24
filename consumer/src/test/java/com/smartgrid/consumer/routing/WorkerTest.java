package com.smartgrid.consumer.routing;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.assertj.core.api.Assertions.assertThat;

import com.smartgrid.consumer.db.ReadingRepository;
import com.smartgrid.consumer.model.Reading;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class WorkerTest {

	private static final Reading READING_1 = new Reading("01-001", 1, 1.5, 230.0, 1000L);
	private static final Reading READING_2 = new Reading("01-002", 1, 2.5, 231.0, 2000L);

	private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

	@Test
	void savesEachQueuedReading() throws InterruptedException {
		BlockingQueue<Reading> queue = new ArrayBlockingQueue<>(10);
		ReadingRepository repository = mock(ReadingRepository.class);
		CountDownLatch latch = new CountDownLatch(2);
		doAnswer(invocation -> {
			latch.countDown();
			return null;
		}).when(repository).save(org.mockito.ArgumentMatchers.any());

		Thread thread = new Thread(new Worker(queue, repository, WorkerMetrics.forWorker(0, registry)));
		thread.start();
		try {
			queue.put(READING_1);
			queue.put(READING_2);

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			verify(repository).save(READING_1);
			verify(repository).save(READING_2);
			assertThat(registry.get("consumer.readings.processed").tag("worker", "0").counter().count()).isEqualTo(2.0);
			assertThat(registry.get("consumer.readings.dropped").tag("worker", "0").counter().count()).isEqualTo(0.0);
			assertThat(registry.get("consumer.db.write.duration").tag("worker", "0").timer().count()).isEqualTo(2L);
		} finally {
			thread.interrupt();
			thread.join(1000);
		}
	}

	@Test
	void dropsAFailedWriteAndKeepsProcessingSubsequentItems() throws InterruptedException {
		BlockingQueue<Reading> queue = new ArrayBlockingQueue<>(10);
		ReadingRepository repository = mock(ReadingRepository.class);
		CountDownLatch latch = new CountDownLatch(1);
		doThrow(new RuntimeException("boom")).when(repository).save(eq(READING_1));
		doAnswer(invocation -> {
			latch.countDown();
			return null;
		}).when(repository).save(eq(READING_2));

		Thread thread = new Thread(new Worker(queue, repository, WorkerMetrics.forWorker(0, registry)));
		thread.start();
		try {
			queue.put(READING_1);
			queue.put(READING_2);

			assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
			verify(repository).save(READING_1);
			verify(repository).save(READING_2);
			assertThat(registry.get("consumer.readings.processed").tag("worker", "0").counter().count()).isEqualTo(1.0);
			assertThat(registry.get("consumer.readings.dropped").tag("worker", "0").counter().count()).isEqualTo(1.0);
		} finally {
			thread.interrupt();
			thread.join(1000);
		}
	}

}
