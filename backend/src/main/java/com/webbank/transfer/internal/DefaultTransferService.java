package com.webbank.transfer.internal;

import java.time.format.DateTimeFormatter;
import java.util.List;

import com.webbank.ledger.AccountHolder;
import com.webbank.ledger.AccountService;
import com.webbank.ledger.AccountView;
import com.webbank.ledger.EntryType;
import com.webbank.ledger.JournalEntryRequest;
import com.webbank.ledger.LedgerService;
import com.webbank.ledger.PostingLeg;
import com.webbank.shared.error.BusinessException;
import com.webbank.shared.error.ErrorCode;
import com.webbank.shared.money.Money;
import com.webbank.shared.time.BusinessCalendar;
import com.webbank.transfer.InternalTransferCommand;
import com.webbank.transfer.TransferService;
import com.webbank.transfer.TransferView;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class DefaultTransferService implements TransferService {

	private static final DateTimeFormatter REFERENCE_DATE = DateTimeFormatter.ofPattern("yyMMdd");

	private static final String DEFAULT_DESCRIPTION = "Chuyển tiền nội bộ";

	private final TransferRepository transfers;

	private final AccountService accounts;

	private final LedgerService ledger;

	private final BusinessCalendar calendar;

	DefaultTransferService(TransferRepository transfers, AccountService accounts, LedgerService ledger,
			BusinessCalendar calendar) {
		this.transfers = transfers;
		this.accounts = accounts;
		this.ledger = ledger;
		this.calendar = calendar;
	}

	@Override
	@Transactional
	public TransferView transferInternal(long customerId, InternalTransferCommand command) {
		Money amount = command.amount();
		if (!amount.isPositive()) {
			throw new BusinessException(ErrorCode.INVALID_AMOUNT);
		}
		if (command.sourceAccountNo().equals(command.destinationAccountNo())) {
			throw new BusinessException(ErrorCode.SAME_ACCOUNT_TRANSFER);
		}
		// Ownership never changes, so it is safe to check before the ledger takes its locks.
		// Balance and status are only trusted inside the ledger, under the row lock.
		AccountView source = accounts.getOwnedAccount(customerId, command.sourceAccountNo());
		AccountHolder destination = accounts.lookupHolder(command.destinationAccountNo());
		if (!source.ledgerBalance().currency().equals(amount.currency())
				|| !destination.currency().equals(amount.currency())) {
			throw new BusinessException(ErrorCode.CURRENCY_MISMATCH);
		}

		String reference = nextReference();
		String description = (command.description() == null || command.description().isBlank()) ? DEFAULT_DESCRIPTION
				: command.description().strip();
		ledger.post(new JournalEntryRequest(reference, EntryType.INTERNAL_TRANSFER, description,
				List.of(PostingLeg.debit(source.accountNo(), amount), PostingLeg.credit(destination.accountNo(), amount))));

		Transfer transfer = Transfer.completedInternal(reference, customerId, source.accountNo(),
				destination.accountNo(), destination.name(), amount, description, calendar.now());
		return transfers.save(transfer).toView();
	}

	@Override
	@Transactional(readOnly = true)
	public TransferView get(long customerId, String reference) {
		return transfers.findByReferenceAndCustomerId(reference, customerId)
			.map(Transfer::toView)
			.orElseThrow(() -> new BusinessException(ErrorCode.TRANSFER_NOT_FOUND));
	}

	/** FT + business date + sequence, e.g. FT26111100000042. Unique across instances via a DB sequence. */
	private String nextReference() {
		long sequence = transfers.nextReferenceSequence() % 100_000_000;
		return "FT%s%08d".formatted(calendar.today().format(REFERENCE_DATE), sequence);
	}

}
