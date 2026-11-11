package com.webbank.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class MoneyTest {

	@Test
	void scaleFollowsIsoMinorUnits() {
		assertThat(Money.of("1500000", "VND").amount().scale()).isZero();
		// BigDecimal.equals compares scale too, so this also asserts the scale is 2.
		assertThat(Money.of("10.5", "USD").amount()).isEqualTo(new BigDecimal("10.50"));
	}

	@Test
	void rejectsMoreDecimalsThanTheCurrencyAllowsInsteadOfRounding() {
		assertThatIllegalArgumentException().isThrownBy(() -> Money.of("100.5", "VND"))
			.withMessageContaining("VND allows at most 0 decimal places");
		assertThatIllegalArgumentException().isThrownBy(() -> Money.of("1.005", "USD"));
	}

	@Test
	void equalityIgnoresTrailingZerosUnlikeBigDecimal() {
		assertThat(new BigDecimal("1.0")).isNotEqualTo(new BigDecimal("1.00"));
		assertThat(Money.of("1.0", "USD")).isEqualTo(Money.of("1.00", "USD"));
	}

	@Test
	void arithmeticKeepsCurrencyAndScale() {
		Money balance = Money.vnd(500_000);

		assertThat(balance.minus(Money.vnd(200_000))).isEqualTo(Money.vnd(300_000));
		assertThat(balance.plus(Money.vnd(1))).isEqualTo(Money.vnd(500_001));
		assertThat(balance.negate().isNegative()).isTrue();
		assertThat(Money.zero(Money.VND).isZero()).isTrue();
	}

	@Test
	void multiplicationRequiresAnExplicitRoundingMode() {
		Money principal = Money.vnd(1_000_000);
		BigDecimal dailyRate = new BigDecimal("0.05").divide(new BigDecimal("365"), 10, RoundingMode.HALF_EVEN);

		assertThat(principal.multiply(dailyRate, RoundingMode.HALF_EVEN)).isEqualTo(Money.vnd(137));
		assertThat(principal.multiply(dailyRate, RoundingMode.DOWN)).isEqualTo(Money.vnd(136));
	}

	@Test
	void refusesToMixCurrencies() {
		assertThatIllegalArgumentException().isThrownBy(() -> Money.vnd(1).plus(Money.of("1", "USD")))
			.withMessageContaining("Currency mismatch");
		assertThatIllegalArgumentException().isThrownBy(() -> Money.vnd(1).compareTo(Money.of("1", "USD")));
	}

	@Test
	void jsonViewKeepsAmountAsPlainString() {
		assertThat(MoneyView.of(Money.of("1E+6", "VND"))).isEqualTo(new MoneyView("1000000", "VND"));
	}

}
