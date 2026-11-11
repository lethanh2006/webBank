package com.webbank.ledger;

import java.util.List;
import java.util.UUID;

import com.webbank.IntegrationTest;
import com.webbank.TestBank;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.money.Money;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class LedgerServiceIT {

	@Autowired
	LedgerService ledger;

	@Autowired
	TestBank bank;

	@Autowired
	JdbcClient jdbc;

	@Autowired
	TransactionTemplate transactionTemplate;

	@AfterEach
	void ledgerStaysConsistent() {
		bank.assertLedgerInvariants();
	}

	@Test
	void postsABalancedEntryAndRecordsBalanceAfterEachPosting() {
		long customer = bank.newCustomer("Nguyễn Văn An").id();
		String source = bank.openAccount(customer, 1_000_000);
		String destination = bank.openAccount(customer, 0);

		PostedEntry entry = ledger.post(transfer(source, destination, 300_000));

		assertThat(entry.postings()).extracting(PostedEntry.PostedLeg::balanceAfter)
			.containsExactly(Money.vnd(700_000), Money.vnd(300_000));
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(700_000));
		assertThat(bank.balance(destination)).isEqualTo(Money.vnd(300_000));
	}

	@Test
	void rejectsAnUnbalancedEntryBeforeTouchingAnyAccount() {
		long customer = bank.newCustomer("Trần Thị Bình").id();
		String account = bank.openAccount(customer, 100_000);

		var unbalanced = new JournalEntryRequest(reference(), EntryType.INTERNAL_TRANSFER, "lệch",
				List.of(PostingLeg.debit(account, Money.vnd(50_000)), PostingLeg.credit(LedgerAccounts.CASH_VND, Money.vnd(40_000))));

		assertThatIllegalArgumentException().isThrownBy(() -> ledger.post(unbalanced)).withMessageContaining("not balanced");
		assertThat(bank.balance(account)).isEqualTo(Money.vnd(100_000));
	}

	@Test
	void insufficientFundsRollsBackTheWholeEntry() {
		long customer = bank.newCustomer("Lê Hoàng Cường").id();
		String source = bank.openAccount(customer, 100_000);
		String destination = bank.openAccount(customer, 0);
		JournalEntryRequest request = transfer(source, destination, 100_001);

		assertThatThrownBy(() -> ledger.post(request)).isInstanceOfSatisfying(BusinessException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.INSUFFICIENT_FUNDS));
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(100_000));
		assertThat(bank.postingCount(destination)).isZero();
		assertThat(journalEntryExists(request.reference())).isFalse();
	}

	@Test
	void frozenAccountsCanReceiveButNotSendMoney() {
		long customer = bank.newCustomer("Phạm Minh Dũng").id();
		String frozen = bank.openAccount(customer, 500_000);
		String other = bank.openAccount(customer, 500_000);
		jdbc.sql("UPDATE account SET status = 'FROZEN' WHERE account_no = ?").param(frozen).update();

		assertThatThrownBy(() -> ledger.post(transfer(frozen, other, 1_000))).isInstanceOfSatisfying(
				BusinessException.class, ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ACCOUNT_NOT_ACTIVE));
		ledger.post(transfer(other, frozen, 1_000));
		assertThat(bank.balance(frozen)).isEqualTo(Money.vnd(501_000));
	}

	@Test
	void postingsAreAppendOnly() {
		long customer = bank.newCustomer("Võ Thị Én").id();
		String account = bank.openAccount(customer, 200_000);

		assertThatThrownBy(() -> jdbc.sql("""
				UPDATE posting SET amount = 1
				WHERE account_id = (SELECT id FROM account WHERE account_no = ?)""").param(account).update())
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
		assertThatThrownBy(() -> jdbc.sql("DELETE FROM journal_entry WHERE type = 'DEPOSIT'").update())
			.isInstanceOf(DataAccessException.class)
			.hasMessageContaining("append-only");
	}

	@Test
	void databaseRejectsAnUnbalancedEntryAtCommitEvenIfTheApplicationIsBypassed() {
		long customer = bank.newCustomer("Đỗ Văn Giang").id();
		String account = bank.openAccount(customer, 0);

		assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
			long entryId = jdbc.sql("""
					INSERT INTO journal_entry (reference, type, description, business_date, created_at)
					VALUES (?, 'DEPOSIT', 'tiền từ trên trời rơi xuống', current_date, now()) RETURNING id""")
				.param(reference())
				.query(Long.class)
				.single();
			jdbc.sql("""
					INSERT INTO posting (journal_entry_id, account_id, direction, amount, currency, balance_after, created_at)
					SELECT ?, id, 'CREDIT', 1000000, 'VND', 1000000, now() FROM account WHERE account_no = ?""")
				.params(entryId, account)
				.update();
		})).rootCause().hasMessageContaining("is not balanced in VND");
		assertThat(bank.postingCount(account)).isZero();
	}

	@Test
	void reversalRestoresBalancesAndCanOnlyHappenOnce() {
		long customer = bank.newCustomer("Bùi Thị Hoa").id();
		String source = bank.openAccount(customer, 1_000_000);
		String destination = bank.openAccount(customer, 0);
		String reference = ledger.post(transfer(source, destination, 250_000)).reference();

		PostedEntry reversal = ledger.reverse(reference, "khách báo chuyển nhầm");

		assertThat(reversal.type()).isEqualTo(EntryType.REVERSAL);
		assertThat(bank.balance(source)).isEqualTo(Money.vnd(1_000_000));
		assertThat(bank.balance(destination)).isEqualTo(Money.vnd(0));
		assertThat(bank.postingCount(source)).as("original postings are kept, not deleted").isEqualTo(3);
		assertThatThrownBy(() -> ledger.reverse(reference, "lần hai")).isInstanceOfSatisfying(BusinessException.class,
				ex -> assertThat(ex.code()).isEqualTo(ErrorCode.ENTRY_ALREADY_REVERSED));
	}

	private boolean journalEntryExists(String reference) {
		return jdbc.sql("SELECT count(*) FROM journal_entry WHERE reference = ?").param(reference).query(Long.class).single() > 0;
	}

	private static JournalEntryRequest transfer(String from, String to, long amount) {
		return new JournalEntryRequest(reference(), EntryType.INTERNAL_TRANSFER, "test",
				List.of(PostingLeg.debit(from, Money.vnd(amount)), PostingLeg.credit(to, Money.vnd(amount))));
	}

	private static String reference() {
		return "T-" + UUID.randomUUID().toString().substring(0, 18);
	}

}
