package com.smartgrid.consumer.routing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * Pre-resolved meters for one worker, built once at startup so the hot
 * per-message path never touches the registry or builds tag strings.
 */
record WorkerMetrics(Timer dbWriteTimer, Counter processedCounter, Counter droppedCounter) {

	static WorkerMetrics forWorker(int workerIndex, MeterRegistry registry) {
		String worker = String.valueOf(workerIndex);
		Timer dbWriteTimer = Timer.builder("consumer.db.write.duration")
				.description("Wall-clock time of a single ReadingRepository.save() call")
				.tag("worker", worker)
				.register(registry);
		Counter processedCounter = Counter.builder("consumer.readings.processed")
				.description("Readings successfully written to the database, per worker")
				.tag("worker", worker)
				.register(registry);
		Counter droppedCounter = Counter.builder("consumer.readings.dropped")
				.description("Readings dropped after a failed database write, per worker")
				.tag("worker", worker)
				.register(registry);
		return new WorkerMetrics(dbWriteTimer, processedCounter, droppedCounter);
	}

}
