package com.webbank.shared.error;

/**
 * An expected business rule violation (insufficient funds, unknown account...).
 * Unchecked so that it rolls back the surrounding transaction by default.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode code;

	public BusinessException(ErrorCode code) {
		this(code, code.defaultMessage());
	}

	public BusinessException(ErrorCode code, String message) {
		super(message);
		this.code = code;
	}

	public ErrorCode code() {
		return code;
	}

}
