package com.webbank.customer.internal;

import java.util.List;
import java.util.Optional;

import com.webbank.customer.CustomerService;
import com.webbank.customer.CustomerView;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.time.BusinessCalendar;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultCustomerService implements CustomerService {

	private final CustomerRepository customers;

	private final BusinessCalendar calendar;

	DefaultCustomerService(CustomerRepository customers, BusinessCalendar calendar) {
		this.customers = customers;
		this.calendar = calendar;
	}

	@Override
	@Transactional
	public CustomerView register(String fullName, String phone, String email) {
		String cifNo = String.valueOf(customers.nextCifNumber());
		return customers.save(new Customer(cifNo, fullName.strip(), phone, email, calendar.now())).toView();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CustomerView> find(long customerId) {
		return customers.findById(customerId).map(Customer::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public CustomerView get(long customerId) {
		return find(customerId).orElseThrow(() -> new BusinessException(ErrorCode.CUSTOMER_NOT_FOUND));
	}

	@Override
	@Transactional(readOnly = true)
	public List<CustomerView> findAll() {
		return customers.findAll(Sort.by("id")).stream().map(Customer::toView).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public long count() {
		return customers.count();
	}

}
