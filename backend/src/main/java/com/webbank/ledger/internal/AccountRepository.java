package com.webbank.ledger.internal;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface AccountRepository extends JpaRepository<Account, Long> {

	/** Identifiers only: resolving account numbers must not load (and cache) unlocked entities. */
	@Query("select new com.webbank.ledger.internal.AccountKey(a.id, a.accountNo) from Account a where a.accountNo in :accountNos")
	List<AccountKey> findKeys(Collection<String> accountNos);

	@Query("select new com.webbank.ledger.internal.AccountKey(a.id, a.accountNo) from Account a where a.id in :ids")
	List<AccountKey> findKeysByIds(Collection<Long> ids);

	// The read side uses DTO projections, which are never managed by the persistence context.
	String ROW = "select new com.webbank.ledger.internal.AccountRow(a.id, a.accountNo, a.customerId, a.name, a.type,"
			+ " a.currency, a.status, a.ledgerBalance, a.availableBalance) from Account a ";

	@Query(ROW + "where a.customerId = :customerId order by a.id")
	List<AccountRow> findRowsByCustomerId(long customerId);

	@Query(ROW + "where a.accountNo = :accountNo")
	Optional<AccountRow> findRowByAccountNo(String accountNo);

	@Query(value = "select nextval('account_no_seq')", nativeQuery = true)
	long nextAccountSequence();

}
