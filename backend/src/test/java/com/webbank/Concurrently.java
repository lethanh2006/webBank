package com.webbank;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

/**
 * Runs N tasks on a thread pool; a latch holds every worker until all tasks are submitted and
 * then releases them together, which maximises the chance of a race condition showing up.
 */
public final class Concurrently {

	private Concurrently() {
	}

	public static <T> Outcomes<T> run(int tasks, int threads, IntFunction<T> task) throws InterruptedException {
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = new ArrayList<>();
		try (ExecutorService executor = Executors.newFixedThreadPool(threads)) {
			for (int i = 0; i < tasks; i++) {
				int index = i;
				Callable<T> callable = () -> {
					start.await();
					return task.apply(index);
				};
				futures.add(executor.submit(callable));
			}
			start.countDown();
			executor.shutdown();
			if (!executor.awaitTermination(2, TimeUnit.MINUTES)) {
				throw new IllegalStateException("Tasks did not finish in time");
			}
		}
		List<T> results = new ArrayList<>();
		List<Throwable> failures = new ArrayList<>();
		for (Future<T> future : futures) {
			try {
				results.add(future.get());
			}
			catch (ExecutionException ex) {
				failures.add(ex.getCause());
			}
		}
		return new Outcomes<>(results, failures);
	}

	public record Outcomes<T>(List<T> results, List<Throwable> failures) {
	}

}
