package com.webbank.transfer.web;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Instant;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import com.webbank.customer.CurrentCustomer;
import com.webbank.idempotency.IdempotencyService;
import com.webbank.idempotency.IdempotentResult;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.money.Money;
import com.webbank.shared.money.MoneyView;
import com.webbank.transfer.InternalTransferCommand;
import com.webbank.transfer.TransferService;
import com.webbank.transfer.TransferStatus;
import com.webbank.transfer.TransferType;
import com.webbank.transfer.TransferView;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/transfers")
@Tag(name = "Transfers")
class TransferController {

	static final String IDEMPOTENCY_KEY = "Idempotency-Key";

	static final String REPLAYED = "Idempotent-Replayed";

	private final TransferService transfers;

	private final IdempotencyService idempotency;

	TransferController(TransferService transfers, IdempotencyService idempotency) {
		this.transfers = transfers;
		this.idempotency = idempotency;
	}

	@PostMapping
	@Operation(summary = "Internal transfer. Retrying with the same Idempotency-Key never moves money twice.")
	ResponseEntity<TransferResponse> create(@Parameter(hidden = true) CurrentCustomer customer,
			@RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
			@Valid @RequestBody TransferRequest request) {
		InternalTransferCommand command = request.toCommand();
		IdempotentResult<TransferResponse> result = idempotency.execute("transfer:customer-" + customer.id(),
				idempotencyKey, command, TransferResponse.class,
				() -> TransferResponse.of(transfers.transferInternal(customer.id(), command)));
		return ResponseEntity.created(URI.create("/api/v1/transfers/" + result.response().reference()))
			.header(REPLAYED, String.valueOf(result.replayed()))
			.body(result.response());
	}

	@GetMapping("/{reference}")
	@Operation(summary = "A transfer of the current customer")
	TransferResponse get(@Parameter(hidden = true) CurrentCustomer customer, @PathVariable String reference) {
		return TransferResponse.of(transfers.get(customer.id(), reference));
	}

	record TransferRequest(@NotBlank @Size(max = 20) String sourceAccountNo,
			@NotBlank @Size(max = 20) String destinationAccountNo,
			@NotNull @Positive BigDecimal amount,
			@Pattern(regexp = "[A-Z]{3}") String currency,
			@Size(max = 140) String description) {

		InternalTransferCommand toCommand() {
			Money money;
			try {
				money = Money.of(amount, currency == null ? "VND" : currency);
			}
			catch (IllegalArgumentException ex) {
				// e.g. 100.5 VND: the currency has no minor unit, refuse instead of rounding.
				throw new BusinessException(ErrorCode.INVALID_AMOUNT, ex.getMessage());
			}
			return new InternalTransferCommand(sourceAccountNo.strip(), destinationAccountNo.strip(), money,
					description);
		}

	}

	record TransferResponse(String reference, TransferType type, TransferStatus status, String sourceAccountNo,
			String destinationAccountNo, String destinationName, MoneyView amount, String description,
			Instant createdAt, Instant completedAt) {

		static TransferResponse of(TransferView view) {
			return new TransferResponse(view.reference(), view.type(), view.status(), view.sourceAccountNo(),
					view.destinationAccountNo(), view.destinationName(), MoneyView.of(view.amount()),
					view.description(), view.createdAt(), view.completedAt());
		}

	}

}
