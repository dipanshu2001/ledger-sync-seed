package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.InMemoryDocumentStore;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.List;
import org.junit.jupiter.api.Test;

class DocumentStoreTest {

    @Test
    void servesTheThreeDeclaredAccessPatternsAndMergesEvidence() {
        InMemoryDocumentStore store = new InMemoryDocumentStore();
        NormalizedTxn txn = new NormalizedTxn("4821",
                OffsetDateTime.parse("2026-07-04T20:24:00+05:30"),
                Direction.DEBIT, new BigDecimal("2499.50"), Category.SPEND,
                "AMAZON PAY", List.of("m-2"));
        store.save(txn);
        store.save(new NormalizedTxn("4821", txn.occurredAt(), txn.direction(),
                txn.amount(), txn.category(), txn.merchant(), List.of("m-1")));

        assertEquals(1, store.forAccountMonth("4821", YearMonth.of(2026, 7)).size());
        assertEquals(new BigDecimal("2499.50"),
                store.categoryTotals("4821").get(Category.SPEND));
        assertTrue(store.byMessageId("m-1").isPresent());
        assertEquals(List.of("m-1", "m-2"),
                store.byMessageId("m-2").orElseThrow().sourceMessageIds());
    }
}
