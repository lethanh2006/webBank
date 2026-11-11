package com.webbank.idempotency;

/** @param replayed true when the response was read back from an earlier execution */
public record IdempotentResult<T>(T response, boolean replayed) {

}
