package com.webbank.shared.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine readable error codes. Clients branch on {@code code}, never on the message text.
 */
public enum ErrorCode {

	VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "Dữ liệu không hợp lệ"),
	IDEMPOTENCY_KEY_REQUIRED(HttpStatus.BAD_REQUEST, "Thiếu header Idempotency-Key"),
	IDEMPOTENCY_KEY_INVALID(HttpStatus.BAD_REQUEST, "Idempotency-Key không đúng định dạng"),
	IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT,
			"Idempotency-Key đã được dùng cho một yêu cầu có nội dung khác"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Chưa xác thực"),
	CUSTOMER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy khách hàng"),
	ACCOUNT_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy tài khoản"),
	INVALID_ACCOUNT_NUMBER(HttpStatus.UNPROCESSABLE_CONTENT, "Số tài khoản không hợp lệ"),
	ACCOUNT_NOT_ACTIVE(HttpStatus.UNPROCESSABLE_CONTENT, "Tài khoản không ở trạng thái hoạt động"),
	CURRENCY_MISMATCH(HttpStatus.UNPROCESSABLE_CONTENT, "Loại tiền không khớp với tài khoản"),
	INVALID_AMOUNT(HttpStatus.UNPROCESSABLE_CONTENT, "Số tiền không hợp lệ"),
	INSUFFICIENT_FUNDS(HttpStatus.UNPROCESSABLE_CONTENT, "Số dư khả dụng không đủ"),
	SAME_ACCOUNT_TRANSFER(HttpStatus.UNPROCESSABLE_CONTENT, "Tài khoản nguồn và đích trùng nhau"),
	JOURNAL_ENTRY_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy bút toán"),
	ENTRY_ALREADY_REVERSED(HttpStatus.CONFLICT, "Bút toán đã được đảo trước đó"),
	TRANSFER_NOT_FOUND(HttpStatus.NOT_FOUND, "Không tìm thấy giao dịch"),
	ACCOUNT_BUSY(HttpStatus.CONFLICT, "Tài khoản đang xử lý giao dịch khác, vui lòng thử lại"),
	CONCURRENT_MODIFICATION(HttpStatus.CONFLICT, "Dữ liệu vừa được người khác cập nhật, vui lòng thử lại"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Lỗi hệ thống, vui lòng thử lại sau");

	private final HttpStatus status;

	private final String defaultMessage;

	ErrorCode(HttpStatus status, String defaultMessage) {
		this.status = status;
		this.defaultMessage = defaultMessage;
	}

	public HttpStatus status() {
		return status;
	}

	public String defaultMessage() {
		return defaultMessage;
	}

}
