package in.simplifymoney.ledgersync.store;

/**
 * Moves everything already in the SQL store into the document store.
 *
 * Two things to know before you start:
 *  - the SQL store is not clean. It has been running without a uniqueness
 *    guarantee for a long time
 *  - this will be run more than once, including after a partial failure
 */
public final class Backfill {

    private final SqlLedgerStore source;
    private final DocumentStore target;

    public Backfill(SqlLedgerStore source, DocumentStore target) {
        this.source = source;
        this.target = target;
    }

    public Result run() {
        long read = 0;
        long written = 0;
        long skipped = 0;
        var existingDocuments = new java.util.ArrayList<>(target.all());
        for (var txn : source.all()) {
            read++;
            var related = existingDocuments.stream()
                    .filter(document -> sharesMessage(document, txn))
                    .toList();
            var equivalent = related.stream()
                    .filter(document -> equivalent(document, txn))
                    .findFirst();
            if (equivalent.isPresent()) {
                related.stream()
                        .filter(document -> !TransactionIdentity.key(document)
                                .equals(TransactionIdentity.key(equivalent.get())))
                        .forEach(target::delete);
                existingDocuments.removeAll(related);
                existingDocuments.add(equivalent.get());
                skipped++;
                continue;
            }
            related.forEach(target::delete);
            target.save(txn);
            existingDocuments.removeAll(related);
            existingDocuments.add(txn);
            written++;
        }
        return new Result(read, written, skipped);
    }

    public record Result(long read, long written, long skipped) {}

    private static boolean equivalent(in.simplifymoney.ledgersync.model.NormalizedTxn a,
                                      in.simplifymoney.ledgersync.model.NormalizedTxn b) {
        return a.accountLast4().equals(b.accountLast4())
                && a.occurredAt().equals(b.occurredAt())
                && a.direction() == b.direction()
                && a.amount().compareTo(b.amount()) == 0
                && a.category() == b.category()
                && a.merchant().equals(b.merchant())
                && new java.util.TreeSet<>(a.sourceMessageIds())
                .equals(new java.util.TreeSet<>(b.sourceMessageIds()));
    }

    private static boolean sharesMessage(in.simplifymoney.ledgersync.model.NormalizedTxn a,
                                         in.simplifymoney.ledgersync.model.NormalizedTxn b) {
        return a.sourceMessageIds().stream().anyMatch(b.sourceMessageIds()::contains);
    }
}
