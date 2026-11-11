package com.webbank.ledger.internal;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.webbank.ledger.Direction;
import com.webbank.ledger.EntryType;
import com.webbank.ledger.TransactionView;
import com.webbank.shared.money.Money;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Account statement read model in plain SQL: a LATERAL join finds the counterparty of each
 * posting, and keyset pagination ({@code id < :beforeId}) stays fast on page 1000 where
 * OFFSET would scan and discard every previous row.
 */
@Component
class TransactionHistoryQuery {

	private static final String SQL = """
			SELECT p.id, je.reference, je.type, je.description, p.direction, p.amount, p.currency,
			       p.balance_after, p.created_at, cp.account_no AS counterparty_no, cp.name AS counterparty_name
			FROM posting p
			JOIN journal_entry je ON je.id = p.journal_entry_id
			LEFT JOIN LATERAL (
			    SELECT a.account_no, a.name
			    FROM posting other
			    JOIN account a ON a.id = other.account_id
			    WHERE other.journal_entry_id = p.journal_entry_id AND other.account_id <> p.account_id
			    ORDER BY other.id
			    LIMIT 1
			) cp ON TRUE
			WHERE p.account_id = :accountId AND p.id < :beforeId
			ORDER BY p.id DESC
			LIMIT :limit
			""";

	private final JdbcClient jdbc;

	TransactionHistoryQuery(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	List<TransactionView> page(long accountId, long beforeId, int limit) {
		return jdbc.sql(SQL)
			.param("accountId", accountId)
			.param("beforeId", beforeId)
			.param("limit", limit)
			.query((rs, rowNum) -> map(rs))
			.list();
	}

	private static TransactionView map(ResultSet rs) throws SQLException {
		String currency = rs.getString("currency");
		return new TransactionView(rs.getLong("id"), rs.getString("reference"), EntryType.valueOf(rs.getString("type")),
				rs.getString("description"), Direction.valueOf(rs.getString("direction")),
				Money.of(rs.getBigDecimal("amount"), currency), Money.of(rs.getBigDecimal("balance_after"), currency),
				rs.getString("counterparty_no"), rs.getString("counterparty_name"),
				rs.getTimestamp("created_at").toInstant());
	}

}
