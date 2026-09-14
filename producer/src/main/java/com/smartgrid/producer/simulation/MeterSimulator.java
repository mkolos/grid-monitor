package com.smartgrid.producer.simulation;

import com.smartgrid.producer.config.ProducerProperties;
import com.smartgrid.producer.kafka.ReadingPublisher;
import jakarta.annotation.PreDestroy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Schedules one independent periodic task per simulated meter on a shared
 * {@link ScheduledExecutorService}. This is the module's core lesson: a
 * small thread pool multiplexing thousands of logical periodic tasks, each
 * meter staggered so the pool doesn't burst all sends at once every tick.
 *
 * <p>Disabled via {@code grid.producer.autostart=false} in tests, so a
 * plain context-load test doesn't start firing 2,300 meters at whatever
 * broker happens to be configured.
 */
@Component
@ConditionalOnProperty(prefix = "grid.producer", name = "autostart", matchIfMissing = true)
public class MeterSimulator implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(MeterSimulator.class);

	private final ProducerProperties properties;
	private final ReadingPublisher publisher;
	private ScheduledExecutorService executor;

	public MeterSimulator(ProducerProperties properties, ReadingPublisher publisher) {
		this.properties = properties;
		this.publisher = publisher;
	}

	@Override
	public void run(ApplicationArguments args) {
		List<MeterState> meters = buildMeters();
		executor = Executors.newScheduledThreadPool(properties.schedulerPoolSize());
		long intervalMillis = properties.interval().toMillis();

		for (MeterState meter : meters) {
			long initialDelay = ThreadLocalRandom.current().nextLong(intervalMillis);
			executor.scheduleAtFixedRate(() -> tick(meter), initialDelay, intervalMillis, TimeUnit.MILLISECONDS);
		}

		log.info("Scheduled {} meters across {} districts on a pool of {} threads, every {}",
				meters.size(), properties.districtCount(), properties.schedulerPoolSize(), properties.interval());
	}

	private void tick(MeterState meter) {
		try {
			publisher.publish(meter.nextReading());
		} catch (Exception ex) {
			// scheduleAtFixedRate cancels the task forever if the Runnable throws,
			// so an uncaught exception here would silently kill this one meter.
			log.error("Meter {} failed to produce a reading, will retry next tick", meter.meterId(), ex);
		}
	}

	private List<MeterState> buildMeters() {
		List<MeterState> meters = new ArrayList<>(properties.districtCount() * properties.metersPerDistrict());
		for (int district = 1; district <= properties.districtCount(); district++) {
			for (int i = 1; i <= properties.metersPerDistrict(); i++) {
				String meterId = "%02d-%03d".formatted(district, i);
				meters.add(MeterState.startingAt(meterId, district));
			}
		}
		return meters;
	}

	@PreDestroy
	public void shutdown() {
		if (executor != null) {
			executor.shutdown();
		}
	}

}
