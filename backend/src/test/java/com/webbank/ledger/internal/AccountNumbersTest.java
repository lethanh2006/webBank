package com.webbank.ledger.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AccountNumbersTest {

	@Test
	void generatedNumbersHaveTwelveDigitsAndAValidCheckDigit() {
		String accountNo = AccountNumbers.customerAccountNumber(42);

		assertThat(accountNo).hasSize(12).startsWith("10000000042");
		assertThat(AccountNumbers.isValid(accountNo)).isTrue();
	}

	@Test
	void detectsASingleMistypedDigit() {
		String accountNo = AccountNumbers.customerAccountNumber(123_456);
		for (int position = 0; position < accountNo.length(); position++) {
			char original = accountNo.charAt(position);
			char typo = original == '9' ? '0' : (char) (original + 1);
			String mistyped = accountNo.substring(0, position) + typo + accountNo.substring(position + 1);
			assertThat(AccountNumbers.isValid(mistyped)).as("typo at position %d: %s", position, mistyped).isFalse();
		}
	}

	@Test
	void detectsTwoSwappedNeighbouringDigits() {
		String accountNo = AccountNumbers.customerAccountNumber(1_234_567);
		String swapped = accountNo.substring(0, 7) + accountNo.charAt(8) + accountNo.charAt(7) + accountNo.substring(9);

		assertThat(swapped).isNotEqualTo(accountNo);
		assertThat(AccountNumbers.isValid(swapped)).isFalse();
	}

	@Test
	void rejectsMalformedInput() {
		assertThat(AccountNumbers.isValid(null)).isFalse();
		assertThat(AccountNumbers.isValid("12345")).isFalse();
		assertThat(AccountNumbers.isValid("GL-CASH-VND")).isFalse();
	}

	@Test
	void holderNameIsUppercaseWithoutVietnameseDiacritics() {
		assertThat(AccountNumbers.holderName("  Nguyễn Văn   Đức ")).isEqualTo("NGUYEN VAN DUC");
		assertThat(AccountNumbers.holderName("Trần Thị Bình")).isEqualTo("TRAN THI BINH");
	}

}
