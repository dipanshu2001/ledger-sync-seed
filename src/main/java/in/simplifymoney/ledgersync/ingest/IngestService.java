package in.simplifymoney.ledgersync.ingest;

import in.simplifymoney.ledgersync.json.Json;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.ParsedTxn;
import in.simplifymoney.ledgersync.parse.Parsers;
import in.simplifymoney.ledgersync.store.LedgerStore;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Reads a corpus of raw messages and puts transactions in the ledger.
 *
 * This is the naive version. It parses each message on its own and saves
 * whatever comes back. It does not ask whether two messages describe the same
 * transaction, and it decides the category from the direction alone.
 */
public final class IngestService {

    private final Parsers parsers;
    private final LedgerStore store;

    public IngestService(Parsers parsers, LedgerStore store) {
        this.parsers = parsers;
        this.store = store;
    }

    public Stats ingestFile(Path corpus) throws IOException {
        List<RawMessage> messages = readCorpus(corpus);
        int skipped = 0;
        Map<TransactionKey, List<ParsedTxn>> grouped = new LinkedHashMap<>();
        for (RawMessage m : messages) {
            Optional<ParsedTxn> p = parsers.parse(m);
            if (p.isEmpty()) {
                skipped++;
                continue;
            }
            ParsedTxn txn = p.get();
            if (txn.statedBalance() != null) {
                store.saveBalanceEvidence(new in.simplifymoney.ledgersync.store.BalanceEvidence(
                        txn.accountLast4(), txn.occurredAt(), txn.statedBalance(),
                        txn.sourceMessageId()));
            }
            grouped.computeIfAbsent(TransactionKey.of(txn), ignored -> new ArrayList<>()).add(txn);
        }
        for (List<ParsedTxn> evidence : grouped.values()) {
            store.save(toTransaction(evidence));
        }
        return new Stats(messages.size(), grouped.size(), skipped);
    }

    public static List<RawMessage> readCorpus(Path corpus) throws IOException {
        List<RawMessage> out = new ArrayList<>();
        try (Stream<String> lines = Files.lines(corpus)) {
            for (String line : (Iterable<String>) lines.filter(s -> !s.isBlank())::iterator) {
                Map<String, Object> o = Json.parseObject(line);
                out.add(new RawMessage(
                        (String) o.get("message_id"),
                        (String) o.get("channel"),
                        (String) o.get("sender"),
                        OffsetDateTime.parse((String) o.get("received_at")),
                        (String) o.get("device_id"),
                        (String) o.get("body")));
            }
        }
        return out;
    }

    private NormalizedTxn toTransaction(List<ParsedTxn> evidence) {
        ParsedTxn p = evidence.get(0);
        Category c = categoryFor(p);
        List<String> sourceIds = evidence.stream()
                .map(ParsedTxn::sourceMessageId)
                .sorted()
                .toList();
        return new NormalizedTxn(p.accountLast4(), p.occurredAt(), p.direction(),
                p.amount(), c, p.merchant(), sourceIds);
    }

    private Category categoryFor(ParsedTxn p) {
        String merchant = p.merchant().toUpperCase();
        if (merchant.contains("PARAG KAPOOR")) return Category.TRANSFER;
        if (p.direction() == Direction.DEBIT
                && merchant.startsWith("UPI")
                && p.amount().compareTo(new java.math.BigDecimal("100.00")) <= 0) {
            return Category.MICRO;
        }
        return p.direction() == Direction.DEBIT ? Category.SPEND : Category.INCOME;
    }

    public record Stats(int messagesRead, int transactionsWritten, int messagesSkipped) {}

    private record TransactionKey(String accountLast4, OffsetDateTime occurredAt,
                                  Direction direction, java.math.BigDecimal amount,
                                  String merchant) {
        static TransactionKey of(ParsedTxn txn) {
            return new TransactionKey(txn.accountLast4(), txn.occurredAt(),
                    txn.direction(), txn.amount(), txn.merchant().trim().toUpperCase());
        }
    }
}
