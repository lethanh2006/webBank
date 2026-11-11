package com.webbank.transfer;

import java.time.Instant;

import com.webbank.shared.money.Money;

public record TransferView(String reference, TransferType type, TransferStatus status, String sourceAccountNo,
		String destinationAccountNo, String destinationName, Money amount, String description, Instant createdAt,
		Instant completedAt) {

}
