package com.webbank.customer.web;

import com.webbank.customer.CurrentCustomer;
import com.webbank.customer.CustomerService;
import com.webbank.customer.CustomerStatus;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;

import org.springframework.core.MethodParameter;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

/**
 * Phase 1 stand-in for authentication: reads the customer id from the
 * {@value #HEADER} header when {@code webbank.demo-auth.enabled=true} (dev and test profiles).
 * Phase 2 replaces this with a resolver that maps the Keycloak JWT subject to a customer, and
 * no controller has to change.
 */
class CurrentCustomerArgumentResolver implements HandlerMethodArgumentResolver {

	static final String HEADER = "X-Demo-Customer-Id";

	private final CustomerService customers;

	private final boolean demoAuthEnabled;

	CurrentCustomerArgumentResolver(CustomerService customers, boolean demoAuthEnabled) {
		this.customers = customers;
		this.demoAuthEnabled = demoAuthEnabled;
	}

	@Override
	public boolean supportsParameter(MethodParameter parameter) {
		return CurrentCustomer.class.equals(parameter.getParameterType());
	}

	@Override
	public CurrentCustomer resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
			NativeWebRequest request, WebDataBinderFactory binderFactory) {
		String header = request.getHeader(HEADER);
		if (!demoAuthEnabled || header == null) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		long customerId;
		try {
			customerId = Long.parseLong(header.strip());
		}
		catch (NumberFormatException ex) {
			throw new BusinessException(ErrorCode.UNAUTHENTICATED);
		}
		return customers.find(customerId)
			.filter(customer -> customer.status() == CustomerStatus.ACTIVE)
			.map(customer -> new CurrentCustomer(customer.id()))
			.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHENTICATED));
	}

}
