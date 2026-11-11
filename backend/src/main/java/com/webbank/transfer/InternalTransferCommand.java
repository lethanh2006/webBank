package com.webbank.transfer;

import java.util.Objects;

import com.webbank.shared.money.Money;

public record InternalTransferCommand(String sourceAccountNo, String destinationAccountNo, Money amount,
		String description) {

	public InternalTransferCommand {
		Objects.requireNonNull(sourceAccountNo, "sourceAccountNo");
		Objects.requireNonNull(destinationAccountNo, "destinationAccountNo");
		Objects.requireNonNull(amount, "amount");
	}

}
