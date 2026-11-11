package com.webbank.ledger.internal;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * Customer account numbers: 12 digits = type prefix (1) + 10 digit sequence + Luhn check digit.
 * The check digit catches most typos (one wrong digit, two swapped digits) before the database
 * is even queried, so a mistyped destination is rejected instead of being sent to a stranger.
 */
final class AccountNumbers {

	private static final Pattern FORMAT = Pattern.compile("\\d{12}");

	private static final Pattern COMBINING_MARKS = Pattern.compile("\\p{M}+");

	private AccountNumbers() {
	}

	static String customerAccountNumber(long sequence) {
		String body = "1" + String.format("%010d", sequence);
		return body + luhnCheckDigit(body);
	}

	static boolean isValid(String accountNo) {
		return accountNo != null && FORMAT.matcher(accountNo).matches()
				&& luhnCheckDigit(accountNo.substring(0, 11)) == accountNo.charAt(11);
	}

	static char luhnCheckDigit(String body) {
		int sum = 0;
		for (int i = 0; i < body.length(); i++) {
			int digit = body.charAt(body.length() - 1 - i) - '0';
			// The check digit will be appended on the right, so the rightmost body digit is doubled.
			if (i % 2 == 0) {
				digit *= 2;
				if (digit > 9) {
					digit -= 9;
				}
			}
			sum += digit;
		}
		return (char) ('0' + (10 - sum % 10) % 10);
	}

	/** "Nguyễn Văn Đức" -> "NGUYEN VAN DUC", the way Vietnamese banks print account names. */
	static String holderName(String fullName) {
		String withoutMarks = COMBINING_MARKS.matcher(Normalizer.normalize(fullName, Normalizer.Form.NFD)).replaceAll("");
		return withoutMarks.replace('đ', 'd').replace('Đ', 'D').toUpperCase(Locale.ROOT).strip().replaceAll("\\s+", " ");
	}

}
