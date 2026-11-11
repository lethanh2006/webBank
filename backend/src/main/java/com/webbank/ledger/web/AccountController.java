package com.webbank.ledger.web;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.webbank.customer.CurrentCustomer;
import com.webbank.ledger.AccountHolder;
import com.webbank.ledger.AccountService;
import com.webbank.ledger.AccountStatus;
import com.webbank.ledger.AccountType;
import com.webbank.ledger.AccountView;
import com.webbank.ledger.Direction;
import com.webbank.ledger.EntryType;
import com.webbank.ledger.HistoryPage;
import com.webbank.ledger.TransactionView;
import com.webbank.shared.money.MoneyView;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Accounts")
class AccountController {

	private final AccountService accounts;

	AccountController(AccountService accounts) {
		this.accounts = accounts;
	}

	@GetMapping("/accounts")
	@Operation(summary = "Accounts of the current customer")
	List<AccountResponse> myAccounts(@Parameter(hidden = true) CurrentCustomer customer) {
		return accounts.findByCustomer(customer.id()).stream().map(AccountResponse::of).toList();
	}

	@GetMapping("/accounts/{accountNo}")
	@Operation(summary = "One account of the current customer (404 for anyone else's account)")
	AccountResponse account(@Parameter(hidden = true) CurrentCustomer customer, @PathVariable String accountNo) {
		return AccountResponse.of(accounts.getOwnedAccount(customer.id(), accountNo));
	}

	@GetMapping("/accounts/{accountNo}/transactions")
	@Operation(summary = "Account statement, newest first, cursor (keyset) pagination")
	HistoryResponse transactions(@Parameter(hidden = true) CurrentCustomer customer, @PathVariable String accountNo,
			@RequestParam(required = false) String cursor, @RequestParam(defaultValue = "20") int size) {
		return HistoryResponse.of(accounts.history(customer.id(), accountNo, cursor, size));
	}

	@GetMapping("/account-holders/{accountNo}")
	@Operation(summary = "Name of the holder of a destination account, shown before confirming a transfer")
	AccountHolderResponse holder(@Parameter(hidden = true) CurrentCustomer customer, @PathVariable String accountNo) {
		AccountHolder holder = accounts.lookupHolder(accountNo);
		return new AccountHolderResponse(holder.accountNo(), holder.name(), holder.currency().getCurrencyCode());
	}

	record AccountResponse(String accountNo, String name, AccountType type, AccountStatus status,
			MoneyView ledgerBalance, MoneyView availableBalance) {

		static AccountResponse of(AccountView view) {
			return new AccountResponse(view.accountNo(), view.name(), view.type(), view.status(),
					MoneyView.of(view.ledgerBalance()), MoneyView.of(view.availableBalance()));
		}

	}

	record TransactionResponse(long id, String reference, EntryType type, String description, Direction direction,
			MoneyView amount, MoneyView balanceAfter, String counterpartyAccountNo, String counterpartyName,
			Instant createdAt) {

		static TransactionResponse of(TransactionView view) {
			return new TransactionResponse(view.id(), view.reference(), view.type(), view.description(),
					view.direction(), MoneyView.of(view.amount()), MoneyView.of(view.balanceAfter()),
					view.counterpartyAccountNo(), view.counterpartyName(), view.createdAt());
		}

	}

	record HistoryResponse(List<TransactionResponse> items, String nextCursor) {

		static HistoryResponse of(HistoryPage page) {
			return new HistoryResponse(page.items().stream().map(TransactionResponse::of).toList(), page.nextCursor());
		}

	}

	record AccountHolderResponse(String accountNo, String name, String currency) {
	}

}
