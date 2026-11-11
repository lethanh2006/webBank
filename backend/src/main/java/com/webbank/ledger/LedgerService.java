package com.webbank.ledger;

/**
 * The only way to move money. Every change of a balance goes through {@link #post}, which locks
 * the accounts involved, checks that the entry balances, writes immutable postings and updates
 * balances in the caller's transaction.
 */
public interface LedgerService {

	PostedEntry post(JournalEntryRequest request);

	/**
	 * Records an entry with every posting of the original entry in the opposite direction.
	 * Postings are never updated or deleted; a reversal is how a mistake is corrected.
	 */
	PostedEntry reverse(String reference, String reason);

}
