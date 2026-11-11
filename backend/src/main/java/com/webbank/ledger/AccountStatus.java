package com.webbank.ledger;

public enum AccountStatus {

	/** Can send and receive money. */
	ACTIVE,

	/** Can receive but not send money (e.g. suspected fraud, court order). */
	FROZEN,

	/** Can neither send nor receive money. */
	CLOSED

}
