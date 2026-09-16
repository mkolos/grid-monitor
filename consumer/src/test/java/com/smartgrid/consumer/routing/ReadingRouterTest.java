package com.smartgrid.consumer.routing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ReadingRouterTest {

	@Test
	void sameMeterIdAlwaysMapsToTheSameWorker() {
		int index = ReadingRouter.workerIndexFor("07-042", 8);

		for (int i = 0; i < 20; i++) {
			assertThat(ReadingRouter.workerIndexFor("07-042", 8)).isEqualTo(index);
		}
	}

	@Test
	void differentMeterIdsSpreadAcrossWorkers() {
		int workerCount = 8;
		Set<Integer> indices = new HashSet<>();

		for (int district = 1; district <= 23; district++) {
			for (int meter = 1; meter <= 100; meter++) {
				String meterId = "%02d-%03d".formatted(district, meter);
				int index = ReadingRouter.workerIndexFor(meterId, workerCount);
				assertThat(index).isBetween(0, workerCount - 1);
				indices.add(index);
			}
		}

		assertThat(indices).hasSize(workerCount);
	}

}
