package com.webbank.idempotency;

import java.util.function.Supplier;

/**
 * Executes an operation at most once per (scope, client key).
 * <p>
 * The key and the operation's response are written in the same database transaction as the
 * operation itself: either both exist or neither does. A retry with the same key and the same
 * request receives the stored response; the same key with a different request is rejected.
 */
public interface IdempotencyService {

	/**
	 * @param scope who and what the key belongs to, e.g. {@code transfer:customer-42}
	 * @param key the client generated Idempotency-Key header
	 * @param request the normalized request, hashed to detect a key reused for another request
	 * @param responseType type used to read a stored response back
	 * @param operation the business operation; it joins the idempotency transaction
	 */
	<T> IdempotentResult<T> execute(String scope, String key, Object request, Class<T> responseType,
			Supplier<T> operation);

}
