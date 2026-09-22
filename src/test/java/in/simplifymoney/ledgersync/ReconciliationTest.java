package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.report.Reports;
import in.simplifymoney.ledgersync.store.BalanceEvidence;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReconciliationTest {

    @Test
    void reportsMovementBetweenBankBalanceEvidence() {
        OffsetDateTime first = OffsetDateTime.parse("2026-07-01T10:00:00+05:30");
        OffsetDateTime second = OffsetDateTime.parse("2026-07-01T11:00:00+05:30");
        NormalizedTxn debit = new NormalizedTxn("4821", second, Direction.DEBIT,
                new BigDecimal("5.00"), Category.MICRO, "UPI/WATER CAN", List.of("m-1"));
        var evidence = List.of(
                new BalanceEvidence("4821", first, new BigDecimal("100.00"), "b-1"),
                new BalanceEvidence("4821", second, new BigDecimal("95.00"), "b-2"));

        Map<String, Object> result = Reports.reconciliation(List.of(debit), evidence);

        assertEquals(0, ((List<?>) result.get("discrepancies")).size());
    }

    @Test
    void reportsAnUnexplainedBalanceDifference() {
        OffsetDateTime first = OffsetDateTime.parse("2026-07-01T10:00:00+05:30");
        OffsetDateTime second = OffsetDateTime.parse("2026-07-01T11:00:00+05:30");
        NormalizedTxn debit = new NormalizedTxn("4821", second, Direction.DEBIT,
                new BigDecimal("5.00"), Category.MICRO, "UPI/WATER CAN", List.of("m-1"));
        var evidence = List.of(
                new BalanceEvidence("4821", first, new BigDecimal("100.00"), "b-1"),
                new BalanceEvidence("4821", second, new BigDecimal("90.00"), "b-2"));

        Map<String, Object> result = Reports.reconciliation(List.of(debit), evidence);

        assertEquals(1, ((List<?>) result.get("discrepancies")).size());
    }
}
