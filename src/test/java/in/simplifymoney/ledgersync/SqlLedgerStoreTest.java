package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.store.SqlLedgerStore;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class SqlLedgerStoreTest {

    @Test
    void saveMergesSameTransactionWhenMerchantCasingChanges() throws Exception {
        Path path = Files.createTempFile("ledger-sql-", "");
        try (SqlLedgerStore store = new SqlLedgerStore(path)) {
            store.migrate(Path.of("db", "migration"));
            OffsetDateTime at = OffsetDateTime.parse("2026-07-01T10:00:00+05:30");
            store.save(new NormalizedTxn("4821", at, Direction.DEBIT,
                    new BigDecimal("25.00"), Category.MICRO, "UPI/BARBER",
                    List.of("m-1")));
            store.save(new NormalizedTxn("4821", at, Direction.DEBIT,
                    new BigDecimal("25.00"), Category.MICRO, "upi/barber",
                    List.of("m-2")));

            var rows = store.all().stream()
                    .filter(t -> t.sourceMessageIds().contains("m-1")
                            || t.sourceMessageIds().contains("m-2"))
                    .toList();

            assertEquals(1, rows.size());
            assertEquals(List.of("m-1", "m-2"), rows.get(0).sourceMessageIds());
        } finally {
            Files.deleteIfExists(path);
            Files.deleteIfExists(Path.of(path + ".mv.db"));
        }
    }
}
