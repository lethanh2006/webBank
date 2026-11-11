package com.webbank.system;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Lightweight connectivity check for the frontend.
 * Infrastructure health (database, disk, ...) is exposed separately under /actuator/health.
 */
@RestController
@RequestMapping("/api/v1/system")
class SystemController {

	private final String serviceName;

	SystemController(@Value("${spring.application.name}") String serviceName) {
		this.serviceName = serviceName;
	}

	@GetMapping("/ping")
	PingResponse ping() {
		return new PingResponse("UP", serviceName, Instant.now());
	}

	record PingResponse(String status, String service, Instant serverTime) {
	}

}
