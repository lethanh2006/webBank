package com.webbank.transfer;

import com.webbank.Concurrently;
import com.webbank.IntegrationTest;
import com.webbank.TestBank;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two classic money races, against a real PostgreSQL: concurrent debits of one account
 * (lost update / double spend) and transfers in opposite directions (deadlock).
 */
@IntegrationTest
class TransferConcurrencyIT {

	@Autowired
	TransferService transfers;

	@Autowired
	TestBank bank;

	@AfterEach
	void ledgerStaysConsistent() {
		bank.assertLedgerInvariants();
	}

	@Test
	void concurrentTransfersNeverOverdrawTheSourceAccount() throws InterruptedException {
		long customer = bank.newCustomer("Nguyễn Thị Lan").id();
		String source = bank.openAccount(customer, 500_000);
		String destination = bank.openAccount(customer, 0);

		var outcomes = Concurrently.run(100, 32, i -> transfers.transferInternal(customer,
				new InternalTransferCommand(source, destination, Money.vnd(10_000), "lần " + i)));

		// 500,000 / 10,000 = exactly 50 transfers can succeed, whatever the interleaving.
		assertThat(outcomes.results()).hasSize(50);
		assertThat(outcomes.failures()).hasSize(50)
			.allSatisfy(failure -> assertThat(failure).isInstanceOfSatisfying(BusinessException.class,
					ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS)));
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(0));
		assertThat(bank.balance(destination)).isEqualTo(Money.vnd(500_000));
	}

	@Test
	void transfersInOppositeDirectionsDoNotDeadlock() throws InterruptedException {
		long customer = bank.newCustomer("Hoàng Văn Minh").id();
		String a = bank.openAccount(customer, 1_000_000);
		String b = bank.openAccount(customer, 1_000_000);

		var outcomes = Concurrently.run(200, 32, i -> {
			boolean aToB = i % 2 == 0;
			return transfers.transferInternal(customer,
					new InternalTransferCommand(aToB ? a : b, aToB ? b : a, Money.vnd(1_000), "chéo " + i));
		});

		// Locks are always taken in ascending account id order, so no transaction ever waits in a
		// cycle. Without that ordering PostgreSQL would abort some of them as deadlock victims.
		assertThat(outcomes.failures()).isEmpty();
		assertThat(outcomes.results()).hasSize(200);
		assertThat(bank.balance(a)).isEqualTo(Money.vnd(1_000_000));
		assertThat(bank.balance(b)).isEqualTo(Money.vnd(1_000_000));
	}

}
