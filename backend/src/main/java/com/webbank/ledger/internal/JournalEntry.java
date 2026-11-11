package com.webbank.ledger.internal;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import com.webbank.ledger.Direction;
import com.webbank.ledger.EntryType;
import com.webbank.shared.money.Money;

/** A balanced, immutable group of postings. Hibernate never issues UPDATE for it. */
@Entity
@Immutable
@Table(name = "journal_entry")
public class JournalEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(nullable = false)
	private String reference;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EntryType type;

	@Column(nullable = false)
	private String description;

	@Column(name = "business_date", nullable = false)
	private LocalDate businessDate;

	@Column(name = "reversal_of_id")
	private Long reversalOfId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@OneToMany(mappedBy = "journalEntry", cascade = CascadeType.PERSIST)
	@OrderBy("id")
	private List<Posting> postings = new ArrayList<>();

	protected JournalEntry() {
	}

	JournalEntry(String reference, EntryType type, String description, LocalDate businessDate, Long reversalOfId,
			Instant createdAt) {
		this.reference = reference;
		this.type = type;
		this.description = description;
		this.businessDate = businessDate;
		this.reversalOfId = reversalOfId;
		this.createdAt = createdAt;
	}

	void addPosting(Account account, Direction direction, Money amount, Money balanceAfter) {
		postings.add(new Posting(this, account.getId(), direction, amount, balanceAfter, createdAt));
	}

	Long getId() {
		return id;
	}

	String getReference() {
		return reference;
	}

	EntryType getType() {
		return type;
	}

	LocalDate getBusinessDate() {
		return businessDate;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

	List<Posting> getPostings() {
		return postings;
	}

}
