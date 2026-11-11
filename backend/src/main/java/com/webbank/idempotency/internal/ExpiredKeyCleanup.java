package com.webbank.idempotency.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes expired keys in small batches so no long-running DELETE holds locks. Running it on
 * several instances at once is harmless (deleting an already deleted row is a no-op); ShedLock
 * is introduced with the other scheduled jobs in phase 4.
 */
@Component
class ExpiredKeyCleanup {

	private static final Logger log = LoggerFactory.getLogger(ExpiredKeyCleanup.class);

	private static final int BATCH_SIZE = 1_000;

	private final JdbcClient jdbc;

	ExpiredKeyCleanup(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Scheduled(fixedDelayString = "${webbank.idempotency.cleanup-interval:PT30M}", initialDelayString = "PT1M")
	void deleteExpiredKeys() {
		int total = 0;
		int deleted;
		do {
			deleted = jdbc.sql("""
					DELETE FROM idempotency_key WHERE id IN (
					    SELECT id FROM idempotency_key WHERE expires_at < now() LIMIT :batch)""")
				.param("batch", BATCH_SIZE)
				.update();
			total += deleted;
		}
		while (deleted == BATCH_SIZE);
		if (total > 0) {
			log.info("Deleted {} expired idempotency keys", total);
		}
	}

}
