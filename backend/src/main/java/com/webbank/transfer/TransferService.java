package com.webbank.transfer;

public interface TransferService {

	/**
	 * Moves money between two accounts of this bank in one ACID transaction.
	 * Not idempotent on its own: API callers wrap it with the idempotency module.
	 */
	TransferView transferInternal(long customerId, InternalTransferCommand command);

	/** @throws com.webbank.shared.error.BusinessException TRANSFER_NOT_FOUND for another customer's transfer */
	TransferView get(long customerId, String reference);

}
