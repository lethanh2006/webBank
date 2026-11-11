package com.webbank.shared.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;
import java.util.Objects;

/**
 * An amount of money in one currency.
 * <p>
 * The scale always equals the currency's ISO 4217 minor units (VND = 0, USD = 2). An amount with
 * more decimals than the currency allows is rejected instead of being rounded silently, because a
 * silent rounding in a money transfer is a bug that nobody notices until reconciliation.
 * Because the scale is normalised, {@link #equals} behaves like {@link #compareTo}
 * (unlike {@code BigDecimal}, where {@code 1.0} is not equal to {@code 1.00}).
 */
public record Money(BigDecimal amount, Currency currency) implements Comparable<Money> {

	public static final Currency VND = Currency.getInstance("VND");

	public Money {
		Objects.requireNonNull(amount, "amount must not be null");
		Objects.requireNonNull(currency, "currency must not be null");
		int minorUnits = currency.getDefaultFractionDigits();
		if (minorUnits < 0) {
			throw new IllegalArgumentException("Unsupported currency: " + currency);
		}
		try {
			amount = amount.setScale(minorUnits, RoundingMode.UNNECESSARY);
		}
		catch (ArithmeticException ex) {
			throw new IllegalArgumentException("%s allows at most %d decimal places, got %s"
				.formatted(currency.getCurrencyCode(), minorUnits, amount.toPlainString()));
		}
	}

	public static Money of(BigDecimal amount, String currencyCode) {
		return new Money(amount, Currency.getInstance(currencyCode));
	}

	public static Money of(String amount, String currencyCode) {
		return of(new BigDecimal(amount), currencyCode);
	}

	public static Money vnd(long amount) {
		return new Money(BigDecimal.valueOf(amount), VND);
	}

	public static Money zero(Currency currency) {
		return new Money(BigDecimal.ZERO, currency);
	}

	public Money plus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.add(other.amount), currency);
	}

	public Money minus(Money other) {
		requireSameCurrency(other);
		return new Money(amount.subtract(other.amount), currency);
	}

	public Money negate() {
		return new Money(amount.negate(), currency);
	}

	/** Multiplies and rounds back to the currency's minor units with an explicit rounding mode. */
	public Money multiply(BigDecimal factor, RoundingMode rounding) {
		int minorUnits = currency.getDefaultFractionDigits();
		return new Money(amount.multiply(factor).setScale(minorUnits, rounding), currency);
	}

	public boolean isPositive() {
		return amount.signum() > 0;
	}

	public boolean isNegative() {
		return amount.signum() < 0;
	}

	public boolean isZero() {
		return amount.signum() == 0;
	}

	public boolean isGreaterThanOrEqualTo(Money other) {
		return compareTo(other) >= 0;
	}

	public String currencyCode() {
		return currency.getCurrencyCode();
	}

	@Override
	public int compareTo(Money other) {
		requireSameCurrency(other);
		return amount.compareTo(other.amount);
	}

	private void requireSameCurrency(Money other) {
		if (!currency.equals(other.currency)) {
			throw new IllegalArgumentException(
					"Currency mismatch: %s vs %s".formatted(currency, other.currency));
		}
	}

	@Override
	public String toString() {
		return amount.toPlainString() + " " + currency.getCurrencyCode();
	}

}
