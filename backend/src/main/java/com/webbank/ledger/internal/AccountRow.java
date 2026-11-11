package com.webbank.ledger.internal;

import java.math.BigDecimal;
import java.util.Currency;

import com.webbank.ledger.AccountStatus;
import com.webbank.ledger.AccountType;
import com.webbank.ledger.AccountView;
import com.webbank.shared.money.Money;

/** Read-only projection of an account row. */
record AccountRow(Long id, String accountNo, Long customerId, String name, AccountType type, String currency,
		AccountStatus status, BigDecimal ledgerBalance, BigDecimal availableBalance) {

	boolean isOwnedBy(long candidateCustomerId) {
		return customerId != null && customerId == candidateCustomerId;
	}

	Currency currencyUnit() {
		return Currency.getInstance(currency);
	}

	AccountView toView() {
		return new AccountView(accountNo, customerId, name, type, status, Money.of(ledgerBalance, currency),
				Money.of(availableBalance, currency));
	}

}
