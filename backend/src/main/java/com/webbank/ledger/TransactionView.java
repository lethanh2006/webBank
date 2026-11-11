package com.webbank.ledger;

import java.time.Instant;

import com.webbank.shared.money.Money;

/** One line of an account statement. */
public record TransactionView(long id, String reference, EntryType type, String description, Direction direction,
		Money amount, Money balanceAfter, String counterpartyAccountNo, String counterpartyName, Instant createdAt) {

}
