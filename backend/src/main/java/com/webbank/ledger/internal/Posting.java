package com.webbank.ledger.internal;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import com.webbank.ledger.Direction;
import com.webbank.shared.money.Money;

@Entity
@Immutable
@Table(name = "posting")
public class Posting {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "journal_entry_id", nullable = false)
	private JournalEntry journalEntry;

	@Column(name = "account_id", nullable = false)
	private Long accountId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private Direction direction;

	@Column(nullable = false)
	private BigDecimal amount;

	@Column(nullable = false)
	private String currency;

	@Column(name = "balance_after", nullable = false)
	private BigDecimal balanceAfter;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected Posting() {
	}

	Posting(JournalEntry journalEntry, Long accountId, Direction direction, Money amount, Money balanceAfter,
			Instant createdAt) {
		this.journalEntry = journalEntry;
		this.accountId = accountId;
		this.direction = direction;
		this.amount = amount.amount();
		this.currency = amount.currencyCode();
		this.balanceAfter = balanceAfter.amount();
		this.createdAt = createdAt;
	}

	Long getAccountId() {
		return accountId;
	}

	Direction getDirection() {
		return direction;
	}

	Money amount() {
		return Money.of(amount, currency);
	}

	Money balanceAfter() {
		return Money.of(balanceAfter, currency);
	}

}
