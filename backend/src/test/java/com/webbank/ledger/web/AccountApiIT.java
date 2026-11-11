package com.webbank.ledger.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.webbank.ApiClient;
import com.webbank.ApiClient.Response;
import com.webbank.IntegrationTest;
import com.webbank.TestBank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class AccountApiIT {

	@Value("${local.server.port}")
	int port;

	@Autowired
	JsonMapper json;

	@Autowired
	TestBank bank;

	ApiClient api;

	long owner;

	String account;

	@BeforeEach
	void setUp() {
		api = new ApiClient(port, json);
		owner = bank.newCustomer("Ngô Bảo Châu").id();
		account = bank.openAccount(owner, 3_000_000);
	}

	@Test
	void listsOnlyTheCurrentCustomersAccountsWithBalancesAsStrings() {
		String second = bank.openAccount(owner, 0);
		bank.openAccount(bank.newCustomer("Người Khác").id(), 1_000);

		Response response = api.get("/accounts", owner);

		assertThat(response.status()).isEqualTo(200);
		assertThat(response.body().findValuesAsString("accountNo")).containsExactly(account, second);
		JsonNode balance = response.body().get(0).get("availableBalance");
		assertThat(balance.get("amount").isString()).isTrue();
		assertThat(balance.get("amount").asString()).isEqualTo("3000000");
		assertThat(balance.get("currency").asString()).isEqualTo("VND");
	}

	@Test
	void anotherCustomersAccountLooksLikeItDoesNotExist() {
		long intruder = bank.newCustomer("Kẻ Tò Mò").id();

		Response detail = api.get("/accounts/" + account, intruder);
		Response history = api.get("/accounts/" + account + "/transactions", intruder);

		// 404 rather than 403: the API does not even confirm that the account exists (BOLA/IDOR).
		assertThat(detail.status()).isEqualTo(404);
		assertThat(detail.code()).isEqualTo("ACCOUNT_NOT_FOUND");
		assertThat(history.status()).isEqualTo(404);
	}

	@Test
	void statementIsPaginatedNewestFirstWithAnOpaqueCursor() {
		String other = bank.openAccount(bank.newCustomer("Lý Thường Kiệt").id(), 0);
		for (int i = 1; i <= 4; i++) {
			Response transfer = api.post("/transfers", owner, UUID.randomUUID().toString(),
					Map.of("sourceAccountNo", account, "destinationAccountNo", other, "amount", String.valueOf(i * 1_000),
							"description", "lần " + i));
			assertThat(transfer.status()).isEqualTo(201);
		}

		List<String> amounts = new ArrayList<>();
		String cursor = null;
		int pages = 0;
		do {
			String query = "/accounts/" + account + "/transactions?size=2" + (cursor == null ? "" : "&cursor=" + cursor);
			Response page = api.get(query, owner);
			assertThat(page.status()).isEqualTo(200);
			page.body().get("items").forEach(item -> amounts.add(item.get("amount").get("amount").asString()));
			JsonNode next = page.body().get("nextCursor");
			cursor = next.isNull() ? null : next.asString();
			pages++;
		}
		while (cursor != null);

		// 4 transfers + the opening deposit = 5 lines, newest first, nothing repeated or skipped.
		assertThat(amounts).containsExactly("4000", "3000", "2000", "1000", "3000000");
		assertThat(pages).isEqualTo(3);
	}

	@Test
	void statementLinesShowTheCounterpartyAndTheRunningBalance() {
		String other = bank.openAccount(bank.newCustomer("Đinh Bộ Lĩnh").id(), 0);
		api.post("/transfers", owner, UUID.randomUUID().toString(),
				Map.of("sourceAccountNo", account, "destinationAccountNo", other, "amount", "500000"));

		JsonNode line = api.get("/accounts/" + account + "/transactions?size=1", owner).body().get("items").get(0);

		assertThat(line.get("direction").asString()).isEqualTo("DEBIT");
		assertThat(line.get("counterpartyAccountNo").asString()).isEqualTo(other);
		assertThat(line.get("counterpartyName").asString()).isEqualTo("DINH BO LINH");
		assertThat(line.get("balanceAfter").get("amount").asString()).isEqualTo("2500000");
	}

	@Test
	void holderLookupValidatesTheCheckDigitBeforeQueryingTheDatabase() {
		String mistyped = account.substring(0, 11) + ((account.charAt(11) - '0' + 1) % 10);

		Response ok = api.get("/account-holders/" + account, owner);
		Response typo = api.get("/account-holders/" + mistyped, owner);
		Response internal = api.get("/account-holders/GL-CASH-VND", owner);

		assertThat(ok.text("name")).isEqualTo("NGO BAO CHAU");
		assertThat(typo.status()).isEqualTo(422);
		assertThat(typo.code()).isEqualTo("INVALID_ACCOUNT_NUMBER");
		assertThat(internal.status()).as("internal ledger accounts are never exposed").isEqualTo(422);
	}

	@Test
	void rejectsAForgedCursor() {
		Response response = api.get("/accounts/" + account + "/transactions?cursor=not-a-cursor", owner);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
	}

}
