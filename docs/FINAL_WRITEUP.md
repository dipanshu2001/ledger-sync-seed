# Ledger Sync - Assignment Write-up

## 1. Executive summary

The ledger ingestion and migration workflow is implemented and functionally
validated. The system reads the supplied SMS/email corpus, parses supported bank
formats, deduplicates messages into real transactions, assigns categories,
generates deterministic reports, persists to SQL, migrates to local MongoDB,
and verifies SQL/document consistency.

The functional test suite passes with **23 tests and 0 failures**. The local
MongoDB migration has also been executed successfully against database
`ledger`, collection `transactions`.

Two items are intentionally deferred:

1. The supplied corpus has an unexplained `₹7,500` movement: the totals fixture
   expects one more transaction than the raw corpus provides.
2. The 100,000-transaction MongoDB benchmark and its six query metrics have
   not been run.

Neither deferred item is hidden or replaced with fabricated data.

## 2. Requirements and implementation status

| Requirement | Status | Evidence |
|---|---|---|
| Parse bank SMS alerts | Complete | HDFC and ICICI formats are supported and regression-tested |
| Parse bank email alerts | Complete | HDFC/ICICI transaction email formats are parsed using bank occurrence time |
| Reject non-transaction/hostile messages | Complete | Parsers require bank-specific transaction fields |
| Fix water-can incident | Complete | Integer amount parsing fixed; regression test added |
| Deduplicate transaction evidence | Complete | Deterministic identity merges SMS/email/replayed evidence |
| Preserve source-message traceability | Complete | Every ledger transaction retains sorted source message IDs |
| Categorize transactions | Complete | `SPEND`, `INCOME`, `MICRO`, and `TRANSFER` rules implemented |
| Generate ledger report | Complete | Stable ordering and exact two-decimal amounts |
| Generate summary report | Complete | Micro and transfer totals are separated from spend/income |
| Generate reconciliation report | Complete | Unexplained balance movements are reported explicitly |
| Persist to SQL | Complete | H2 migrations and idempotent SQL writes implemented |
| Implement document-store adapter | Complete | Local MongoDB adapter implemented |
| Implement backfill | Complete | Dirty SQL rows are canonicalized and safely rerun |
| Implement consistency checking | Complete | Field-level divergence reporting implemented |
| Run functional tests | Complete | 23 tests pass |
| Run local Mongo migration | Complete | 266 documents migrated; rerun skips all; zero divergences |
| Run 100,000-row benchmark | Deferred | Delayed by request |
| Resolve supplied corpus mismatch | Deferred | Raw fixture lacks the source alert for `₹7,500` |
| Complete release evidence | Complete | Walkthrough and evidence recorded in README/tracker |

## 3. Parsing and ingestion

Supported input:

- HDFC SMS transaction formats
- HDFC card SMS formats
- HDFC transaction emails
- ICICI legacy SMS format
- ICICI compact `Dr`/`Cr` SMS format
- ICICI transaction emails

The normalized transaction identity is:

```text
account + occurred_at + direction + amount + normalized merchant
```

This identity is used consistently by ingestion, SQL canonicalization, Mongo
document IDs, backfill, and consistency checking. Multiple messages describing
one transaction are merged, while all source message IDs are retained.

Amounts are represented as positive `BigDecimal` values with exactly two decimal
places. The direction carries the debit/credit meaning.

## 4. Incident response

The production-style incident involved a `₹5` water-can debit being displayed
as `₹92,213.10`.

### Root cause

The old amount pattern required two decimal places. It skipped `Rs.5` and then
captured the later available balance as the transaction amount.

### Fix

The amount parser now accepts integer, one-decimal, and two-decimal amounts.
The parser-specific regression test confirms that `Rs.5` remains `5.00` even
when followed by a decimal available balance.

The exact five-line incident update is recorded in:

```text
incident/INC-2026-09-11.md
```

## 5. Categories and reports

The category rules are mutually exclusive:

- `MICRO`: debit, merchant begins with `UPI`, amount is at most `₹100`
- `TRANSFER`: corpus-supported owned-account transfer involving
  `PARAG KAPOOR`
- `SPEND`: ordinary debit not classified as micro or transfer
- `INCOME`: ordinary credit not classified as transfer

The reports are written by the `report` command:

```text
ledger.json
summary.json
reconciliation.json
```

Report validation confirmed:

- required fields are present
- amounts have exactly two decimal places
- directions and categories are valid
- source-message IDs are present and sorted
- no source message is assigned to multiple normalized transactions
- reconciliation exposes the unexplained `₹7,500` movement

## 6. SQL and MongoDB migration

The selected document database is local MongoDB:

```text
URI:        mongodb://localhost:27017
Database:   ledger
Collection: transactions
```

Each Mongo document stores:

- account suffix
- occurrence timestamp
- occurrence month
- direction
- exact amount string
- category
- merchant
- source message IDs

Indexes support:

- account/month/time retrieval
- source-message lookup
- document identity through `_id`

### Migration evidence

The executed workflow was:

```bash
./gradlew run --args="migrate"
./gradlew run --args="ingest fixtures/corpus-a.jsonl"
./gradlew run --args="backfill-mongo"
./gradlew run --args="check-mongo"
```

Results:

- first backfill: **266 documents written**
- second backfill: **0 written, 266 skipped**
- consistency check: **0 divergences**
- account/month smoke query: **103 rows returned**
- category totals: all four categories returned
- source-message lookup: returned the matching transaction

The adapter also reuses equivalent existing indexes, preventing startup failure
when an index exists under a different name.

## 7. Functional test evidence

The complete Gradle test suite passes:

```text
23 tests
0 failures
0 errors
0 skipped
```

Coverage includes:

- amount parsing
- parser regressions
- normalized transaction contract
- replay/idempotency
- document-store access patterns
- reconciliation
- consistency checking
- duplicate SQL backfill
- stale document replacement after identity changes

The dependency-free self-check also runs successfully:

```bash
./gradlew selfCheck
```

## 8. Supplied corpus discrepancy

The supplied fixtures contain an internal mismatch:

- `corpus-a-totals.json` expects **257 transactions**
- the raw corpus yields **256 source-backed transactions**
- account `4821` expected spend: **₹87,068.38**
- source-backed spend: **₹79,568.38**
- unexplained difference: **₹7,500.00**

The balance evidence independently shows a `₹7,500` unexplained movement on
2026-07-29. However, no supplied raw message contains an account-4821
`₹7,500` transaction alert.

The implementation does not synthesize a transaction because every normalized
transaction must cite at least one real source message. Instead,
`reconciliation.json` reports the discrepancy. The correct data-side fix is to
provide the missing bank alert or correct the expected totals fixture.

The SQL database also contains 15 intentionally seeded legacy rows used to
exercise dirty-data migration. Therefore, a report generated from the normal
SQL database includes legacy rows in addition to corpus rows. Corpus checkpoint
validation must use a fresh validation database or otherwise exclude seeded
legacy data.

## 9. Deferred work

### 9.1 Corpus correction

Deferred because the missing `₹7,500` transaction has no source message in the
provided corpus. Adding a synthetic row would make the numbers match while
breaking source traceability.

### 9.2 100,000-transaction benchmark

Deferred by request. The required six MongoDB metrics are not claimed:

- account/month: `totalDocsExamined`, `nReturned`
- category totals: `totalDocsExamined`, `nReturned`
- message lookup: `totalDocsExamined`, `nReturned`

No benchmark numbers have been invented.

### 9.3 Docker Compose

`compose.yaml` starts MongoDB on `localhost:27017`, matching the adapter default.
Use `docker compose up -d` before `backfill-mongo` and `check-mongo`.

## 10. Repository evidence

The detailed working record is maintained in:

- `docs/PROJECT_TRACKER.csv`
- `docs/PROJECT_PLAN.md`
- `docs/DECISION_LOG.md`
- `docs/DATA_DECISIONS.md`
- `docs/AI_LEARNING_LOG.md`
- `incident/INC-2026-09-11.md`

