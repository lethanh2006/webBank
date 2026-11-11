package com.webbank.ledger;

import java.util.Objects;

import com.webbank.shared.money.Money;

/** One side of a journal entry: debit or credit an account by an amount. */
public record PostingLeg(String accountNo, Direction direction, Money amount) {

	public PostingLeg {
		Objects.requireNonNull(accountNo, "accountNo");
		Objects.requireNonNull(direction, "direction");
		Objects.requireNonNull(amount, "amount");
	}

	public static PostingLeg debit(String accountNo, Money amount) {
		return new PostingLeg(accountNo, Direction.DEBIT, amount);
	}

	public static PostingLeg credit(String accountNo, Money amount) {
		return new PostingLeg(accountNo, Direction.CREDIT, amount);
	}

}
