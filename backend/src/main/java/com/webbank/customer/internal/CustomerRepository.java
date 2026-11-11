package com.webbank.customer.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

interface CustomerRepository extends JpaRepository<Customer, Long> {

	@Query(value = "select nextval('cif_no_seq')", nativeQuery = true)
	long nextCifNumber();

}
