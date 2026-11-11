package com.webbank.ledger.internal;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import com.webbank.ledger.Direction;
import com.webbank.ledger.EntryType;
import com.webbank.ledger.JournalEntryRequest;
import com.webbank.ledger.LedgerService;
import com.webbank.ledger.PostedEntry;
import com.webbank.ledger.PostedEntry.PostedLeg;
import com.webbank.ledger.PostingLeg;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.money.Money;
import com.webbank.shared.time.BusinessCalendar;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultLedgerService implements LedgerService {

	private final AccountRepository accounts;

	private final JournalEntryRepository entries;

	private final EntityManager entityManager;

	private final BusinessCalendar calendar;

	DefaultLedgerService(AccountRepository accounts, JournalEntryRepository entries, EntityManager entityManager,
			BusinessCalendar calendar) {
		this.accounts = accounts;
		this.entries = entries;
		this.entityManager = entityManager;
		this.calendar = calendar;
	}

	@Override
	@Transactional
	public PostedEntry post(JournalEntryRequest request) {
		requireBalanced(request.legs());
		Map<String, Account> locked = lockAccounts(request.legs().stream().map(PostingLeg::accountNo).toList());
		JournalEntry entry = new JournalEntry(request.reference(), request.type(), request.description(),
				calendar.today(), null, calendar.now());
		return record(entry, request.legs(), locked);
	}

	@Override
	@Transactional
	public PostedEntry reverse(String reference, String reason) {
		JournalEntry original = entries.findByReference(reference)
			.orElseThrow(() -> new BusinessException(ErrorCode.JOURNAL_ENTRY_NOT_FOUND));
		Map<Long, String> accountNos = accounts
			.findKeysByIds(original.getPostings().stream().map(Posting::getAccountId).toList())
			.stream()
			.collect(Collectors.toMap(AccountKey::id, AccountKey::accountNo));
		List<PostingLeg> legs = original.getPostings()
			.stream()
			.map(posting -> new PostingLeg(accountNos.get(posting.getAccountId()), posting.getDirection().opposite(),
					posting.amount()))
			.toList();

		Map<String, Account> locked = lockAccounts(accountNos.values());
		// Checked after taking the locks: a concurrent reversal of the same entry has committed by now.
		if (entries.existsByReversalOfId(original.getId())) {
			throw new BusinessException(ErrorCode.ENTRY_ALREADY_REVERSED);
		}
		JournalEntry reversal = new JournalEntry(reference + "-RV", EntryType.REVERSAL,
				"Đảo bút toán %s: %s".formatted(reference, reason), calendar.today(), original.getId(), calendar.now());
		return record(reversal, legs, locked);
	}

	/**
	 * Locks every account of the entry with SELECT ... FOR UPDATE, always in ascending id order.
	 * Two transfers A→B and B→A therefore both lock min(A, B) first: no circular wait, no deadlock.
	 * <p>
	 * {@code refresh} re-reads the row while taking the lock. Reading the entity first and locking
	 * it afterwards would be a lost update bug: the persistence context would keep returning the
	 * balance it read before the lock was granted.
	 */
	private Map<String, Account> lockAccounts(Collection<String> accountNos) {
		var distinct = new LinkedHashSet<>(accountNos);
		List<AccountKey> keys = accounts.findKeys(distinct);
		if (keys.size() != distinct.size()) {
			throw new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND);
		}
		Map<String, Account> locked = new LinkedHashMap<>();
		keys.stream().sorted(Comparator.comparing(AccountKey::id)).forEach(key -> {
			Account account = entityManager.getReference(Account.class, key.id());
			entityManager.refresh(account, LockModeType.PESSIMISTIC_WRITE);
			locked.put(key.accountNo(), account);
		});
		return locked;
	}

	private PostedEntry record(JournalEntry entry, List<PostingLeg> legs, Map<String, Account> locked) {
		List<PostedLeg> posted = legs.stream().map(leg -> {
			Account account = locked.get(leg.accountNo());
			Money balanceAfter = leg.direction() == Direction.DEBIT ? account.debit(leg.amount(), entry.getCreatedAt())
					: account.credit(leg.amount(), entry.getCreatedAt());
			entry.addPosting(account, leg.direction(), leg.amount(), balanceAfter);
			return new PostedLeg(leg.accountNo(), leg.direction(), leg.amount(), balanceAfter);
		}).toList();
		entries.save(entry);
		return new PostedEntry(entry.getReference(), entry.getType(), entry.getBusinessDate(), entry.getCreatedAt(),
				posted);
	}

	/** Debits must equal credits in every currency. The database checks this again at commit. */
	static void requireBalanced(List<PostingLeg> legs) {
		if (legs.size() < 2) {
			throw new IllegalArgumentException("A journal entry needs at least two legs");
		}
		Map<Currency, BigDecimal> net = new HashMap<>();
		for (PostingLeg leg : legs) {
			if (!leg.amount().isPositive()) {
				throw new IllegalArgumentException("Posting amounts must be positive: " + leg);
			}
			BigDecimal signed = leg.direction() == Direction.CREDIT ? leg.amount().amount() : leg.amount().amount().negate();
			net.merge(leg.amount().currency(), signed, BigDecimal::add);
		}
		Map<Currency, BigDecimal> unbalanced = net.entrySet()
			.stream()
			.filter(e -> e.getValue().signum() != 0)
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
		if (!unbalanced.isEmpty()) {
			throw new IllegalArgumentException("Journal entry is not balanced: " + unbalanced);
		}
	}

}
