package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.List;
import java.util.Collections;

/**
 * Where transactions live.
 *
 * Note what this interface does NOT promise: that saving the same transaction
 * twice results in one row.
 */
public interface LedgerStore {

    void save(NormalizedTxn txn);

    List<NormalizedTxn> all();

    long count();

    default void saveBalanceEvidence(BalanceEvidence evidence) {}

    default List<BalanceEvidence> balanceEvidence() {
        return Collections.emptyList();
    }
}
