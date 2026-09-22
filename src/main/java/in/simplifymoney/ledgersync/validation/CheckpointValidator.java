package in.simplifymoney.ledgersync.validation;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Compares a corpus-only ledger with corpus-a-totals.json without hiding drift. */
public final class CheckpointValidator {

    private CheckpointValidator() {}

    public static List<Divergence> compare(List<NormalizedTxn> ledger,
                                            Map<String, Object> checkpoint) {
        List<Divergence> result = new ArrayList<>();
        number(result, "transactions_expected", decimal(checkpoint.get("transactions_expected")),
                BigDecimal.valueOf(ledger.size()));
        @SuppressWarnings("unchecked")
        Map<String, Object> accounts = (Map<String, Object>) checkpoint.get("accounts");
        for (Map.Entry<String, Object> entry : accounts.entrySet()) {
            String account = entry.getKey();
            @SuppressWarnings("unchecked")
            Map<String, Object> expected = (Map<String, Object>) entry.getValue();
            List<NormalizedTxn> rows = ledger.stream()
                    .filter(t -> t.accountLast4().equals(account))
                    .sorted(Comparator.comparing(NormalizedTxn::occurredAt))
                    .toList();
            BigDecimal spend = sum(rows, Category.SPEND);
            BigDecimal income = sum(rows, Category.INCOME);
            BigDecimal micro = sum(rows, Category.MICRO);
            BigDecimal transfersOut = sum(rows, Category.TRANSFER, Direction.DEBIT);
            BigDecimal transfersIn = sum(rows, Category.TRANSFER, Direction.CREDIT);
            number(result, account + ".transactions_expected",
                    decimal(expected.get("transactions_expected")),
                    BigDecimal.valueOf(rows.size()));
            number(result, account + ".spend", decimal(expected.get("spend")), spend);
            number(result, account + ".income", decimal(expected.get("income")), income);
            number(result, account + ".micro_total", decimal(expected.get("micro_total")), micro);
            number(result, account + ".transferred_out",
                    decimal(expected.get("transferred_out")), transfersOut);
            number(result, account + ".transferred_in",
                    decimal(expected.get("transferred_in")), transfersIn);
            long expectedMicroCount = ((BigDecimal) expected.get("micro_count")).longValue();
            if (expectedMicroCount != rows.stream()
                    .filter(t -> t.category() == Category.MICRO).count()) {
                result.add(new Divergence(account + ".micro_count",
                        Long.toString(expectedMicroCount),
                        Long.toString(rows.stream()
                                .filter(t -> t.category() == Category.MICRO).count())));
            }
        }
        return result;
    }

    private static BigDecimal sum(List<NormalizedTxn> rows, Category category) {
        return rows.stream().filter(t -> t.category() == category)
                .map(NormalizedTxn::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal sum(List<NormalizedTxn> rows, Category category, Direction direction) {
        return rows.stream().filter(t -> t.category() == category)
                .filter(t -> t.direction() == direction)
                .map(NormalizedTxn::amount).reduce(BigDecimal.ZERO.setScale(2), BigDecimal::add);
    }

    private static BigDecimal decimal(Object value) {
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    private static void number(List<Divergence> out, String field,
                               BigDecimal expected, BigDecimal actual) {
        if (expected.compareTo(actual) != 0) {
            out.add(new Divergence(field, expected.toPlainString(), actual.toPlainString()));
        }
    }

    public record Divergence(String field, String expected, String actual) {}
}
