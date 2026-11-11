package com.webbank.idempotency.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.regex.Pattern;

import com.webbank.idempotency.IdempotencyService;
import com.webbank.idempotency.IdempotentResult;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.time.BusinessCalendar;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
class DefaultIdempotencyService implements IdempotencyService {

	static final Duration RETENTION = Duration.ofHours(24);

	private static final Pattern VALID_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

	private final JdbcClient jdbc;

	private final TransactionTemplate transactions;

	private final JsonMapper json;

	private final BusinessCalendar calendar;

	DefaultIdempotencyService(JdbcClient jdbc, TransactionTemplate transactions, JsonMapper json,
			BusinessCalendar calendar) {
		this.jdbc = jdbc;
		this.transactions = transactions;
		this.json = json;
		this.calendar = calendar;
	}

	@Override
	public <T> IdempotentResult<T> execute(String scope, String key, Object request, Class<T> responseType,
			Supplier<T> operation) {
		if (key == null || key.isBlank()) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REQUIRED);
		}
		if (!VALID_KEY.matcher(key).matches()) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_INVALID);
		}
		String requestHash = sha256(scope + '\n' + json.writeValueAsString(request));

		return transactions.execute(status -> {
			Instant now = calendar.now();
			// If another transaction inserted the same key and has not committed yet, PostgreSQL
			// makes this INSERT wait on the unique index until that transaction ends. Concurrent
			// duplicates are therefore serialized without any extra lock.
			Optional<Long> insertedId = jdbc.sql("""
					INSERT INTO idempotency_key (scope, idem_key, request_hash, created_at, expires_at)
					VALUES (:scope, :key, :hash, :now, :expiresAt)
					ON CONFLICT (scope, idem_key) DO NOTHING
					RETURNING id""")
				.param("scope", scope)
				.param("key", key)
				.param("hash", requestHash)
				.param("now", utc(now))
				.param("expiresAt", utc(now.plus(RETENTION)))
				.query(Long.class)
				.optional();

			if (insertedId.isEmpty()) {
				return replay(scope, key, requestHash, responseType);
			}
			T response = operation.get();
			jdbc.sql("UPDATE idempotency_key SET response_body = CAST(:body AS jsonb) WHERE id = :id")
				.param("body", json.writeValueAsString(response))
				.param("id", insertedId.get())
				.update();
			return new IdempotentResult<>(response, false);
		});
	}

	private <T> IdempotentResult<T> replay(String scope, String key, String requestHash, Class<T> responseType) {
		StoredKey stored = jdbc.sql("""
				SELECT request_hash, response_body::text AS body
				FROM idempotency_key WHERE scope = :scope AND idem_key = :key""")
			.param("scope", scope)
			.param("key", key)
			.query((rs, rowNum) -> new StoredKey(rs.getString("request_hash"), rs.getString("body")))
			.single();
		if (!stored.requestHash().equals(requestHash)) {
			throw new BusinessException(ErrorCode.IDEMPOTENCY_KEY_REUSED);
		}
		if (stored.responseBody() == null) {
			// Cannot happen: the row only becomes visible once its transaction committed with a response.
			throw new IllegalStateException("Idempotency key without stored response: " + key);
		}
		return new IdempotentResult<>(json.readValue(stored.responseBody(), responseType), true);
	}

	private static OffsetDateTime utc(Instant instant) {
		return instant.atOffset(ZoneOffset.UTC);
	}

	private static String sha256(String value) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private record StoredKey(String requestHash, String responseBody) {
	}

}
