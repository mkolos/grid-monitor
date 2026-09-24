package com.smartgrid.consumer.routing;

import com.smartgrid.consumer.db.ReadingRepository;
import com.smartgrid.consumer.model.Reading;
import java.util.concurrent.BlockingQueue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains one dedicated queue and writes one reading at a time. A write
 * failure is logged and dropped rather than retried, so one bad row can't
 * back up this worker's queue (and, transitively, the meters routed to it)
 * indefinitely.
 */
class Worker implements Runnable {

	private static final Logger log = LoggerFactory.getLogger(Worker.class);

	private final BlockingQueue<Reading> queue;
	private final ReadingRepository repository;
	private final WorkerMetrics metrics;

	Worker(BlockingQueue<Reading> queue, ReadingRepository repository, WorkerMetrics metrics) {
		this.queue = queue;
		this.repository = repository;
		this.metrics = metrics;
	}

	@Override
	public void run() {
		while (!Thread.currentThread().isInterrupted()) {
			Reading reading;
			try {
				reading = queue.take();
			} catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
			try {
				metrics.dbWriteTimer().record(() -> repository.save(reading));
				metrics.processedCounter().increment();
			} catch (Exception ex) {
				metrics.droppedCounter().increment();
				log.error("Dropping reading for meter {}, failed to write to DB", reading.meterId(), ex);
			}
		}
	}

}
