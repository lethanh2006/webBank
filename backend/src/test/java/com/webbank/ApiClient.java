package com.webbank;

import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

/** Calls the API over real HTTP (Tomcat on a random port), never throwing on 4xx/5xx. */
public class ApiClient {

	public static final String CUSTOMER_HEADER = "X-Demo-Customer-Id";

	private final RestClient rest;

	private final JsonMapper json;

	public ApiClient(int port, JsonMapper json) {
		this.rest = RestClient.builder()
			.baseUrl("http://localhost:" + port + "/api/v1")
			.defaultStatusHandler(status -> true, (request, response) -> {
			})
			.build();
		this.json = json;
	}

	public Response get(String path, Long customerId) {
		ResponseEntity<String> entity = rest.get()
			.uri(path)
			.headers(headers -> authenticate(headers, customerId))
			.retrieve()
			.toEntity(String.class);
		return toResponse(entity);
	}

	public Response post(String path, Long customerId, String idempotencyKey, Map<String, ?> body) {
		ResponseEntity<String> entity = rest.post()
			.uri(path)
			.headers(headers -> {
				authenticate(headers, customerId);
				if (idempotencyKey != null) {
					headers.set("Idempotency-Key", idempotencyKey);
				}
			})
			.contentType(MediaType.APPLICATION_JSON)
			.body(body)
			.retrieve()
			.toEntity(String.class);
		return toResponse(entity);
	}

	private static void authenticate(HttpHeaders headers, Long customerId) {
		if (customerId != null) {
			headers.set(CUSTOMER_HEADER, customerId.toString());
		}
	}

	private Response toResponse(ResponseEntity<String> entity) {
		String raw = entity.getBody();
		JsonNode body = (raw == null || raw.isBlank()) ? null : json.readTree(raw);
		return new Response(entity.getStatusCode().value(), entity.getHeaders(), raw, body);
	}

	public record Response(int status, HttpHeaders headers, String raw, JsonNode body) {

		public String code() {
			return body.get("code").asString();
		}

		public String text(String field) {
			return body.get(field).asString();
		}

	}

}
