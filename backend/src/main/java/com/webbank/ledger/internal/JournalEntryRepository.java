package com.webbank.ledger.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

	@EntityGraph(attributePaths = "postings")
	Optional<JournalEntry> findByReference(String reference);

	boolean existsByReversalOfId(Long reversalOfId);

}
