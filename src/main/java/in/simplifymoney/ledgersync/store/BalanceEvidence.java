package in.simplifymoney.ledgersync.store;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** A bank-stated balance attached to one parsed transaction alert. */
public record BalanceEvidence(
        String accountLast4,
        OffsetDateTime occurredAt,
        BigDecimal balance,
        String sourceMessageId) {
}
