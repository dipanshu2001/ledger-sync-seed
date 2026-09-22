package in.simplifymoney.ledgersync;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import in.simplifymoney.ledgersync.model.RawMessage;
import in.simplifymoney.ledgersync.parse.EmailParser;
import in.simplifymoney.ledgersync.parse.Dates;
import in.simplifymoney.ledgersync.parse.HdfcSmsParser;
import in.simplifymoney.ledgersync.parse.IciciSmsParser;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ParserRegressionTest {

    private static RawMessage message(String sender, String channel, String body) {
        return new RawMessage("test-message", channel, sender,
                OffsetDateTime.parse("2026-07-04T12:00:00+05:30"), "test-device", body);
    }

    @Test
    void integerTransactionDoesNotBecomeTheAvailableBalance() {
        var parsed = new HdfcSmsParser().parse(message(
                HdfcSmsParser.SENDER, "sms",
                "Rs.5 debited from a/c **4821 on 04-07-26 at 11:54 "
                        + "to UPI/WATER CAN. Avl Bal: Rs.92,213.10"));

        assertTrue(parsed.isPresent());
        assertEquals("5.00", parsed.get().amount().toPlainString());
    }

    @Test
    void parsesCompactIciciTransaction() {
        var parsed = new IciciSmsParser().parse(message(
                IciciSmsParser.SENDER, "sms",
                "ICICI Bank Acct XX9075 Dr INR 18 on 13-Aug-2026 21:00; "
                        + "UPI/MILK BOOTH ref no 293880518117. BalAvl Rs 51,210.63"));

        assertTrue(parsed.isPresent());
        assertEquals("9075", parsed.get().accountLast4());
        assertEquals("18.00", parsed.get().amount().toPlainString());
    }

    @Test
    void parsesHdfcTransactionEmail() {
        var parsed = new EmailParser().parse(message(
                "alerts@hdfcbank.net", "email",
                "Date: Wed, 01 Jul 2026 09:02:00 +0530\n"
                        + "Subject: Transaction alert on your account\n\n"
                        + "Your account ending 4821 has been credited with INR 45,000.\n"
                        + "Merchant / Remarks: SALARY CREDIT\n"
                        + "Transaction reference: 1597155421\n"));

        assertTrue(parsed.isPresent());
        assertEquals("45000.00", parsed.get().amount().toPlainString());
        assertEquals("SALARY CREDIT", parsed.get().merchant());
        assertEquals(Dates.IST, parsed.get().occurredAt().getOffset());
    }

    @Test
    void doesNotTreatCardAvailableLimitAsAccountBalance() {
        var parsed = new HdfcSmsParser().parse(message(
                HdfcSmsParser.SENDER, "sms",
                "Rs.1,111.11 spent on HDFC Bank Card x3310 at DMART "
                        + "on 18-07-26 17:09. Avl Limit: Rs.196,666.67"));

        assertTrue(parsed.isPresent());
        assertEquals(null, parsed.get().statedBalance());
    }

    @Test
    void parsesMandateDebitThatIncludesTheAccountBalance() {
        var parsed = new HdfcSmsParser().parse(message(
                HdfcSmsParser.SENDER, "sms",
                "E-mandate! Rs.649.00 will be deducted from your HDFC Bank A/c XX4821 "
                        + "on 22-07-26 at 06:15 for NETFLIX ENTERTAINMENT. "
                        + "Avl Bal: Rs.46,868.04"));

        assertTrue(parsed.isPresent());
        assertEquals("649.00", parsed.get().amount().toPlainString());
        assertEquals("4821", parsed.get().accountLast4());
        assertEquals("NETFLIX ENTERTAINMENT", parsed.get().merchant());
    }
}
