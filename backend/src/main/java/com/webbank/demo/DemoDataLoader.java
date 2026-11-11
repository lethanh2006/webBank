package com.webbank.demo;

import java.util.List;
import java.util.UUID;

import com.webbank.customer.CustomerService;
import com.webbank.ledger.AccountService;
import com.webbank.ledger.EntryType;
import com.webbank.ledger.JournalEntryRequest;
import com.webbank.ledger.LedgerAccounts;
import com.webbank.ledger.LedgerService;
import com.webbank.ledger.PostingLeg;
import com.webbank.shared.money.Money;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Seeds demo customers on an empty dev database. Opening balances are real cash deposits
 * posted through the ledger, never balances written directly, so the invariants hold.
 */
@Component
@Profile("demo")
class DemoDataLoader implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataLoader.class);

	private final CustomerService customers;

	private final AccountService accounts;

	private final LedgerService ledger;

	private final TransactionTemplate transactions;

	DemoDataLoader(CustomerService customers, AccountService accounts, LedgerService ledger,
			TransactionTemplate transactions) {
		this.customers = customers;
		this.accounts = accounts;
		this.ledger = ledger;
		this.transactions = transactions;
	}

	@Override
	public void run(ApplicationArguments args) {
		if (customers.count() > 0) {
			return;
		}
		transactions.executeWithoutResult(status -> {
			long an = customer("Nguyễn Văn An", "0901000001", 50_000_000, 5_000_000);
			customer("Trần Thị Bình", "0901000002", 20_000_000);
			customer("Lê Hoàng Cường", "0901000003", 2_000_000);
			log.info("Demo data created; first demo customer id = {}", an);
		});
	}

	private long customer(String fullName, String phone, long... openingBalances) {
		long customerId = customers.register(fullName, phone, null).id();
		for (long balance : openingBalances) {
			String accountNo = accounts.openCustomerAccount(customerId, Money.VND).accountNo();
			ledger.post(new JournalEntryRequest("DP-" + UUID.randomUUID().toString().substring(0, 18), EntryType.DEPOSIT,
					"Nộp tiền mặt mở tài khoản", List.of(PostingLeg.debit(LedgerAccounts.CASH_VND, Money.vnd(balance)),
							PostingLeg.credit(accountNo, Money.vnd(balance)))));
		}
		return customerId;
	}

}
