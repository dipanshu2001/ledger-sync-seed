package in.simplifymoney.ledgersync.store;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.model.Sorts;
import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.bson.Document;

/**
 * MongoDB adapter for the local ledger database.
 *
 * One transaction is one document. The transaction identity is the document
 * id; source message IDs are denormalized into the document and indexed for
 * direct traceability.
 */
public final class MongoDocumentStore implements DocumentStore, AutoCloseable {

    public static final String DEFAULT_URI = "mongodb://localhost:27017";
    public static final String DATABASE = "ledger";
    private static final String COLLECTION = "transactions";

    private final MongoClient client;
    private final MongoCollection<Document> collection;

    public MongoDocumentStore() {
        this(System.getenv().getOrDefault("LEDGER_MONGO_URI", DEFAULT_URI));
    }

    public MongoDocumentStore(String uri) {
        client = MongoClients.create(uri);
        collection = client.getDatabase(DATABASE).getCollection(COLLECTION);
        ensureIndex(new Document("account_last4", 1)
                        .append("occurred_month", 1)
                        .append("occurred_at", 1),
                "account_month_time");
        ensureIndex(new Document("source_message_ids", 1), "source_message_ids");
    }

    @Override
    public void save(NormalizedTxn txn) {
        String id = identity(txn);
        Document existing = collection.find(Filters.eq("_id", id)).first();
        if (existing != null) {
            NormalizedTxn prior = fromDocument(existing);
            var ids = new java.util.TreeSet<>(prior.sourceMessageIds());
            ids.addAll(txn.sourceMessageIds());
            txn = new NormalizedTxn(prior.accountLast4(), prior.occurredAt(),
                    prior.direction(), prior.amount(), prior.category(),
                    prior.merchant(), List.copyOf(ids));
        }
        collection.replaceOne(Filters.eq("_id", id), toDocument(id, txn),
                new ReplaceOptions().upsert(true));
    }

    @Override
    public void delete(NormalizedTxn txn) {
        collection.deleteOne(Filters.eq("_id", identity(txn)));
    }

    @Override
    public List<NormalizedTxn> forAccountMonth(String accountLast4, YearMonth month) {
        List<NormalizedTxn> result = new ArrayList<>();
        for (Document document : collection.find(Filters.and(
                        Filters.eq("account_last4", accountLast4),
                        Filters.eq("occurred_month", month.toString())))
                .sort(Sorts.descending("occurred_at"))) {
            result.add(fromDocument(document));
        }
        return result;
    }

    @Override
    public Map<Category, BigDecimal> categoryTotals(String accountLast4) {
        Map<Category, BigDecimal> result = new EnumMap<>(Category.class);
        for (Category category : Category.values()) {
            result.put(category, BigDecimal.ZERO.setScale(2));
        }
        for (Document document : collection.find(Filters.eq("account_last4", accountLast4))) {
            NormalizedTxn txn = fromDocument(document);
            result.put(txn.category(), result.get(txn.category()).add(txn.amount()));
        }
        return result;
    }

    @Override
    public Optional<NormalizedTxn> byMessageId(String messageId) {
        Document document = collection.find(Filters.eq("source_message_ids", messageId)).first();
        return document == null ? Optional.empty() : Optional.of(fromDocument(document));
    }

    @Override
    public List<NormalizedTxn> all() {
        List<NormalizedTxn> result = new ArrayList<>();
        for (Document document : collection.find()) result.add(fromDocument(document));
        return result;
    }

    @Override
    public void close() {
        client.close();
    }

    private void ensureIndex(Document key, String name) {
        for (Document index : collection.listIndexes()) {
            if (key.equals(index.get("key"))) {
                return;
            }
        }
        collection.createIndex(key, new com.mongodb.client.model.IndexOptions().name(name));
    }

    private static Document toDocument(String id, NormalizedTxn txn) {
        return new Document("_id", id)
                .append("account_last4", txn.accountLast4())
                .append("occurred_at", txn.occurredAt().toString())
                .append("occurred_month", YearMonth.from(txn.occurredAt()).toString())
                .append("direction", txn.direction().name())
                .append("amount", txn.amount().toPlainString())
                .append("category", txn.category().name())
                .append("merchant", txn.merchant())
                .append("source_message_ids", txn.sourceMessageIds());
    }

    @SuppressWarnings("unchecked")
    private static NormalizedTxn fromDocument(Document document) {
        return new NormalizedTxn(
                document.getString("account_last4"),
                OffsetDateTime.parse(document.getString("occurred_at")),
                Direction.valueOf(document.getString("direction")),
                new BigDecimal(document.getString("amount")).setScale(2),
                Category.valueOf(document.getString("category")),
                document.getString("merchant"),
                (List<String>) document.get("source_message_ids"));
    }

    private static String identity(NormalizedTxn txn) {
        return TransactionIdentity.key(txn);
    }
}
