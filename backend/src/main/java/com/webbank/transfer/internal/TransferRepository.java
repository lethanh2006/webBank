package com.webbank.transfer.internal;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface TransferRepository extends JpaRepository<Transfer, Long> {

	Optional<Transfer> findByReferenceAndCustomerId(String reference, Long customerId);

	@Query(value = "select nextval('transfer_ref_seq')", nativeQuery = true)
	long nextReferenceSequence();

}
