# AI Disclosure and Mistake Log

This file is maintained during the build and will be summarized in `README.md`. It must distinguish suggestions from verified facts.

## Tools used

| Tool | Intended use | Verification rule |
|---|---|---|
| GitHub Copilot | Explore code, propose parsing and storage designs, draft tests and documentation | No generated code is accepted without corpus evidence, focused tests, and contract review |
| Local Gradle/JDK commands | Compile, run the pipeline, and execute tests | Command and result are recorded in the tracker or task notes |
| Git diff/history | Review scope and preserve real implementation steps | Do not squash away the reasoning or unrelated user changes |

## Concrete mistakes

### A-01 — Treating parser coverage as sufficient without a regression test

- **Date:** 2026-09-20
- **Context:** The initial implementation pass focused on widening the amount
  pattern and adding parser formats.
- **AI output (concise excerpt):** “The amount pattern can accept optional
  decimals; existing amount tests cover the parser utility.”
- **Why it was wrong/worse:** Existing tests covered only decimal transaction
  amounts. They did not reproduce the production shape where an integer
  transaction amount is followed by a decimal available balance, so the
  incident could recur without a test failure.
- **What we wrote instead:** Added an explicit `Rs.5`-before-`Avl Bal`
  regression case and recorded the exact first-match failure mechanism in the
  decision log. The parser-level incident test now passes in the functional
  suite.
- **Impact:** The fix was functionally correct, but the first test proposal was
  insufficient and had to be strengthened before the incident task could be
  closed.

### Mistake template

#### A-XX — Short description

- **Date:** YYYY-MM-DD
- **Context:** What task the AI was helping with.
- **AI output (verbatim or concise excerpt):** The incorrect or inferior suggestion.
- **Why it was wrong/worse:** The contract, corpus evidence, test, or operational concern that disproved it.
- **What we wrote instead:** The corrected implementation or decision.
- **Impact:** Code changed, tests added, and whether any work had to be discarded.

## Review questions for every AI suggestion

- Does it preserve the frozen `NormalizedTxn`, `Category`, and contract test?
- Does it distinguish uploaded messages from underlying transactions?
- Does it accept only a real bank transaction and reject hostile/non-transaction text?
- Does it preserve exact money and bank-stated occurrence time?
- Is it idempotent under replay and partial failure?
- Does it explain evidence, rather than tuning output to match the checkpoint?
