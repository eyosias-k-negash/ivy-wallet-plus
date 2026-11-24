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
        result.amountSource shouldBe SmsAmountSource.AMOUNT_REGEX
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

        result.amountSource shouldBe SmsAmountSource.MASTER_REGEX
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
    fun `fee adds up every group of every match`() {
        val text = "$body Service charge USD 5.00 and VAT USD 0.75."
        val result = parse(
            multiRules.copy(feeRegex = "(?:Service charge|VAT) USD ([0-9,.]+\\d)", captureFee = true),
            text
        )

        result.feeStep!!.matches.size shouldBe 2
        result.fee shouldBe 5.75
        result.transaction!!.fee shouldBe 5.75
        result.transaction!!.amount shouldBe 1250.0
    }

    @Test
    fun `fee isn't saved when fee capture is off`() {
        val text = "$body Service charge USD 5.00."
        val result = parse(multiRules.copy(feeRegex = "Service charge USD ([0-9,.]+\\d)"), text)

        result.fee shouldBe 5.0
        result.transaction!!.fee shouldBe null
    }

    @Test
    fun `zero or unreadable fee gives no fee`() {
        val rules = multiRules.copy(feeRegex = "Service charge USD (\\S+?)\\.?$", captureFee = true)

        parse(rules, "$body Service charge USD 0.00").transaction!!.fee shouldBe null
        parse(rules, "$body Service charge USD five").fee shouldBe null
        parse(rules, "$body Service charge USD five").transaction!!.fee shouldBe null
    }

    @Test
    fun `capture menu turns off date, description and balance`() {
        val result = parse(
            multiRules.copy(captureDate = false, captureDescription = false, captureBalance = false)
        )

        val transaction = result.transaction!!
        transaction.dateTimeMillis shouldBe 0L
        transaction.description shouldBe null
        transaction.balance shouldBe null
        // The tester still sees what the regexes found.
        result.parsedDateTimeMillis shouldNotBe null
        result.description shouldBe "Payment to ABC Supermarket"
        result.balance shouldBe "8,400.50"
    }

    @Test
    fun `balance without description capture is the whole description`() {
        val result = parse(multiRules.copy(captureDescription = false))

        result.transaction!!.description shouldBe "(Bal: 8,400.50)"
        result.transaction!!.balance shouldBe 8400.5
    }

    @Test
    fun `invalid regex of a field that isn't captured doesn't stop the transaction`() {
        val result = parse(multiRules.copy(descriptionRegex = "for (.+", captureDescription = false))

        result.transaction shouldNotBe null
        result.failure shouldBe null
    }

    @Test
    fun `sender filter matches address or contact name`() {
        SmsParser.senderMatches("examplebank", "EXAMPLEBANK", null) shouldBe true
        SmsParser.senderMatches("Bank", "+15550100000", "My Bank") shouldBe true
        SmsParser.senderMatches("EXAMPLEBANK", "OtherWallet", null) shouldBe false
        SmsParser.senderMatches(null, "EXAMPLEBANK", null) shouldBe false
    }
}
