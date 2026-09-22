package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.Locale;

/** Stable document identity used by both migration and document adapters. */
final class TransactionIdentity {

    private TransactionIdentity() {}

    static String key(NormalizedTxn txn) {
        return String.join("|", txn.accountLast4(), txn.occurredAt().toString(),
                txn.direction().name(), txn.amount().toPlainString(),
                txn.merchant().trim().toUpperCase(Locale.ROOT));
    }
}
