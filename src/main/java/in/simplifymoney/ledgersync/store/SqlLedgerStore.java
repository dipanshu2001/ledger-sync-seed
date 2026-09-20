package in.simplifymoney.ledgersync.store;

import in.simplifymoney.ledgersync.model.Category;
import in.simplifymoney.ledgersync.model.Direction;
import in.simplifymoney.ledgersync.model.NormalizedTxn;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The store this service has used since it was written: a single relational
 * table, reached over plain JDBC.
 *
 * The driver is a runtime dependency (see build.gradle) - this class compiles
 * against the JDK alone.
 */
public final class SqlLedgerStore implements LedgerStore, AutoCloseable {

    private static final String URL_PREFIX = "jdbc:h2:";
    private final Connection conn;

    public SqlLedgerStore(Path dbFile) {
        try {
            this.conn = DriverManager.getConnection(
                    URL_PREFIX + dbFile.toAbsolutePath() + ";MODE=PostgreSQL", "sa", "");
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "could not open the ledger database at " + dbFile
                            + " (is the H2 driver on the runtime classpath?)", e);
        }
    }

    /** Applies every db/migration/V*.sql in filename order. */
    public void migrate(Path migrationDir) {
        try (Statement st = conn.createStatement()) {
            st.execute("CREATE TABLE IF NOT EXISTS schema_history ("
                    + "  filename VARCHAR(200) PRIMARY KEY,"
                    + "  applied_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");

            List<Path> files;
            try (var s = Files.list(migrationDir)) {
                files = s.filter(p -> p.getFileName().toString().endsWith(".sql")).sorted().toList();
            }
            for (Path f : files) {
                String name = f.getFileName().toString();
                try (PreparedStatement q = conn.prepareStatement(
                        "SELECT 1 FROM schema_history WHERE filename = ?")) {
                    q.setString(1, name);
                    try (ResultSet rs = q.executeQuery()) {
                        if (rs.next()) continue;
                    }
                }
                String sql = Files.readString(f);
                for (String stmt : sql.split(";")) {
                    if (!stmt.isBlank()) st.execute(stmt);
                }
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO schema_history(filename) VALUES (?)")) {
                    ins.setString(1, name);
                    ins.executeUpdate();
                }
                System.out.println("applied " + name);
            }
        } catch (Exception e) {
            throw new IllegalStateException("migration failed", e);
        }
    }

    @Override
    public void save(NormalizedTxn t) {
        try {
            try (PreparedStatement find = conn.prepareStatement(
                "SELECT id, source_message_ids FROM ledger WHERE account_last4 = ?"
                        + " AND occurred_at = ? AND direction = ? AND amount = ?"
                        + " AND merchant = ? ORDER BY id LIMIT 1")) {
            find.setString(1, t.accountLast4());
            find.setString(2, t.occurredAt().toString());
            find.setString(3, t.direction().name());
            find.setBigDecimal(4, t.amount());
            find.setString(5, t.merchant());
            try (ResultSet rs = find.executeQuery()) {
                if (rs.next()) {
                    TreeSet<String> ids = new TreeSet<>(Arrays.asList(
                            rs.getString(2).split(",")));
                    ids.addAll(t.sourceMessageIds());
                    try (PreparedStatement update = conn.prepareStatement(
                            "UPDATE ledger SET source_message_ids = ? WHERE id = ?")) {
                        update.setString(1, String.join(",", ids));
                        update.setLong(2, rs.getLong(1));
                        update.executeUpdate();
                    }
                    return;
                }
            }
        }
            try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO ledger(account_last4, occurred_at, direction, amount,"
                        + " category, merchant, source_message_ids)"
                        + " VALUES (?,?,?,?,?,?,?)")) {
            insert.setString(1, t.accountLast4());
            insert.setString(2, t.occurredAt().toString());
            insert.setString(3, t.direction().name());
            insert.setBigDecimal(4, t.amount());
            insert.setString(5, t.category().name());
            insert.setString(6, t.merchant());
            insert.setString(7, String.join(",", t.sourceMessageIds()));
            insert.executeUpdate();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not save " + t, e);
        }
    }

    @Override
    public List<NormalizedTxn> all() {
        Map<String, NormalizedTxn> unique = new LinkedHashMap<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT account_last4, occurred_at, direction, amount, category,"
                             + " merchant, source_message_ids FROM ledger ORDER BY occurred_at")) {
            while (rs.next()) {
                NormalizedTxn txn = new NormalizedTxn(
                        rs.getString(1),
                        OffsetDateTime.parse(rs.getString(2)),
                        Direction.valueOf(rs.getString(3)),
                        rs.getBigDecimal(4).setScale(2),
                        Category.valueOf(rs.getString(5)),
                        rs.getString(6),
                        Arrays.stream(rs.getString(7).split(","))
                                .filter(s -> !s.isBlank()).toList());
                String key = String.join("|", txn.accountLast4(), txn.occurredAt().toString(),
                        txn.direction().name(), txn.amount().toPlainString(),
                        txn.merchant().trim().toUpperCase());
                NormalizedTxn existing = unique.get(key);
                if (existing == null) {
                    unique.put(key, txn);
                } else {
                    TreeSet<String> ids = new TreeSet<>(existing.sourceMessageIds());
                    ids.addAll(txn.sourceMessageIds());
                    unique.put(key, new NormalizedTxn(existing.accountLast4(),
                            existing.occurredAt(), existing.direction(), existing.amount(),
                            existing.category(), existing.merchant(), List.copyOf(ids)));
                }
            }
        } catch (SQLException e) {
            throw new IllegalStateException("could not read the ledger", e);
        }
        return new ArrayList<>(unique.values());
    }

    @Override
    public long count() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM ledger")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new IllegalStateException("could not count the ledger", e);
        }
    }

    @Override
    public void saveBalanceEvidence(BalanceEvidence evidence) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "MERGE INTO balance_evidence(account_last4, occurred_at, stated_balance,"
                            + " source_message_id) KEY(source_message_id) VALUES (?,?,?,?)")) {
                ps.setString(1, evidence.accountLast4());
                ps.setString(2, evidence.occurredAt().toString());
                ps.setBigDecimal(3, evidence.balance());
                ps.setString(4, evidence.sourceMessageId());
                ps.executeUpdate();
            } catch (SQLException e) {
                throw new IllegalStateException("could not save balance evidence", e);
            }
        }

    @Override
    public List<BalanceEvidence> balanceEvidence() {
            List<BalanceEvidence> result = new ArrayList<>();
            try (Statement st = conn.createStatement();
                 ResultSet rs = st.executeQuery(
                         "SELECT account_last4, occurred_at, stated_balance, source_message_id"
                                 + " FROM balance_evidence ORDER BY occurred_at")) {
                while (rs.next()) {
                    result.add(new BalanceEvidence(rs.getString(1),
                            OffsetDateTime.parse(rs.getString(2)),
                            rs.getBigDecimal(3).setScale(2), rs.getString(4)));
                }
                return result;
            } catch (SQLException e) {
                throw new IllegalStateException("could not read balance evidence", e);
        }
    }

    public BigDecimal sumAmounts() {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT SUM(amount) FROM ledger")) {
            return rs.next() && rs.getBigDecimal(1) != null
                    ? rs.getBigDecimal(1).setScale(2) : BigDecimal.ZERO.setScale(2);
        } catch (SQLException e) {
            throw new IllegalStateException("could not total the ledger", e);
        }
    }

    @Override
    public void close() {
        try { conn.close(); } catch (SQLException ignored) { }
    }
}
