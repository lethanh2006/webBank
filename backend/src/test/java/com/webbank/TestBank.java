package com.webbank;

import java.math.BigDecimal;
import java.util.UUID;

import com.webbank.customer.CustomerService;
import com.webbank.customer.CustomerView;
import com.webbank.ledger.AccountService;
import com.webbank.ledger.EntryType;
import com.webbank.ledger.JournalEntryRequest;
import com.webbank.ledger.LedgerAccounts;
import com.webbank.ledger.LedgerService;
import com.webbank.ledger.PostingLeg;
import com.webbank.shared.money.Money;

import org.springframework.jdbc.core.simple.JdbcClient;

import static org.assertj.core.api.Assertions.assertThat;

/** Creates test data through the real services and checks the ledger invariants. */
public class TestBank {

	private final CustomerService customers;

	private final AccountService accounts;

	private final LedgerService ledger;

	private final JdbcClient jdbc;

	TestBank(CustomerService customers, AccountService accounts, LedgerService ledger, JdbcClient jdbc) {
		this.customers = customers;
		this.accounts = accounts;
		this.ledger = ledger;
		this.jdbc = jdbc;
	}

	public CustomerView newCustomer(String fullName) {
		return customers.register(fullName, null, null);
	}

	/** Opens a VND account and funds it with a cash deposit posted through the ledger. */
	public String openAccount(long customerId, long initialBalance) {
		String accountNo = accounts.openCustomerAccount(customerId, Money.VND).accountNo();
		if (initialBalance > 0) {
			deposit(accountNo, initialBalance);
		}
		return accountNo;
	}

	public void deposit(String accountNo, long amount) {
		ledger.post(new JournalEntryRequest("DP-" + UUID.randomUUID().toString().substring(0, 18), EntryType.DEPOSIT, "Nộp tiền mặt",
				java.util.List.of(PostingLeg.debit(LedgerAccounts.CASH_VND, Money.vnd(amount)),
						PostingLeg.credit(accountNo, Money.vnd(amount)))));
	}

	public Money balance(String accountNo) {
		BigDecimal balance = jdbc.sql("SELECT ledger_balance FROM account WHERE account_no = ?")
			.param(accountNo)
			.query(BigDecimal.class)
			.single();
		return Money.of(balance, "VND");
	}

	public long postingCount(String accountNo) {
		return jdbc.sql("SELECT count(*) FROM posting p JOIN account a ON a.id = p.account_id WHERE a.account_no = ?")
			.param(accountNo)
			.query(Long.class)
			.single();
	}

	/**
	 * The three invariants of a double-entry ledger:
	 * every account's balance equals the sum of its postings, all balances sum to zero,
	 * and no customer account is overdrawn.
	 */
	public void assertLedgerInvariants() {
		long driftedAccounts = jdbc.sql("""
				SELECT count(*) FROM account a
				WHERE a.ledger_balance <> COALESCE((
				    SELECT sum(CASE WHEN p.direction = 'CREDIT' THEN p.amount ELSE -p.amount END)
				    FROM posting p WHERE p.account_id = a.id), 0)
				""").query(Long.class).single();
		BigDecimal total = jdbc.sql("SELECT COALESCE(sum(ledger_balance), 0) FROM account")
			.query(BigDecimal.class)
			.single();
		long overdrawn = jdbc.sql("SELECT count(*) FROM account WHERE type <> 'INTERNAL' AND available_balance < 0")
			.query(Long.class)
			.single();

		assertThat(driftedAccounts).as("accounts whose balance differs from their postings").isZero();
		assertThat(total).as("sum of all balances").isEqualByComparingTo(BigDecimal.ZERO);
		assertThat(overdrawn).as("overdrawn customer accounts").isZero();
	}

}
