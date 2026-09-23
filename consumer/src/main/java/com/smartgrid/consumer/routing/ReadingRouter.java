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
	private final ReadingRepository repository;
	private ExecutorService executor;

	public ReadingRouter(ConsumerProperties properties, ReadingRepository repository) {
		this.workerCount = properties.workerCount();
		this.queues = new ArrayList<>(workerCount);
		for (int i = 0; i < workerCount; i++) {
			queues.add(new ArrayBlockingQueue<>(properties.queueCapacity()));
		}
		this.repository = repository;
	}

	@PostConstruct
	void start() {
		executor = Executors.newVirtualThreadPerTaskExecutor();
		for (BlockingQueue<Reading> queue : queues) {
			executor.submit(new Worker(queue, repository));
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
