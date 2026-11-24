package com.ivy.legacy.sms

import com.ivy.base.model.TransactionType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SmsParserTest {

    private val body = "Example Bank: Account ****1234 debited with USD 1,250.00 " +
        "on 10/11/2026 at 14:05:09 for Payment to ABC Supermarket. " +
        "Balance is USD 8,400.50."

    private val multiRules = SmsParsingRules(
        masterRegex = "debited|credited",
        useMultiRegex = true,
        incomeRegex = "credited",
        expenseRegex = "debited",
        amountRegex = "USD\\s*([0-9,.]+)",
        dateTimeRegex = "on (\\d{2})/(\\d{2})/(\\d{4}) at (\\d{2}):(\\d{2}):(\\d{2})",
        descriptionRegex = "for (.+?)\\. Balance",
        balanceRegex = "Balance is USD ([0-9,]+\\.\\d{2})",
    )

    private fun parse(rules: SmsParsingRules, text: String = body) = SmsParser.parse(
        body = text,
        rules = rules,
        accountName = "Example Bank",
        receivedAtMillis = 0L,
    )

    @Test
    fun `multi-regex extracts every field`() {
        val result = parse(multiRules)

        val transaction = result.transaction
        transaction shouldNotBe null
        transaction!!.amount shouldBe 1250.0
        transaction.type shouldBe TransactionType.EXPENSE
        transaction.description shouldBe "Payment to ABC Supermarket\n(Bal: 8,400.50)"
        transaction.dateTimeMillis shouldBe LocalDateTime.of(2026, 11, 10, 14, 5, 9)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        result.typeSource shouldBe SmsTypeSource.EXPENSE_REGEX
        result.amountSource shouldBe SmsValueSource.FIELD_REGEX
        transaction.balance shouldBe 8400.5
    }

    @Test
    fun `no balance without a balance match`() {
        val result = parse(multiRules.copy(balanceRegex = "Available ([0-9.]+)"))

        result.transaction!!.balance shouldBe null
    }

    @Test
    fun `blank income regex doesn't turn every SMS into income`() {
        val result = parse(multiRules.copy(incomeRegex = "", expenseRegex = "  "))

        result.incomeStep!!.isSet shouldBe false
        result.type shouldBe TransactionType.EXPENSE
        result.typeSource shouldBe SmsTypeSource.KEYWORDS
    }

    @Test
    fun `description joins groups from every match`() {
        val result = parse(multiRules.copy(descriptionRegex = "(Account \\S+)|(ABC \\w+)"))

        result.descriptionStep!!.matches.size shouldBe 2
        result.description shouldBe "Account ****1234\nABC Supermarket"
    }

    @Test
    fun `amount falls back to master group 1`() {
        val result = parse(multiRules.copy(masterRegex = "debited with USD ([0-9,.]+)", amountRegex = "USD"))

        result.amountSource shouldBe SmsValueSource.MASTER_REGEX
        result.amount shouldBe 1250.0
    }

    @Test
    fun `master mismatch creates no transaction`() {
        val result = parse(multiRules.copy(masterRegex = "refunded"))

        result.transaction shouldBe null
        result.failure shouldBe "Master regex didn't match"
    }

    @Test
    fun `invalid regex is reported instead of crashing`() {
        val result = parse(multiRules.copy(descriptionRegex = "for (.+"))

        result.descriptionStep!!.error shouldNotBe null
        result.transaction shouldBe null
        result.failure shouldBe "Description regex is invalid"
    }

    @Test
    fun `single regex reads the amount from group 1 and the type from keywords`() {
        val result = parse(SmsParsingRules(masterRegex = "USD ([0-9,.]+)", useMultiRegex = false))

        result.transaction!!.amount shouldBe 1250.0
        result.transaction!!.type shouldBe TransactionType.EXPENSE
        result.transaction!!.description shouldBe null
        result.transaction!!.balance shouldBe null
    }

    @Test
    fun `sender filter matches address or contact name`() {
        SmsParser.senderMatches("examplebank", "EXAMPLEBANK", null) shouldBe true
        SmsParser.senderMatches("Bank", "+15550100000", "My Bank") shouldBe true
        SmsParser.senderMatches("EXAMPLEBANK", "OtherWallet", null) shouldBe false
        SmsParser.senderMatches(null, "EXAMPLEBANK", null) shouldBe false
    }

    // Named groups

    private val walletPaid = "Example Wallet: You have paid USD 25.00 to Corner Shop " +
        "on 20/11/2026 09:15:00. Your balance is USD 100.00."
    private val walletReceived = "Example Wallet: You have received USD 40.00 by transaction number AB12 " +
        "on 2026-11-21 18:30:05 from Example Bank. Your balance is USD 140.00."

    private val bothDateLayouts = "on (?:(?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4})|" +
        "(?<year2>\\d{4})-(?<month2>\\d{2})-(?<day2>\\d{2})) " +
        "(?<hour>\\d{2}):(?<minute>\\d{2}):(?<second>\\d{2})"

    private fun millis(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0) =
        LocalDateTime.of(year, month, day, hour, minute, second)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

    @Test
    fun `one named date pattern reads day-first and year-first layouts`() {
        val rules = multiRules.copy(masterRegex = "You have", dateTimeRegex = bothDateLayouts)

        parse(rules, walletPaid).parsedDateTimeMillis shouldBe millis(2026, 11, 20, 9, 15, 0)
        parse(rules, walletReceived).parsedDateTimeMillis shouldBe millis(2026, 11, 21, 18, 30, 5)
    }

    @Test
    fun `named date groups can come in any order`() {
        val rules = multiRules.copy(
            masterRegex = "You have",
            dateTimeRegex = "(?<year>\\d{4})-(?<month>\\d{2})-(?<day>\\d{2})",
        )

        val result = parse(rules, walletReceived)

        result.parsedDateTimeMillis shouldBe millis(2026, 11, 21)
        result.dateTimeSource shouldBe SmsValueSource.FIELD_REGEX
    }

    @Test
    fun `named date reads month names, 2-digit years and AM-PM`() {
        val rules = multiRules.copy(
            dateTimeRegex = "(?<day>\\d+)-(?<month>[a-z]+)-(?<year>\\d+),? " +
                "(?<hour>\\d+):(?<minute>\\d+) ?(?<ampm>[ap]m)",
        )
        fun dateOf(text: String) = parse(rules, "Account debited with USD 5.00 on $text.").parsedDateTimeMillis

        dateOf("10-Nov-26, 2:05 PM") shouldBe millis(2026, 11, 10, 14, 5)
        dateOf("3-September-2026 12:30 am") shouldBe millis(2026, 9, 3, 0, 30)
        dateOf("3-Sept-2026 12:30 pm") shouldBe millis(2026, 9, 3, 12, 30)
    }

    @Test
    fun `named date without a year uses the year the SMS arrived`() {
        val rules = multiRules.copy(dateTimeRegex = "on (?<day>\\d{2})/(?<month>\\d{2})")

        val result = SmsParser.parse(body, rules, "Example Bank", receivedAtMillis = millis(2025, 1, 2))

        result.parsedDateTimeMillis shouldBe millis(2025, 11, 10)
    }

    @Test
    fun `swapped day and month is explained and the receive time is used`() {
        val rules = multiRules.copy(dateTimeRegex = "on (?<month>\\d{2})/(?<day>\\d{2})/(?<year>\\d{4})")

        val result = parse(rules, body.replace("10/11/2026", "20/11/2026"))

        result.parsedDateTimeMillis shouldBe null
        result.dateTimeProblem shouldBe "Month 20 doesn't exist. Are the day and month the wrong way round?"
        result.transaction!!.dateTimeMillis shouldBe 0L
    }

    @Test
    fun `named date without day or month says so`() {
        val result = parse(multiRules.copy(dateTimeRegex = "on (?<year>\\d{4})"), body.replace("10/11/", ""))

        result.parsedDateTimeMillis shouldBe null
        result.dateTimeProblem shouldBe
            "Needs a day and a month, but no day or month group took part in the match."
    }

    @Test
    fun `positional date rejects days and months that don't exist`() {
        fun problemFor(date: String) = parse(multiRules, body.replace("10/11/2026", date)).dateTimeProblem

        problemFor("31/11/2026") shouldBe "Day 31 doesn't exist in November 2026."
        problemFor("13/20/2026") shouldBe "Month 20 doesn't exist."
        problemFor("10/11/2026") shouldBe null
    }

    @Test
    fun `named amount and balance ignore other groups`() {
        val rules = multiRules.copy(
            amountRegex = "(debited|credited) with (USD) (?<amount>[0-9,.]+)",
            balanceRegex = "(Balance) is USD (?<balance>[0-9,]+\\.\\d{2})",
        )

        val result = parse(rules)

        result.amount shouldBe 1250.0
        result.balance shouldBe "8,400.50"
    }

    @Test
    fun `named description keeps only description groups from every match`() {
        val rules = multiRules.copy(
            descriptionRegex = "(Account) (?<description>\\S+)|(for) (?<description2>[^.]+)",
        )

        val result = parse(rules)

        result.descriptionStep!!.matches.size shouldBe 2
        result.description shouldBe "****1234\nPayment to ABC Supermarket"
    }

    @Test
    fun `single regex with named groups fills every field`() {
        val rules = SmsParsingRules(
            masterRegex = "You have (?:(?<income>received)|(?<expense>paid)) USD (?<amount>[\\d,.]+) " +
                "(?<description>(?:to|by)(?:(?! on ).)*) $bothDateLayouts.*?balance is USD (?<balance>[\\d,.]+)\\.",
            useMultiRegex = false,
        )

        val paid = parse(rules, walletPaid)
        paid.transaction!!.amount shouldBe 25.0
        paid.type shouldBe TransactionType.EXPENSE
        paid.typeSource shouldBe SmsTypeSource.MASTER_REGEX
        paid.transaction!!.dateTimeMillis shouldBe millis(2026, 11, 20, 9, 15, 0)
        paid.transaction!!.description shouldBe "to Corner Shop\n(Bal: 100.00)"
        paid.transaction!!.balance shouldBe 100.0
        paid.descriptionSource shouldBe SmsValueSource.MASTER_REGEX

        val received = parse(rules, walletReceived)
        received.type shouldBe TransactionType.INCOME
        received.transaction!!.dateTimeMillis shouldBe millis(2026, 11, 21, 18, 30, 5)
    }

    @Test
    fun `multi-regex falls back to named groups in the master pattern`() {
        val rules = multiRules.copy(
            masterRegex = "debited with USD (?<amount>[0-9,.]+) on (?<day>\\d{2})/(?<month>\\d{2})/(?<year>\\d{4})",
            amountRegex = "",
            dateTimeRegex = "",
        )

        val result = parse(rules)

        result.amount shouldBe 1250.0
        result.amountSource shouldBe SmsValueSource.MASTER_REGEX
        result.parsedDateTimeMillis shouldBe millis(2026, 11, 10)
        result.dateTimeSource shouldBe SmsValueSource.MASTER_REGEX
        // The type and balance patterns are set, so they win over the master
        result.typeSource shouldBe SmsTypeSource.EXPENSE_REGEX
        result.balanceSource shouldBe SmsValueSource.FIELD_REGEX
    }

    @Test
    fun `master with named groups doesn't read group 1 as the amount`() {
        val rules = SmsParsingRules(masterRegex = "(?<day>\\d{2})/\\d{2}.*USD ([0-9,.]+)", useMultiRegex = false)

        val result = parse(rules)

        result.amount shouldBe null
        result.failure shouldBe "No amount found"
    }

    @Test
    fun `unknown and misplaced group names are reported`() {
        val rules = multiRules.copy(
            amountRegex = "USD (?<amout>[0-9,.]+)",
            balanceRegex = "Balance is USD (?<balance>[0-9,.]+) ?(?<day>\\d*)",
            incomeRegex = "(?<income>credited)",
        )

        val result = parse(rules)

        result.amountStep!!.warnings shouldBe listOf("<amout> isn't a name the parser reads, so it's ignored.")
        result.balanceStep!!.warnings shouldBe
            listOf("<day> is ignored here. It's read from the date/time or master pattern.")
        result.incomeStep!!.warnings shouldBe listOf("<income> is ignored here. Only the master pattern reads it.")
        result.master.warnings shouldBe emptyList()
    }

    @Test
    fun `group names ignore case and trailing digits`() {
        SmsGroup.of("Day") shouldBe SmsGroup.DAY
        SmsGroup.of("year12") shouldBe SmsGroup.YEAR
        SmsGroup.of("AMPM") shouldBe SmsGroup.AMPM
        SmsGroup.of("dayx") shouldBe null
        SmsGroup.of("2") shouldBe null
    }

    @Test
    fun `group name scanner skips escapes, classes and non-capturing groups`() {
        SmsParser.scanGroupNames("(?<day>\\d)(\\d)(?:x)(?<month2>y)") shouldBe listOf("day", null, "month2")
        SmsParser.scanGroupNames("\\((?<a>x)\\)") shouldBe listOf("a")
        SmsParser.scanGroupNames("[(](?<a>x)[^]()][[(]]") shouldBe listOf("a")
        SmsParser.scanGroupNames("\\Q(?<no>\\E(?<yes>x)") shouldBe listOf("yes")
        SmsParser.scanGroupNames("(?<=a)(?<!b)(?=c)(?!d)(?>e)(?i)(?i:f)(g)") shouldBe listOf(null)
    }
}
