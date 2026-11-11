package com.webbank.ledger.internal;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Currency;
import java.util.List;

import com.webbank.customer.CustomerService;
import com.webbank.customer.CustomerView;
import com.webbank.ledger.AccountHolder;
import com.webbank.ledger.AccountService;
import com.webbank.ledger.AccountStatus;
import com.webbank.ledger.AccountType;
import com.webbank.ledger.AccountView;
import com.webbank.ledger.HistoryPage;
import com.webbank.ledger.TransactionView;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.time.BusinessCalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultAccountService implements AccountService {

	static final int MAX_PAGE_SIZE = 100;

	private final AccountRepository accounts;

	private final TransactionHistoryQuery historyQuery;

	private final CustomerService customers;

	private final BusinessCalendar calendar;

	DefaultAccountService(AccountRepository accounts, TransactionHistoryQuery historyQuery, CustomerService customers,
			BusinessCalendar calendar) {
		this.accounts = accounts;
		this.historyQuery = historyQuery;
		this.customers = customers;
		this.calendar = calendar;
	}

	@Override
	@Transactional
	public AccountView openCustomerAccount(long customerId, Currency currency) {
		CustomerView customer = customers.get(customerId);
		String accountNo = AccountNumbers.customerAccountNumber(accounts.nextAccountSequence());
		accounts.save(Account.openCustomerAccount(accountNo, customer.id(), AccountNumbers.holderName(customer.fullName()),
				currency, calendar.now()));
		return accounts.findRowByAccountNo(accountNo).orElseThrow().toView();
	}

	@Override
	@Transactional(readOnly = true)
	public List<AccountView> findByCustomer(long customerId) {
		return accounts.findRowsByCustomerId(customerId).stream().map(AccountRow::toView).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public AccountView getOwnedAccount(long customerId, String accountNo) {
		return ownedRow(customerId, accountNo).toView();
	}

	@Override
	@Transactional(readOnly = true)
	public AccountHolder lookupHolder(String accountNo) {
		if (!AccountNumbers.isValid(accountNo)) {
			throw new BusinessException(ErrorCode.INVALID_ACCOUNT_NUMBER);
		}
		return accounts.findRowByAccountNo(accountNo)
			.filter(row -> row.type() == AccountType.CURRENT && row.status() != AccountStatus.CLOSED)
			.map(row -> new AccountHolder(row.accountNo(), row.name(), row.currencyUnit()))
			.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
	}

	@Override
	@Transactional(readOnly = true)
	public HistoryPage history(long customerId, String accountNo, String cursor, int size) {
		AccountRow account = ownedRow(customerId, accountNo);
		int pageSize = Math.clamp(size, 1, MAX_PAGE_SIZE);
		// Fetch one extra row to know whether another page exists.
		List<TransactionView> rows = historyQuery.page(account.id(), decodeCursor(cursor), pageSize + 1);
		if (rows.size() <= pageSize) {
			return new HistoryPage(rows, null);
		}
		List<TransactionView> page = rows.subList(0, pageSize);
		return new HistoryPage(List.copyOf(page), encodeCursor(page.getLast().id()));
	}

	/**
	 * Ownership is part of the query: another customer's account is reported as not found
	 * rather than forbidden, so the API does not reveal that the account exists (BOLA/IDOR).
	 */
	private AccountRow ownedRow(long customerId, String accountNo) {
		return accounts.findRowByAccountNo(accountNo)
			.filter(row -> row.isOwnedBy(customerId))
			.orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
	}

	/** Opaque cursor: clients must not build or do arithmetic on it. */
	static String encodeCursor(long postingId) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(("p:" + postingId).getBytes(StandardCharsets.UTF_8));
	}

	static long decodeCursor(String cursor) {
		if (cursor == null || cursor.isBlank()) {
			return Long.MAX_VALUE;
		}
		try {
			String decoded = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
			if (!decoded.startsWith("p:")) {
				throw new IllegalArgumentException(decoded);
			}
			return Long.parseLong(decoded.substring(2));
		}
		catch (IllegalArgumentException ex) {
			throw new BusinessException(ErrorCode.VALIDATION_FAILED, "Cursor không hợp lệ");
		}
	}

}
