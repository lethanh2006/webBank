package com.webbank.system;

import com.webbank.customer.CustomerService;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

	@Autowired
	MockMvcTester mvc;

	// Needed by the CurrentCustomer argument resolver registered in the MVC slice.
	@MockitoBean
	CustomerService customers;

	@Test
	void pingReportsServiceUpWithIsoTimestamp() {
		var response = assertThat(mvc.get().uri("/api/v1/system/ping")).hasStatusOk().bodyJson();

		response.extractingPath("$.status").isEqualTo("UP");
		response.extractingPath("$.service").isEqualTo("webbank");
		// Instants must be serialized as ISO-8601 strings, never epoch numbers.
		response.extractingPath("$.serverTime").asString().matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
	}

	@Test
	void echoesAValidClientRequestId() {
		assertThat(mvc.get().uri("/api/v1/system/ping").header("X-Request-Id", "abc-12345-xyz"))
			.hasHeader("X-Request-Id", "abc-12345-xyz");
	}

	@Test
	void replacesARequestIdThatCouldInjectLogLines() {
		assertThat(mvc.get().uri("/api/v1/system/ping").header("X-Request-Id", "evil\nFAKE LOG LINE"))
			.headers()
			.hasHeaderSatisfying("X-Request-Id", values -> assertThat(values.getFirst()).matches("[0-9a-f-]{36}"));
	}

	@Test
	void unknownEndpointsReturnProblemDetailsWithCodeAndRequestId() {
		var result = assertThat(mvc.get().uri("/api/v1/does-not-exist").header("X-Request-Id", "req-00000001"));

		result.hasStatus(HttpStatus.NOT_FOUND).hasContentType(MediaType.APPLICATION_PROBLEM_JSON);
		result.bodyJson().extractingPath("$.code").isEqualTo("HTTP_404");
		result.bodyJson().extractingPath("$.requestId").isEqualTo("req-00000001");
	}

}
