package com.webbank.ledger;

import java.util.Currency;
import java.util.List;

/** Account opening and the read side of accounts. Every read is scoped to its owner. */
public interface AccountService {

	AccountView openCustomerAccount(long customerId, Currency currency);

	List<AccountView> findByCustomer(long customerId);

	/** @throws com.webbank.shared.error.BusinessException ACCOUNT_NOT_FOUND when not owned by the customer */
	AccountView getOwnedAccount(long customerId, String accountNo);

	/** Resolves a destination account for a transfer (validates the check digit first). */
	AccountHolder lookupHolder(String accountNo);

	HistoryPage history(long customerId, String accountNo, String cursor, int size);

}
