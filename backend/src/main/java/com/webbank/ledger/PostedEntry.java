package com.webbank.ledger;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.webbank.shared.money.Money;

public record PostedEntry(String reference, EntryType type, LocalDate businessDate, Instant postedAt,
		List<PostedLeg> postings) {

	public record PostedLeg(String accountNo, Direction direction, Money amount, Money balanceAfter) {
	}

}
