package com.webbank.ledger;

import java.util.List;
import java.util.Objects;

/** A balanced set of postings to record atomically under one business reference. */
public record JournalEntryRequest(String reference, EntryType type, String description, List<PostingLeg> legs) {

	public JournalEntryRequest {
		Objects.requireNonNull(reference, "reference");
		Objects.requireNonNull(type, "type");
		Objects.requireNonNull(description, "description");
		legs = List.copyOf(legs);
	}

}
