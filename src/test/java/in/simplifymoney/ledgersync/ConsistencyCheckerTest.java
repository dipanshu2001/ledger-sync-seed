package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.ConsistencyChecker;
import in.simplifymoney.ledgersync.store.InMemoryDocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class ConsistencyCheckerTest {

    @Test
    void namesChangedDocumentFields() throws Exception {
        var path = Files.createTempFile("ledger-check-", "");
        try (SqlLedgerStore sql = new SqlLedgerStore(path)) {
            InMemoryDocumentStore documents = new InMemoryDocumentStore();
            sql.migrate(java.nio.file.Path.of("db", "migration"));
            NormalizedTxn sqlTxn = new NormalizedTxn("4821",
                    OffsetDateTime.parse("2026-07-01T10:00:00+05:30"),
                    Direction.DEBIT, new BigDecimal("5.00"), Category.MICRO,
                    "UPI/WATER CAN", List.of("m-1"));
            sql.save(sqlTxn);
            documents.save(new NormalizedTxn("4821", sqlTxn.occurredAt(), sqlTxn.direction(),
                    new BigDecimal("6.00"), sqlTxn.category(), sqlTxn.merchant(),
                    sqlTxn.sourceMessageIds()));

            var divergences = new ConsistencyChecker(sql, documents).check();
            assertTrue(divergences.stream().anyMatch(d -> d.what().endsWith(".amount")));
            assertTrue(divergences.stream().noneMatch(
                    d -> d.what().startsWith("document-only:")));
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(java.nio.file.Path.of(path + ".mv.db"));
        }
    }
}
