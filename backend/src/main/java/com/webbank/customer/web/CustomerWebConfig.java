package com.webbank.customer.web;

import java.util.List;

import com.webbank.customer.CustomerService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration(proxyBeanMethods = false)
class CustomerWebConfig implements WebMvcConfigurer {

	private final CustomerService customers;

	private final boolean demoAuthEnabled;

	CustomerWebConfig(CustomerService customers, @Value("${webbank.demo-auth.enabled:false}") boolean demoAuthEnabled) {
		this.customers = customers;
		this.demoAuthEnabled = demoAuthEnabled;
	}

	@Override
	public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
		resolvers.add(new CurrentCustomerArgumentResolver(customers, demoAuthEnabled));
	}

}
