package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A document-store-shaped implementation used by the CLI-free tests and
 * migration checks. Production adapters can preserve the same access paths.
 */
public final class InMemoryDocumentStore implements DocumentStore {

    private final Map<String, NormalizedTxn> transactions = new HashMap<>();
    private final Map<String, String> messageToTransaction = new HashMap<>();

    @Override
    public void save(NormalizedTxn txn) {
        String key = TransactionIdentity.key(txn);
        NormalizedTxn existing = transactions.get(key);
        if (existing != null) {
            var ids = new java.util.TreeSet<>(existing.sourceMessageIds());
            ids.addAll(txn.sourceMessageIds());
            txn = new NormalizedTxn(existing.accountLast4(), existing.occurredAt(),
                    existing.direction(), existing.amount(), existing.category(),
                    existing.merchant(), List.copyOf(ids));
        }
        transactions.put(key, txn);
        for (String messageId : txn.sourceMessageIds()) messageToTransaction.put(messageId, key);
    }

    @Override
    public void delete(NormalizedTxn txn) {
        String key = TransactionIdentity.key(txn);
        NormalizedTxn removed = transactions.remove(key);
        if (removed == null) return;
        for (String messageId : removed.sourceMessageIds()) {
            if (key.equals(messageToTransaction.get(messageId))) {
                messageToTransaction.remove(messageId);
            }
        }
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        return transactions.values().stream()
                .filter(t -> t.accountLast4().equals(accountLast4))
                .filter(t -> YearMonth.from(t.occurredAt()).equals(month))
                .sorted(Comparator.comparing(NormalizedTxn::occurredAt).reversed())
                .toList();
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> result = new EnumMap<>(Category.class);
        for (Category category : Category.values()) result.put(category, BigDecimal.ZERO.setScale(2));
        for (NormalizedTxn txn : transactions.values()) {
            if (txn.accountLast4().equals(accountLast4)) {
                result.put(txn.category(), result.get(txn.category()).add(txn.amount()));
            }
        }
        return result;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {
        String key = messageToTransaction.get(messageId);
        return key == null ? Optional.empty() : Optional.ofNullable(transactions.get(key));
    }

    @Override
    public List<NormalizedTxn> all() {
        return List.copyOf(transactions.values());
    }

}
