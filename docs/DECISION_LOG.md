# Decision Log

This is a living record of consequential choices. Each entry should state the context, evidence, decision, rejected alternatives, and consequence. Do not rewrite history when a later experiment changes the decision; append a superseding entry.

## Entry template

### D-XX — Short decision title

- **Date/status:** YYYY-MM-DD / proposed or accepted
- **Question:** What needed deciding?
- **Evidence:** Corpus observation, code constraint, test result, or operational requirement.
- **Decision:** What we will implement.
- **Rejected alternatives:** What else was considered and why it was not chosen.
- **Consequence:** Follow-on work, risk, or trade-off.

## Initial entries to complete during discovery

### D-01 — Transaction identity and deduplication

- **Date/status:** 2026-09-20 / accepted for current corpus
- **Question:** Which stable attributes identify one real transaction across multiple uploaded messages?
- **Evidence:** The corpus has 522 uploads, 479 parser matches after format fixes, and exact normalized grouping produces the checkpoint's 257 transaction count. `message_id` identifies an upload, not the underlying transaction.
- **Decision:** Use account, bank occurrence timestamp, direction, exact amount, and normalized merchant as the first transaction identity key; merge all evidence IDs and sort them.
- **Rejected alternatives:** Using `message_id` as the transaction key would violate the specification; using only amount would merge unrelated transactions.
- **Consequence:** The identity rule must be shared by ingestion, backfill, and consistency checking. Collision tests are still required for unseen corpora.

### D-11 — Incident amount extraction

- **Date/status:** 2026-09-20 / accepted
- **Question:** Why did a ₹5 water-can debit become ₹92,213.10?
- **Evidence:** `Amounts.first` accepted only amounts with two decimal places. The transaction was `Rs.5`; the first matching amount was therefore the later `Avl Bal: Rs.92,213.10`.
- **Decision:** Accept integer and one/two-decimal rupee amounts, preserving the first transaction amount selected by a bank-specific parser.
- **Rejected alternatives:** Taking the smallest amount or subtracting balances would hide malformed templates and could silently corrupt legitimate messages.
- **Consequence:** Add a regression test for an integer transaction before a decimal balance and measure every unique affected transaction, not just raw uploads.

### D-02 — Money representation

- **Date/status:** 2026-09-20 / accepted
- **Question:** How should paisa-exact values be represented?
- **Evidence:** `NormalizedTxn` requires positive `BigDecimal` values with scale 2; reports require exact decimal strings.
- **Decision:** Use `BigDecimal`, normalize at parser boundaries, and never use binary floating point for money.
- **Rejected alternatives:** `double` risks rounding; integer paise would require conversion at every frozen-model boundary.
- **Consequence:** Amount parsing and report serialization need explicit scale checks.

### D-03 — Parser acceptance policy

- **Date/status:** 2026-09-20 / accepted
- **Question:** How do we prevent hostile or non-transaction messages from entering the ledger?
- **Evidence:** The corpus includes money mentions that are not transactions and hostile content.
- **Decision:** Require bank-template-specific evidence for account, amount, direction, and occurrence time; reject unmatched content explicitly.
- **Rejected alternatives:** Generic currency regex parsing is too permissive and caused the incident class of risk.
- **Consequence:** Each supported template needs a focused positive and negative test.

### D-04 — Category precedence

- **Date/status:** 2026-09-20 / accepted
- **Question:** When a debit is also small or a transfer, which category wins?
- **Evidence:** `MICRO` is a UPI debit of ₹100 or less; `TRANSFER` is a movement between owned accounts and must not count as spend.
- **Decision:** Determine precedence from corpus transfer evidence, then encode one mutually exclusive rule and test boundary values.
- **Rejected alternatives:** Direction-only classification is explicitly incomplete; category assignment based only on amount misclassifies transfers.
- **Consequence:** The classifier must inspect merchant/counterparty and account ownership, not just direction.

### D-05 — Document database

- **Date/status:** 2026-09-20 / accepted
- **Question:** DynamoDB or MongoDB?
- **Evidence:** DynamoDB is preferred, but local startup, testability, and direct query metrics matter.
- **Decision:** Use local MongoDB at `mongodb://localhost:27017`, database `ledger`, collection `transactions`. Keep the in-memory implementation for tests and no-database verification.
- **Rejected alternatives:** DynamoDB Local was rejected because the requested environment already provides local MongoDB and the three access patterns map directly to MongoDB compound and multikey indexes without Docker.
- **Consequence:** MongoDB must be running locally; query metrics will use `explain("executionStats")`.

### D-06 — Reconciliation semantics

- **Date/status:** 2026-09-20 / accepted
- **Question:** What does “cannot account for” mean when balance evidence is incomplete?
- **Evidence:** Reports must expose discrepancies honestly; corpus totals include opening and closing balance checkpoints.
- **Decision:** Derive reconciliation from explicit bank-stated balance evidence and report missing/ambiguous evidence instead of inventing transactions.
- **Rejected alternatives:** Forcing totals to match would conceal data quality problems and violates the assignment.
- **Consequence:** Reconciliation output needs stable notes explaining the missing link or mismatch.

### D-12 — Deferred checkpoint and benchmark work

- **Date/status:** 2026-09-20 / deferred by user
- **Question:** Should the missing corpus movement and 100,000-row benchmark block functional completion?
- **Evidence:** The raw corpus has no source message for the `₹7,500` movement, while the local Mongo migration and all functional tests pass. The benchmark is a separate measurement exercise.
- **Decision:** Keep the `₹7,500` discrepancy explicit in `reconciliation.json` and defer the 100,000-row benchmark.
- **Rejected alternatives:** Adding a synthetic transaction would violate source traceability; inventing benchmark numbers would violate the measurement requirement.
- **Consequence:** The functional release is complete, but the final assignment score still has two documented deferred items.

### D-07 — Backfill idempotency

- **Date/status:** 2026-09-20 / accepted
- **Question:** How can backfill survive dirty SQL, reruns, and partial failure?
- **Evidence:** SQL has no uniqueness guarantee and backfill may run repeatedly.
- **Decision:** Use the same deterministic transaction identity as ingestion and make target writes idempotent/upsert-like.
- **Rejected alternatives:** Blind inserts duplicate data; row-count checkpoints cannot distinguish duplicates from real transactions.
- **Consequence:** Backfill results must distinguish read, written, and skipped records and be safe to resume.

### D-08 — Consistency comparison

- **Date/status:** 2026-09-20 / accepted
- **Question:** What must the checker compare?
- **Evidence:** The evaluator will deliberately alter the document store; row counts alone are insufficient.
- **Decision:** Compare canonical transaction identity plus every contract field and source-message set, reporting field-level divergences.
- **Rejected alternatives:** Counts or aggregate totals can miss a swapped amount or merchant.
- **Consequence:** Canonicalization and deterministic diff formatting are required.

### D-09 — Query measurement

- **Date/status:** 2026-09-20 / proposed
- **Question:** How will examined-versus-returned numbers be measured honestly?
- **Evidence:** README requires six numbers at 100,000 transactions.
- **Decision:** Generate a reproducible benchmark dataset, run each declared query with production indexes, and record engine-native metrics rather than estimates.
- **Rejected alternatives:** Theoretical index selectivity numbers are not acceptable evidence.
- **Consequence:** Benchmark setup and dataset assumptions must be documented beside the six numbers.

### D-10 — Framework scope

- **Date/status:** 2026-09-20 / proposed
- **Question:** Should the implementation add a framework?
- **Evidence:** The seed is JDK-only apart from test dependencies and already has a small command-line pipeline.
- **Decision:** Prefer the existing Java structure and minimal dependencies unless the chosen document store requires a justified client.
- **Rejected alternatives:** Introducing a web framework would add boot and compose complexity unrelated to the required behavior.
- **Consequence:** Keep adapters and domain logic testable without a running server.
