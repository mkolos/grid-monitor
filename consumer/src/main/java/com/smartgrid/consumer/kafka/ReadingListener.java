package com.smartgrid.consumer.kafka;

import com.smartgrid.consumer.model.Reading;
import com.smartgrid.consumer.routing.ReadingRouter;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Only polls and routes — never touches the DB directly. Runs with the
 * container's default single listener thread and default (batch) ack
 * mode: the offset commits right after this method returns, i.e. right
 * after the reading is handed to a worker queue, not after it's actually
 * written to the DB. That's an accepted at-most-once trade-off: a crash
 * between commit and write loses one history row, in exchange for not
 * needing per-partition contiguous-offset tracking to ack after the async
 * write completes.
 */
@Component
public class ReadingListener {

	private final ReadingRouter router;

	public ReadingListener(ReadingRouter router) {
		this.router = router;
	}

	@KafkaListener(topics = "${grid.consumer.topic}", groupId = "${grid.consumer.group-id}")
	public void onReading(Reading reading) {
		router.route(reading);
	}

}
