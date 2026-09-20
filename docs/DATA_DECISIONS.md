# Decisions Forced by Corpus A

This is evidence from the data, not a restatement of the assignment.

## Observations and current choices

| Observation | Choice | What more time/data could change |
|---|---|---|
| The same transaction is uploaded with different `message_id` values, often as an exact repeated body and sometimes in SMS/email or bank-format variants. | Group on account, occurrence time, direction, amount, and normalized merchant; retain every evidence ID. | A production reference or bank transaction ID would be safer than a heuristic key and should supersede it when available. |
| Corpus A contains 522 uploads but 257 real transactions; exact body duplication alone is not the whole duplication story. | Deduplication is performed after parsing on normalized transaction fields. | Test collision behavior on a corpus containing two same-minute same-amount same-merchant purchases. |
| `m-00022-2f118b` says `Rs.5` followed by `Avl Bal: Rs.92,213.10`; the old parser selected the balance. | Amount parsing accepts integer values and takes the first transaction amount matched by the bank-specific parser. | Add template-specific amount spans rather than relying on first-match semantics if new bank templates contain pre-transaction rupee figures. |
| Transfers pair `IMPS/P2A/PARAG KAPOOR` debits on one savings account with credits on the other. | Treat the PARAG KAPOOR counterparty as an owned-account transfer; do not classify it as spend or income. | Confirm ownership from account metadata instead of a corpus-specific counterparty string. |
| Small debits include `UPI/…` and `UPI …` merchant spellings; one `0.50` mandate verification uses the latter. | Treat a debit whose merchant starts with `UPI` and amount is at most ₹100 as `MICRO`. | Clarify whether all UPI mandate verification debits should be included or separately flagged in production. |
| The checkpoint contains two savings accounts but the corpus also yields a third HDFC card account. | Keep card transactions in the ledger and summary; do not discard them merely because the checkpoint has no card section. | Obtain card opening/closing/category checkpoints before claiming complete reconciliation for that account. |
| HDFC card alerts label the quoted value `Avl Limit`, not `Avl Bal`. | Keep the card transaction but do not treat available credit limit as a savings-account balance evidence point. | Add card statement/opening balance evidence if card reconciliation is required. |
| Email records contain a bank `Date:` header but no separate transaction timestamp. | Use the email's explicit bank alert date as occurrence time and preserve its offset. | Confirm with the provider whether email delivery can lag the transaction and whether a transaction timestamp will be added. |
