package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.TreeSet;

/** Used by SelfCheck and by tests. Keeps everything it is given. */
public final class InMemoryLedgerStore implements LedgerStore {

    private final List<NormalizedTxn> rows = new ArrayList<>();
    private final List<BalanceEvidence> balances = new ArrayList<>();

    @Override
    public void save(NormalizedTxn txn) {
        for (int i = 0; i < rows.size(); i++) {
            NormalizedTxn existing = rows.get(i);
            if (!sameTransaction(existing, txn)) continue;
            TreeSet<String> ids = new TreeSet<>(existing.sourceMessageIds());
            ids.addAll(txn.sourceMessageIds());
            rows.set(i, new NormalizedTxn(existing.accountLast4(), existing.occurredAt(),
                    existing.direction(), existing.amount(), existing.category(),
                    existing.merchant(), List.copyOf(ids)));
            return;
        }
        rows.add(txn);
    }

    private boolean sameTransaction(NormalizedTxn a, NormalizedTxn b) {
        return a.accountLast4().equals(b.accountLast4())
                && a.occurredAt().equals(b.occurredAt())
                && a.direction() == b.direction()
                && a.amount().compareTo(b.amount()) == 0
                && a.merchant().equalsIgnoreCase(b.merchant());
    }

    @Override public List<NormalizedTxn> all() { return Collections.unmodifiableList(rows); }

    @Override public long count() { return rows.size(); }

    @Override public void saveBalanceEvidence(BalanceEvidence evidence) {
        if (!balances.contains(evidence)) balances.add(evidence);
    }

    @Override public List<BalanceEvidence> balanceEvidence() {
        return Collections.unmodifiableList(balances);
    }
}
