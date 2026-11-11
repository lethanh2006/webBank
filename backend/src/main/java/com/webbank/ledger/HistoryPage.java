package com.webbank.ledger;

import java.util.List;

/** A page of an account statement; {@code nextCursor} is null on the last page. */
public record HistoryPage(List<TransactionView> items, String nextCursor) {

}
