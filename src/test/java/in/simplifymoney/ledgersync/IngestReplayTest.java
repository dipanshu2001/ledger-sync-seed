package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;

import in.simplifymoney.ledgersync.ingest.IngestService;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.InMemoryLedgerStore;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IngestReplayTest {

    @Test
    void replayingTheCorpusDoesNotAddTransactions() throws Exception {
        InMemoryLedgerStore store = new InMemoryLedgerStore();
        IngestService ingest = new IngestService(new Parsers(), store);

        ingest.ingestFile(Path.of("fixtures", "corpus-a.jsonl"));
        var first = store.all();
        ingest.ingestFile(Path.of("fixtures", "corpus-a.jsonl"));

        assertEquals(first.size(), store.all().size());
        assertEquals(first, store.all());
    }
}
