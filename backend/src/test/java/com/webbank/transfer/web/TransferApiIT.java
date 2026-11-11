package com.webbank.transfer.web;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.webbank.ApiClient;
import com.webbank.ApiClient.Response;
import com.webbank.Concurrently;
import com.webbank.IntegrationTest;
import com.webbank.TestBank;
import com.webbank.shared.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import static org.assertj.core.api.Assertions.assertThat;

@IntegrationTest
class TransferApiIT {

	@Value("${local.server.port}")
	int port;

	@Autowired
	JsonMapper json;

	@Autowired
	TestBank bank;

	ApiClient api;

	long customer;

	String source;

	String destination;

	@BeforeEach
	void setUp() {
		api = new ApiClient(port, json);
		customer = bank.newCustomer("Phan Thanh Nam").id();
		source = bank.openAccount(customer, 1_000_000);
		destination = bank.openAccount(bank.newCustomer("Trịnh Thu Trang").id(), 0);
	}

	@AfterEach
	void ledgerStaysConsistent() {
		bank.assertLedgerInvariants();
	}

	@Test
	void createsATransferAndShowsTheDestinationHolder() {
		Response response = transfer(newKey(), 250_000);

		assertThat(response.status()).isEqualTo(201);
		assertThat(response.text("reference")).matches("FT\\d{14}");
		assertThat(response.headers().getLocation()).hasPath("/api/v1/transfers/" + response.text("reference"));
		assertThat(response.text("destinationName")).isEqualTo("TRINH THU TRANG");
		assertThat(response.body().get("amount").get("amount").asString()).isEqualTo("250000");
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(750_000));
		assertThat(api.get("/transfers/" + response.text("reference"), customer).status()).isEqualTo(200);
	}

	@Test
	void retryWithTheSameKeyReturnsTheSameResponseAndMovesMoneyOnce() {
		String key = newKey();

		Response first = transfer(key, 100_000);
		Response retry = transfer(key, 100_000);

		assertThat(retry.status()).isEqualTo(201);
		assertThat(retry.headers().getFirst("Idempotent-Replayed")).isEqualTo("true");
		assertThat(first.headers().getFirst("Idempotent-Replayed")).isEqualTo("false");
		assertThat(retry.body()).isEqualTo(first.body());
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(900_000));
	}

	@Test
	void concurrentRetriesWithTheSameKeyExecuteExactlyOnce() throws InterruptedException {
		String key = newKey();

		var outcomes = Concurrently.run(20, 20, i -> transfer(key, 50_000));

		assertThat(outcomes.failures()).isEmpty();
		assertThat(outcomes.results()).extracting(Response::status).containsOnly(201);
		assertThat(outcomes.results()).extracting(Response::body).containsOnly(outcomes.results().getFirst().body());
		assertThat(outcomes.results()).filteredOn(r -> "false".equals(r.headers().getFirst("Idempotent-Replayed")))
			.hasSize(1);
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(950_000));
		assertThat(bank.balance(destination)).isEqualTo(Money.vnd(50_000));
	}

	@Test
	void reusingAKeyForADifferentRequestIsRejected() {
		String key = newKey();
		transfer(key, 100_000);

		Response response = transfer(key, 200_000);

		assertThat(response.status()).isEqualTo(422);
		assertThat(response.code()).isEqualTo("IDEMPOTENCY_KEY_REUSED");
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(900_000));
	}

	@Test
	void writesWithoutAnIdempotencyKeyAreRejected() {
		Response response = transfer(null, 100_000);

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("IDEMPOTENCY_KEY_REQUIRED");
		assertThat(response.body().has("requestId")).isTrue();
	}

	@Test
	void aFailedTransferStoresNoResultSoTheSameKeyCanBeRetriedLater() {
		String key = newKey();

		Response tooMuch = transfer(key, 1_500_000);
		bank.deposit(source, 1_000_000);
		Response retried = transfer(key, 1_500_000);

		assertThat(tooMuch.status()).isEqualTo(422);
		assertThat(tooMuch.code()).isEqualTo("INSUFFICIENT_FUNDS");
		assertThat(retried.status()).isEqualTo(201);
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(500_000));
	}

	@Test
	void cannotSendMoneyFromAnotherCustomersAccount() {
		long intruder = bank.newCustomer("Kẻ Gian").id();

		Response response = api.post("/transfers", intruder, newKey(), body(source, destination, "100000"));

		assertThat(response.status()).isEqualTo(404);
		assertThat(response.code()).isEqualTo("ACCOUNT_NOT_FOUND");
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(1_000_000));
	}

	@Test
	void rejectsAmountsWithDecimalsForVnd() {
		Response response = api.post("/transfers", customer, newKey(), body(source, destination, "1000.5"));

		assertThat(response.status()).isEqualTo(422);
		assertThat(response.code()).isEqualTo("INVALID_AMOUNT");
	}

	@Test
	void rejectsInvalidInputWithFieldErrors() {
		Response response = api.post("/transfers", customer, newKey(), body("", destination, "-5"));

		assertThat(response.status()).isEqualTo(400);
		assertThat(response.code()).isEqualTo("VALIDATION_FAILED");
		assertThat(response.body().get("errors").findValuesAsString("field")).contains("sourceAccountNo", "amount");
	}

	@Test
	void rejectsUnauthenticatedCalls() {
		Response response = api.post("/transfers", null, newKey(), body(source, destination, "1000"));

		assertThat(response.status()).isEqualTo(401);
		assertThat(response.code()).isEqualTo("UNAUTHENTICATED");
	}

	private Response transfer(String key, long amount) {
		return api.post("/transfers", customer, key, body(source, destination, String.valueOf(amount)));
	}

	private static Map<String, Object> body(String from, String to, String amount) {
		Map<String, Object> body = new HashMap<>();
		body.put("sourceAccountNo", from);
		body.put("destinationAccountNo", to);
		body.put("amount", amount);
		body.put("description", "Trả tiền nhà");
		return body;
	}

	private static String newKey() {
		return UUID.randomUUID().toString();
	}

}
