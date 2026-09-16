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

	Worker(BlockingQueue<Reading> queue, ReadingRepository repository) {
		this.queue = queue;
		this.repository = repository;
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
				repository.save(reading);
			} catch (Exception ex) {
				log.error("Dropping reading for meter {}, failed to write to DB", reading.meterId(), ex);
			}
		}
	}

}
