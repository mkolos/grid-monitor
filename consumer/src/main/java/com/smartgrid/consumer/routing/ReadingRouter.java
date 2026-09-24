package com.smartgrid.consumer.routing;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.smartgrid.consumer.config.ConsumerProperties;
import com.smartgrid.consumer.db.ReadingRepository;
import com.smartgrid.consumer.model.Reading;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

/**
 * Routes each reading to a fixed worker by {@code hash(meterId) % workers},
 * so every message for a given meter always lands on the same worker
 * thread — preserving the per-meter ordering Kafka already guarantees via
 * partitioning by meterId, all the way through to the DB write. This
 * ordering guarantee comes from the queue/worker topology, not from the
 * kind of thread each worker runs on.
 *
 * <p>Each worker owns one bounded queue; {@code put()} blocks when full,
 * which is the backpressure mechanism that slows Kafka consumption to
 * match DB write speed.
 */
@Component
public class ReadingRouter {

	private final int workerCount;
	private final List<BlockingQueue<Reading>> queues;
	private final List<WorkerMetrics> workerMetrics;
	private final ReadingRepository repository;
	private ExecutorService executor;

	public ReadingRouter(ConsumerProperties properties, ReadingRepository repository, MeterRegistry registry) {
		this.workerCount = properties.workerCount();
		this.queues = new ArrayList<>(workerCount);
		this.workerMetrics = new ArrayList<>(workerCount);
		for (int i = 0; i < workerCount; i++) {
			BlockingQueue<Reading> queue = new ArrayBlockingQueue<>(properties.queueCapacity());
			queues.add(queue);
			Gauge.builder("consumer.queue.depth", queue, BlockingQueue::size)
					.description("Number of readings currently queued for a worker (DB-write backpressure)")
					.tag("worker", String.valueOf(i))
					.register(registry);
			workerMetrics.add(WorkerMetrics.forWorker(i, registry));
		}
		this.repository = repository;
	}

	@PostConstruct
	void start() {
		executor = Executors.newVirtualThreadPerTaskExecutor();
		for (int i = 0; i < queues.size(); i++) {
			executor.submit(new Worker(queues.get(i), repository, workerMetrics.get(i)));
		}
	}

	public void route(Reading reading) {
		int index = workerIndexFor(reading.meterId(), workerCount);
		try {
			queues.get(index).put(reading);
		} catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while routing reading for meter " + reading.meterId(), ex);
		}
	}

	static int workerIndexFor(String meterId, int workerCount) {
		return Math.floorMod(meterId.hashCode(), workerCount);
	}

	@PreDestroy
	void shutdown() {
		if (executor != null) {
			executor.shutdownNow();
			try {
				executor.awaitTermination(5, TimeUnit.SECONDS);
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
			}
		}
	}

}
