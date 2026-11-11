package com.webbank.shared.time;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

import org.springframework.stereotype.Component;

/**
 * Single source of "now" and of the business date. Business dates (daily limits, end-of-day,
 * interest accrual) follow Vietnam time, while timestamps are stored in UTC. Injecting a
 * {@link Clock} keeps time-dependent rules testable.
 */
@Component
public class BusinessCalendar {

	public static final ZoneId BANK_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

	private final Clock clock;

	public BusinessCalendar(Clock clock) {
		this.clock = clock;
	}

	public Instant now() {
		return clock.instant();
	}

	public LocalDate today() {
		return LocalDate.ofInstant(clock.instant(), BANK_ZONE);
	}

}
