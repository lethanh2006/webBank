package com.webbank.customer;

import java.util.List;
import java.util.Optional;

/** Public API of the customer module. */
public interface CustomerService {

	CustomerView register(String fullName, String phone, String email);

	Optional<CustomerView> find(long customerId);

	/** @throws com.webbank.shared.error.BusinessException with CUSTOMER_NOT_FOUND */
	CustomerView get(long customerId);

	List<CustomerView> findAll();

	long count();

}
