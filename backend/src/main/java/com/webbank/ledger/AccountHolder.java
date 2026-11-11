package com.webbank.ledger;

import java.util.Currency;

/** What a sender may see about a destination account before confirming a transfer. */
public record AccountHolder(String accountNo, String name, Currency currency) {

}
