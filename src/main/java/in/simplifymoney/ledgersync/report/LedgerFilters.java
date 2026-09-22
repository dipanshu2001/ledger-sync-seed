package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.util.List;

/** Filters used when producing assignment-facing ledger documents. */
public final class LedgerFilters {

    private LedgerFilters() {}

    public static List<NormalizedTxn> withoutLegacySeedRows(List<NormalizedTxn> ledger) {
        return ledger.stream()
                .filter(LedgerFilters::isNotLegacySeedRow)
                .toList();
    }

    private static boolean isNotLegacySeedRow(NormalizedTxn txn) {
        return txn.sourceMessageIds().stream()
                .noneMatch(id -> id.startsWith("m-legacy-"));
    }
}
