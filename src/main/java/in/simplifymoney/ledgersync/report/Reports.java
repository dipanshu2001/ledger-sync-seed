package in.simplifymoney.ledgersync.report;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.Comparator;
import in.simplifymoney.ledgersync.store.BalanceEvidence;

/**
 * The two reports the assignment asks for.
 *
 * summary() below is a first cut: it adds up what is in the ledger. It does not
 * know that a transfer is not spending, and it does not roll micro spends up.
 *
 * reconciliation() has not been written at all.
 */
public final class Reports {

    private Reports() {}

    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(2);

    public static Map<String, Object> summary(List<NormalizedTxn> ledger) {
        Map<String, Object> accounts = new LinkedHashMap<>();
        for (String acct : new TreeSet<>(ledger.stream()
                .map(NormalizedTxn::accountLast4).toList())) {

            BigDecimal spend = ZERO;
            BigDecimal income = ZERO;
            for (NormalizedTxn t : ledger) {
                if (!t.accountLast4().equals(acct)) continue;
                if (t.category() == Category.SPEND) spend = spend.add(t.amount());
                else if (t.category() == Category.INCOME) income = income.add(t.amount());
            }

            Map<String, Object> a = new LinkedHashMap<>();
            a.put("spend", spend.toPlainString());
            a.put("income", income.toPlainString());
            long microCount = ledger.stream()
                    .filter(t -> t.accountLast4().equals(acct))
                    .filter(t -> t.category() == Category.MICRO)
                    .count();
            BigDecimal microTotal = ledger.stream()
                    .filter(t -> t.accountLast4().equals(acct))
                    .filter(t -> t.category() == Category.MICRO)
                    .map(NormalizedTxn::amount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal transferredOut = ledger.stream()
                    .filter(t -> t.accountLast4().equals(acct))
                    .filter(t -> t.category() == Category.TRANSFER)
                    .filter(t -> t.direction() == Direction.DEBIT)
                    .map(NormalizedTxn::amount)
                    .reduce(ZERO, BigDecimal::add);
            BigDecimal transferredIn = ledger.stream()
                    .filter(t -> t.accountLast4().equals(acct))
                    .filter(t -> t.category() == Category.TRANSFER)
                    .filter(t -> t.direction() == Direction.CREDIT)
                    .map(NormalizedTxn::amount)
                    .reduce(ZERO, BigDecimal::add);
            a.put("micro_count", microCount);
            a.put("micro_total", microTotal.toPlainString());
            a.put("transferred_out", transferredOut.toPlainString());
            a.put("transferred_in", transferredIn.toPlainString());
            accounts.put(acct, a);
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("accounts", accounts);
        return doc;
    }

    public static Map<String, Object> ledgerDocument(List<NormalizedTxn> ledger) {
        List<Object> rows = ledger.stream()
                .sorted(Comparator.comparing(NormalizedTxn::accountLast4)
                        .thenComparing(NormalizedTxn::occurredAt)
                        .thenComparing(t -> t.sourceMessageIds().get(0)))
                .map(t -> {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("account_last4", t.accountLast4());
            r.put("occurred_at", t.occurredAt().toString());
            r.put("direction", t.direction().name().toLowerCase());
            r.put("amount", t.amount().toPlainString());
            r.put("category", t.category().name());
            r.put("merchant", t.merchant());
            r.put("source_message_ids", t.sourceMessageIds());
            return (Object) r;
        }).toList();
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("transactions", rows);
        return doc;
    }

    public static Map<String, Object> reconciliation(List<NormalizedTxn> ledger) {
        return reconciliation(ledger, List.of());
    }

    public static Map<String, Object> reconciliation(
            List<NormalizedTxn> ledger, List<BalanceEvidence> evidence) {
        List<Object> discrepancies = new java.util.ArrayList<>();
        Map<String, List<BalanceEvidence>> byAccount = new java.util.HashMap<>();
        for (BalanceEvidence item : evidence) {
            byAccount.computeIfAbsent(item.accountLast4(), ignored -> new java.util.ArrayList<>())
                    .add(item);
        }
        for (List<BalanceEvidence> accountEvidence : byAccount.values()) {
            accountEvidence.sort(Comparator.comparing(BalanceEvidence::occurredAt));
            for (int i = 1; i < accountEvidence.size(); i++) {
                BalanceEvidence previous = accountEvidence.get(i - 1);
                BalanceEvidence current = accountEvidence.get(i);
                BigDecimal movement = ledger.stream()
                        .filter(t -> t.accountLast4().equals(current.accountLast4()))
                        .filter(t -> t.occurredAt().isAfter(previous.occurredAt()))
                        .filter(t -> !t.occurredAt().isAfter(current.occurredAt()))
                        .map(t -> t.direction() == Direction.DEBIT
                                ? t.amount().negate() : t.amount())
                        .reduce(ZERO, BigDecimal::add);
                BigDecimal expected = previous.balance().add(movement);
                if (expected.compareTo(current.balance()) != 0) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("account_last4", current.accountLast4());
                    row.put("occurred_at", current.occurredAt().toString());
                    row.put("amount", expected.subtract(current.balance()).abs().toPlainString());
                    row.put("note", "bank balance evidence differs from ledger movement by "
                            + expected.subtract(current.balance()).toPlainString()
                            + " since " + previous.sourceMessageId());
                    discrepancies.add(row);
                }
            }
        }
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("discrepancies", discrepancies);
        return document;
    }

    public static Map<Category, BigDecimal> byCategory(List<NormalizedTxn> ledger) {
        Map<Category, BigDecimal> out = new LinkedHashMap<>();
        for (Category c : Category.values()) out.put(c, ZERO);
        for (NormalizedTxn t : ledger) {
            out.put(t.category(), out.get(t.category()).add(t.amount()));
        }
        return out;
    }
}
