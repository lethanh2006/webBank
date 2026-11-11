package com.webbank.ledger;

import com.webbank.shared.money.Money;

public record AccountView(String accountNo, Long customerId, String name, AccountType type, AccountStatus status,
		Money ledgerBalance, Money availableBalance) {

}
