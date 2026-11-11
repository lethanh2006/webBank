package com.webbank.shared.error;

import java.util.List;
import java.util.Map;

import com.webbank.shared.web.RequestIdFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every error to an RFC 9457 problem detail carrying a stable {@code code} and the
 * {@code requestId}, so a customer complaint can be matched to the server logs.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ProblemDetail> handleBusiness(BusinessException ex) {
		log.info("Business rule violated: {} - {}", ex.code(), ex.getMessage());
		return problem(ex.code(), ex.getMessage());
	}

	/** Lock wait timeout or deadlock victim: safe to retry with the same Idempotency-Key. */
	@ExceptionHandler(PessimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleLockFailure(PessimisticLockingFailureException ex) {
		log.warn("Could not acquire lock: {}", ex.getMostSpecificCause().getMessage());
		return problem(ErrorCode.ACCOUNT_BUSY, ErrorCode.ACCOUNT_BUSY.defaultMessage());
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex) {
		log.info("Optimistic lock conflict: {}", ex.getMessage());
		return problem(ErrorCode.CONCURRENT_MODIFICATION, ErrorCode.CONCURRENT_MODIFICATION.defaultMessage());
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		// Never leak internal details (SQL, stack traces) to the client.
		return problem(ErrorCode.INTERNAL_ERROR, ErrorCode.INTERNAL_ERROR.defaultMessage());
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, ErrorCode.VALIDATION_FAILED.defaultMessage());
		body.setTitle(ErrorCode.VALIDATION_FAILED.defaultMessage());
		body.setProperty("code", ErrorCode.VALIDATION_FAILED.name());
		List<Map<String, String>> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(error -> Map.of("field", error.getField(), "message", String.valueOf(error.getDefaultMessage())))
			.toList();
		body.setProperty("errors", errors);
		return handleExceptionInternal(ex, body, headers, status, request);
	}

	/** Enriches the problem details produced for Spring MVC's own exceptions (400, 404, 405...). */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
				problem.setProperty("code", "HTTP_" + statusCode.value());
			}
			addRequestId(problem);
		}
		return response;
	}

	private static ResponseEntity<ProblemDetail> problem(ErrorCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(code.status(), detail);
		problem.setTitle(code.defaultMessage());
		problem.setProperty("code", code.name());
		addRequestId(problem);
		return ResponseEntity.status(code.status()).body(problem);
	}

	private static void addRequestId(ProblemDetail problem) {
		String requestId = MDC.get(RequestIdFilter.MDC_KEY);
		if (requestId != null) {
			problem.setProperty("requestId", requestId);
		}
	}

}
