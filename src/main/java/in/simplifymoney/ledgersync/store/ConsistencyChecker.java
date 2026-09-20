package in.simplifymoney.ledgersync.store;

import java.util.List;
import in.simplifymoney.ledgersync.model.NormalizedTxn;

/**
 * Proves the two stores agree, and says precisely where they do not.
 *
 * NOT IMPLEMENTED - this is yours.
 *
 * We will run your checker against a document store we have deliberately
 * altered. It has to find what we changed and name it. A checker that only
 * compares row counts will not.
 */
public final class ConsistencyChecker {

    private final SqlLedgerStore sql;
    private final DocumentStore documents;

    public ConsistencyChecker(SqlLedgerStore sql, DocumentStore documents) {
        this.sql = sql;
        this.documents = documents;
    }

    public List<Divergence> check() {
        List<Divergence> divergences = new java.util.ArrayList<>();
        java.util.Set<String> matchedDocumentKeys = new java.util.HashSet<>();
        for (NormalizedTxn sqlTxn : sql.all()) {
            NormalizedTxn documentTxn = sqlTxn.sourceMessageIds().stream()
                    .map(documents::byMessageId)
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .findFirst()
                    .orElse(null);
            String identity = String.join(",", sqlTxn.sourceMessageIds());
            if (documentTxn == null) {
                divergences.add(new Divergence(identity, describe(sqlTxn), "<missing>"));
                continue;
            }
            matchedDocumentKeys.add(TransactionIdentity.key(documentTxn));
            compare(divergences, identity, "account_last4",
                    sqlTxn.accountLast4(), documentTxn.accountLast4());
            compare(divergences, identity, "occurred_at",
                    sqlTxn.occurredAt().toString(), documentTxn.occurredAt().toString());
            compare(divergences, identity, "direction",
                    sqlTxn.direction().name(), documentTxn.direction().name());
            compare(divergences, identity, "amount",
                    sqlTxn.amount().toPlainString(), documentTxn.amount().toPlainString());
            compare(divergences, identity, "category",
                    sqlTxn.category().name(), documentTxn.category().name());
            compare(divergences, identity, "merchant",
                    sqlTxn.merchant(), documentTxn.merchant());
            compare(divergences, identity, "source_message_ids",
                    new java.util.TreeSet<>(sqlTxn.sourceMessageIds()).toString(),
                    new java.util.TreeSet<>(documentTxn.sourceMessageIds()).toString());
        }
        for (NormalizedTxn documentTxn : documents.all()) {
            if (!matchedDocumentKeys.contains(TransactionIdentity.key(documentTxn))) {
                divergences.add(new Divergence("document-only:" + key(documentTxn),
                        "<missing>", describe(documentTxn)));
            }
        }
        return divergences;
    }

    private static void compare(List<Divergence> out, String identity, String field,
                                String sqlValue, String documentValue) {
        if (!sqlValue.equals(documentValue)) {
            out.add(new Divergence(identity + "." + field, sqlValue, documentValue));
        }
    }

    private static String describe(NormalizedTxn txn) {
        return txn.accountLast4() + "|" + txn.occurredAt() + "|"
                + txn.direction() + "|" + txn.amount().toPlainString();
    }

    private static String key(NormalizedTxn txn) {
        return TransactionIdentity.key(txn);
    }

    /** One place the two stores disagree. */
    public record Divergence(String what, String inSql, String inDocuments) {}
}
