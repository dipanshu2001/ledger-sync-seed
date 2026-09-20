package in.simplifymoney.ledgersync.parse;

import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.model.Direction;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Optional;

/**
 * Bank transaction alert emails.
 *
 * The bank email format carries its occurrence timestamp in the Date header.
 */
public final class EmailParser implements MessageParser {

    private static final Pattern DATE = Pattern.compile(
            "(?m)^Date:\\s*(?<date>.+)$");
    private static final Pattern ACCOUNT = Pattern.compile(
            "(?i)account ending (?<acct>\\d{4})");
    private static final Pattern MOVEMENT = Pattern.compile(
            "(?i)has been (?<dir>debited|credited) with "
                    + "(?<amount>(?:Rs\\.?|INR)\\s*[0-9,]+(?:\\.[0-9]{1,2})?)\\.");
    private static final Pattern MERCHANT = Pattern.compile(
            "(?m)^Merchant / Remarks:\\s*(?<merchant>.+)$");

    @Override
    public boolean supports(RawMessage m) {
        return "email".equals(m.channel());
    }

    @Override
    public Optional<ParsedTxn> parse(RawMessage m) {
        Matcher date = DATE.matcher(m.body());
        Matcher account = ACCOUNT.matcher(m.body());
        Matcher movement = MOVEMENT.matcher(m.body());
        Matcher merchant = MERCHANT.matcher(m.body());
        if (!date.find() || !account.find() || !movement.find() || !merchant.find()) {
            return Optional.empty();
        }

        OffsetDateTime occurredAt;
        try {
            occurredAt = OffsetDateTime.parse(date.group("date").trim(),
                    DateTimeFormatter.ofPattern("EEE, dd MMM yyyy HH:mm:ss xx", Locale.ENGLISH))
                    .withOffsetSameInstant(Dates.IST);
        } catch (DateTimeParseException e) {
            return Optional.empty();
        }
        BigDecimal amount = new BigDecimal(
                movement.group("amount").replaceAll("(?i)^Rs\\.?|^INR", "")
                        .replace(",", "").trim()).setScale(2);
        Direction direction = "debited".equalsIgnoreCase(movement.group("dir"))
                ? Direction.DEBIT : Direction.CREDIT;
        return Optional.of(new ParsedTxn(account.group("acct"), occurredAt, direction,
                amount, merchant.group("merchant").trim(), null, m.messageId()));
    }
}
