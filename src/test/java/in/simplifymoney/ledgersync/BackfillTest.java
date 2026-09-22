package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.Backfill;
import in.simplifymoney.ledgersync.store.InMemoryDocumentStore;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class BackfillTest {

    @Test
    void backfillIsSafeToRerunAgainstDuplicateSqlRows() throws Exception {
        Path path = Files.createTempFile("ledger-backfill-", "");
        try (SqlLedgerStore sql = new SqlLedgerStore(path)) {
            sql.migrate(Path.of("db", "migration"));
            NormalizedTxn txn = new NormalizedTxn("4821",
                    OffsetDateTime.parse("2026-07-01T10:00:00+05:30"),
                    Direction.DEBIT, new BigDecimal("5.00"), Category.MICRO,
                    "UPI/WATER CAN", List.of("m-1"));
            sql.save(txn);
            sql.save(txn);

            InMemoryDocumentStore documents = new InMemoryDocumentStore();
            Backfill.Result first = new Backfill(sql, documents).run();
            Backfill.Result second = new Backfill(sql, documents).run();

            assertEquals(11, first.written());
            assertEquals(0, second.written());
            assertEquals(11, documents.all().size());
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(Path.of(path + ".mv.db"));
        }
    }

    @Test
    void replacesStaleDocumentWhenAReferencedTransactionIdentityChanges() throws Exception {
        Path path = Files.createTempFile("ledger-backfill-", "");
        try (SqlLedgerStore sql = new SqlLedgerStore(path)) {
            sql.migrate(Path.of("db", "migration"));
            NormalizedTxn oldTxn = new NormalizedTxn("4821",
                    OffsetDateTime.parse("2026-07-01T10:00:00+05:30"),
                    Direction.DEBIT, new BigDecimal("5.00"), Category.MICRO,
                    "UPI/WATER CAN", List.of("m-1"));
            NormalizedTxn newTxn = new NormalizedTxn("4821", oldTxn.occurredAt(),
                    oldTxn.direction(), new BigDecimal("6.00"), oldTxn.category(),
                    oldTxn.merchant(), oldTxn.sourceMessageIds());
            InMemoryDocumentStore documents = new InMemoryDocumentStore();
            documents.save(oldTxn);
            sql.save(newTxn);

            Backfill.Result result = new Backfill(sql, documents).run();

            assertEquals(11, result.written());
            assertEquals(11, documents.all().size());
            assertEquals(newTxn, documents.byMessageId("m-1").orElseThrow());
            assertTrue(documents.all().stream()
                    .noneMatch(document -> document.equals(oldTxn)));
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(Path.of(path + ".mv.db"));
        }
    }
}
