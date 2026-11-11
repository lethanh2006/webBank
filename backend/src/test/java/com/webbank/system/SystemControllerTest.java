package com.webbank.system;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import static org.assertj.core.api.Assertions.assertThat;

@WebMvcTest(SystemController.class)
class SystemControllerTest {

	@Autowired
	MockMvcTester mvc;

	@Test
	void pingReportsServiceUpWithIsoTimestamp() {
		var response = assertThat(mvc.get().uri("/api/v1/system/ping")).hasStatusOk().bodyJson();

		response.extractingPath("$.status").isEqualTo("UP");
		response.extractingPath("$.service").isEqualTo("webbank");
		// Instants must be serialized as ISO-8601 strings, never epoch numbers.
		response.extractingPath("$.serverTime").asString().matches("\\d{4}-\\d{2}-\\d{2}T.*Z");
	}

}
