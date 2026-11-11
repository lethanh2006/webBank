package com.webbank.ledger.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Currency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.webbank.ledger.AccountStatus;
import com.webbank.ledger.AccountType;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.money.Money;

/**
 * Ledger account with its running balances. Balances only change through {@link #debit} and
 * {@link #credit}, which the ledger calls while holding a row lock on this account.
 */
@Entity
@Table(name = "account")
public class Account {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "account_no", nullable = false, updatable = false)
	private String accountNo;

	@Column(name = "customer_id", updatable = false)
	private Long customerId;

	@Column(nullable = false)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private AccountType type;

	@Column(nullable = false, updatable = false)
	private String currency;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AccountStatus status;

	@Column(name = "ledger_balance", nullable = false)
	private BigDecimal ledgerBalance;

	@Column(name = "available_balance", nullable = false)
	private BigDecimal availableBalance;

	/**
	 * Optimistic lock for low-contention changes (status, name). Money movements additionally
	 * take a pessimistic row lock, so this version never makes a transfer fail.
	 */
	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Account() {
	}

	static Account openCustomerAccount(String accountNo, long customerId, String name, Currency currency, Instant now) {
		Account account = new Account();
		account.accountNo = accountNo;
		account.customerId = customerId;
		account.name = name;
		account.type = AccountType.CURRENT;
		account.currency = currency.getCurrencyCode();
		account.status = AccountStatus.ACTIVE;
		account.ledgerBalance = BigDecimal.ZERO;
		account.availableBalance = BigDecimal.ZERO;
		account.createdAt = now;
		account.updatedAt = now;
		return account;
	}

	/** @return the ledger balance after the debit */
	Money debit(Money amount, Instant now) {
		requireCurrency(amount);
		if (status != AccountStatus.ACTIVE) {
			throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE,
					"Tài khoản %s không thể ghi nợ (trạng thái %s)".formatted(accountNo, status));
		}
		// Internal accounts may go negative (see the sign convention in V2__ledger.sql).
		if (type != AccountType.INTERNAL && !availableBalance().isGreaterThanOrEqualTo(amount)) {
			throw new BusinessException(ErrorCode.INSUFFICIENT_FUNDS);
		}
		ledgerBalance = ledgerBalance.subtract(amount.amount());
		availableBalance = availableBalance.subtract(amount.amount());
		updatedAt = now;
		return ledgerBalance();
	}

	/** @return the ledger balance after the credit */
	Money credit(Money amount, Instant now) {
		requireCurrency(amount);
		if (status == AccountStatus.CLOSED) {
			throw new BusinessException(ErrorCode.ACCOUNT_NOT_ACTIVE,
					"Tài khoản %s đã đóng".formatted(accountNo));
		}
		ledgerBalance = ledgerBalance.add(amount.amount());
		availableBalance = availableBalance.add(amount.amount());
		updatedAt = now;
		return ledgerBalance();
	}

	private void requireCurrency(Money amount) {
		if (!amount.currencyCode().equals(currency)) {
			throw new BusinessException(ErrorCode.CURRENCY_MISMATCH);
		}
	}

	Long getId() {
		return id;
	}

	String getAccountNo() {
		return accountNo;
	}

	Money ledgerBalance() {
		return Money.of(ledgerBalance, currency);
	}

	Money availableBalance() {
		return Money.of(availableBalance, currency);
	}

}
