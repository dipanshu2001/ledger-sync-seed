# Ledger Sync Build Plan

**Created:** 2026-09-20  
**Tracker:** `docs/PROJECT_TRACKER.csv`  
**Working rule:** complete tasks in dependency order, keep the CSV status current, and record evidence rather than relying on memory.

## Execution order

1. **Discovery:** read the specification and frozen contracts, then profile the entire corpus. The profile must identify all message templates, duplicate/evidence relationships, account pairs, micro-debit patterns, non-transactions, and hostile content.
2. **Incident first:** reproduce the inflated water-can transaction before changing parsers. Trace the raw body, parsed amount, normalized transaction, report output, and legacy SQL row. Measure the complete blast radius and write the exact triggering rule.
3. **Parsing:** implement email parsing and all observed ICICI formats. Parser output must use the bank's occurrence timestamp, not upload time, and must reject messages that merely mention money.
4. **Incident fix:** correct the smallest root cause, then add a regression test that demonstrably fails against the old implementation and passes against the fixed implementation.
5. **Ingestion:** establish a deterministic real-transaction identity. Replayed message IDs, overlapping corpus files, and multiple messages for one transaction must converge to one transaction while retaining all source message IDs.
6. **Classification and reports:** classify each transaction exactly once, then implement ledger, summary, and reconciliation output. Use `BigDecimal` with explicit two-decimal normalization and stable ordering.
7. **Checkpoint validation:** compare output to `fixtures/corpus-a-totals.json`. If a value differs, investigate and document why; never tune logic to a checkpoint without a data-backed rule.
8. **Document store:** choose DynamoDB or MongoDB, document the decision, implement the three declared access patterns, and make the database start through Docker Compose.
9. **Migration safety:** implement idempotent backfill from dirty SQL and a field-level consistency checker that detects altered documents, not just row-count changes.
10. **Release evidence:** run focused tests, `verify.sh`, the compose workflow, the 100,000-transaction query measurements, and prepare the five-line incident update and walkthrough.

## Definition of done

- `./verify.sh` remains usable and the complete pipeline produces all three report files.
- A corpus replay or overlap does not change the ledger.
- Every ledger row has at least one source message and every source message is traceable through the document store.
- Amounts are positive, exact, and formatted to two decimal places.
- Summary totals exclude `TRANSFER` from spend/income and report `MICRO` separately.
- Reconciliation reports unexplained movements rather than hiding them.
- Backfill is safe after duplicate SQL rows, reruns, and partial failure.
- Consistency checking identifies exactly which transaction and fields diverge.
- README contains the database choice, decision log, data-made decisions, AI disclosure/mistake, unfinished work, and six query efficiency numbers.

## Evidence convention

For each completed tracker row, add a short note containing the command or test, the relevant output, and the date. Keep detailed reasoning in the logs below rather than expanding the tracker into a prose journal.

## Open implementation questions

These are deliberately unanswered until corpus inspection or a focused experiment provides evidence:

- What exact fields define the identity of one real transaction when amount, date, account, and merchant are not all unique?
- How are transfer counterparties recognized reliably across the three accounts?
- Which balance messages are authoritative enough for reconciliation, and how should a missing or stale balance be represented?
- Are email and SMS timestamps always in IST, or must the parser normalize an explicit offset?
- Which document-store emulator and production-compatible indexing strategy best support the required measurements?
- What is the expected behavior when two messages disagree on a transaction field?

Record the answer, evidence, and resulting code change in `DECISION_LOG.md` before closing each question.
