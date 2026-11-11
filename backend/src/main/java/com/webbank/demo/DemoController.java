package com.webbank.demo;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.webbank.customer.CustomerService;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Dev profile only: lets the frontend switch between demo customers until login exists. */
@RestController
@Profile("dev")
@RequestMapping("/api/v1/demo")
@Tag(name = "Demo (dev profile only)")
class DemoController {

	private final CustomerService customers;

	DemoController(CustomerService customers) {
		this.customers = customers;
	}

	@GetMapping("/customers")
	@SecurityRequirements
	@Operation(summary = "Demo customers to impersonate with the X-Demo-Customer-Id header")
	List<DemoCustomer> customers() {
		return customers.findAll().stream().map(c -> new DemoCustomer(c.id(), c.cifNo(), c.fullName())).toList();
	}

	record DemoCustomer(long id, String cifNo, String fullName) {
	}

}
