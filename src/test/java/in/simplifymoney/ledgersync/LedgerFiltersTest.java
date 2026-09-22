package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.report.LedgerFilters;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class LedgerFiltersTest {

    @Test
    void assignmentReportsExcludeLegacySeedRows() {
        OffsetDateTime at = OffsetDateTime.parse("2026-07-01T10:00:00+05:30");
        NormalizedTxn corpus = new NormalizedTxn("4821", at, Direction.DEBIT,
                new BigDecimal("5.00"), Category.MICRO, "UPI/WATER CAN",
                List.of("m-00004-9c11ae"));
        NormalizedTxn legacy = new NormalizedTxn("4821", at, Direction.DEBIT,
                new BigDecimal("92213.10"), Category.SPEND, "UPI/WATER CAN",
                List.of("m-legacy-0041"));

        assertEquals(List.of(corpus),
                LedgerFilters.withoutLegacySeedRows(List.of(legacy, corpus)));
    }
}
