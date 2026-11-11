package com.webbank.shared.money;

/**
 * JSON representation of {@link Money}. The amount is a string so that JavaScript clients never
 * parse it into a floating point number.
 */
public record MoneyView(String amount, String currency) {

	public static MoneyView of(Money money) {
		return new MoneyView(money.amount().toPlainString(), money.currencyCode());
	}

}
