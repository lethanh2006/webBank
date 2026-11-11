package com.webbank.transfer.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.webbank.shared.money.Money;
import com.webbank.transfer.TransferStatus;
import com.webbank.transfer.TransferType;
import com.webbank.transfer.TransferView;

/** The business document of a transfer. The money itself moves in the ledger, same reference. */
@Entity
@Table(name = "transfer")
public class Transfer {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false, updatable = false)
	private String reference;

	@Column(name = "customer_id", nullable = false, updatable = false)
	private Long customerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private TransferType type;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private TransferStatus status;

	@Column(name = "source_account_no", nullable = false, updatable = false)
	private String sourceAccountNo;

	@Column(name = "destination_account_no", nullable = false, updatable = false)
	private String destinationAccountNo;

	@Column(name = "destination_name", nullable = false, updatable = false)
	private String destinationName;

	@Column(nullable = false, updatable = false)
	private BigDecimal amount;

	@Column(nullable = false, updatable = false)
	private String currency;

	@Column(nullable = false, updatable = false)
	private String description;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "completed_at")
	private Instant completedAt;

	@Version
	private long version;

	protected Transfer() {
	}

	static Transfer completedInternal(String reference, long customerId, String sourceAccountNo,
			String destinationAccountNo, String destinationName, Money amount, String description, Instant now) {
		Transfer transfer = new Transfer();
		transfer.reference = reference;
		transfer.customerId = customerId;
		transfer.type = TransferType.INTERNAL;
		transfer.status = TransferStatus.COMPLETED;
		transfer.sourceAccountNo = sourceAccountNo;
		transfer.destinationAccountNo = destinationAccountNo;
		transfer.destinationName = destinationName;
		transfer.amount = amount.amount();
		transfer.currency = amount.currencyCode();
		transfer.description = description;
		transfer.createdAt = now;
		transfer.completedAt = now;
		return transfer;
	}

	TransferView toView() {
		return new TransferView(reference, type, status, sourceAccountNo, destinationAccountNo, destinationName,
				Money.of(amount, currency), description, createdAt, completedAt);
	}

}
