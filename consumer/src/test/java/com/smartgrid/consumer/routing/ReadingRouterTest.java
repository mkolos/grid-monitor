package com.smartgrid.consumer.routing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

import com.smartgrid.consumer.config.ConsumerProperties;
import com.smartgrid.consumer.db.ReadingRepository;
import com.smartgrid.consumer.model.Reading;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class ReadingRouterTest {

	private ReadingRouter router;

	@AfterEach
	void tearDown() {
		if (router != null) {
			router.shutdown();
		}
	}

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

	@Test
	void workersRunOnVirtualThreads() throws InterruptedException {
		ReadingRepository repository = mock(ReadingRepository.class);
		CountDownLatch latch = new CountDownLatch(1);
		AtomicBoolean sawVirtualThread = new AtomicBoolean();
		doAnswer(invocation -> {
			sawVirtualThread.set(Thread.currentThread().isVirtual());
			latch.countDown();
			return null;
		}).when(repository).save(any());

		router = new ReadingRouter(new ConsumerProperties("topic", "group", 2, 10), repository);
		router.start();
		router.route(new Reading("01-001", 1, 1.5, 230.0, 1000L));

		assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(sawVirtualThread).isTrue();
	}

	@Test
	void sameMeterIdIsAlwaysProcessedByTheSameWorkerThread() throws InterruptedException {
		ReadingRepository repository = mock(ReadingRepository.class);
		int messageCount = 20;
		CountDownLatch latch = new CountDownLatch(messageCount);
		Set<Long> threadIds = ConcurrentHashMap.newKeySet();
		doAnswer(invocation -> {
			threadIds.add(Thread.currentThread().threadId());
			latch.countDown();
			return null;
		}).when(repository).save(any());

		router = new ReadingRouter(new ConsumerProperties("topic", "group", 8, 50), repository);
		router.start();
		for (int i = 0; i < messageCount; i++) {
			router.route(new Reading("01-001", 1, 1.0 + i, 230.0, 1000L + i));
		}

		assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
		assertThat(threadIds).hasSize(1);
	}

	@Test
	void slowWriteForOneMeterDoesNotBlockOthers() throws InterruptedException {
		ReadingRepository repository = mock(ReadingRepository.class);
		CountDownLatch blockedMeterStarted = new CountDownLatch(1);
		CountDownLatch releaseBlockedMeter = new CountDownLatch(1);
		CountDownLatch otherMeterDone = new CountDownLatch(1);
		doAnswer(invocation -> {
			Reading reading = invocation.getArgument(0);
			if (reading.meterId().equals("01-001")) {
				blockedMeterStarted.countDown();
				releaseBlockedMeter.await(2, TimeUnit.SECONDS);
			} else {
				otherMeterDone.countDown();
			}
			return null;
		}).when(repository).save(any());

		router = new ReadingRouter(new ConsumerProperties("topic", "group", 8, 50), repository);
		router.start();
		router.route(new Reading("01-001", 1, 1.5, 230.0, 1000L));
		assertThat(blockedMeterStarted.await(2, TimeUnit.SECONDS)).isTrue();

		router.route(new Reading("02-999", 2, 2.5, 231.0, 2000L));

		assertThat(otherMeterDone.await(2, TimeUnit.SECONDS)).isTrue();
		releaseBlockedMeter.countDown();
	}

	@Test
	void routeBlocksWhenTheTargetWorkersQueueIsFull() throws InterruptedException {
		ReadingRepository repository = mock(ReadingRepository.class);
		CountDownLatch releaseWorker = new CountDownLatch(1);
		doAnswer(invocation -> {
			releaseWorker.await(5, TimeUnit.SECONDS);
			return null;
		}).when(repository).save(any());

		router = new ReadingRouter(new ConsumerProperties("topic", "group", 1, 1), repository);
		router.start();
		router.route(new Reading("01-001", 1, 1.0, 230.0, 1000L));
		router.route(new Reading("01-001", 1, 2.0, 230.0, 2000L));

		CountDownLatch thirdMessageAccepted = new CountDownLatch(1);
		Thread routingThread = new Thread(() -> {
			router.route(new Reading("01-001", 1, 3.0, 230.0, 3000L));
			thirdMessageAccepted.countDown();
		});
		routingThread.start();
		try {
			assertThat(thirdMessageAccepted.await(200, TimeUnit.MILLISECONDS)).isFalse();
		} finally {
			releaseWorker.countDown();
			routingThread.join(2000);
		}
	}

	@Test
	void shutdownStopsWorkersPromptly() throws InterruptedException {
		ReadingRepository repository = mock(ReadingRepository.class);
		router = new ReadingRouter(new ConsumerProperties("topic", "group", 4, 10), repository);
		router.start();
		router.route(new Reading("01-001", 1, 1.0, 230.0, 1000L));

		long start = System.nanoTime();
		router.shutdown();
		long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

		assertThat(elapsedMs).isLessThan(5000);
	}

}
