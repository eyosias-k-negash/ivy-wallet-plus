package com.ivy.legacy.sms

import com.ivy.base.model.TransactionType
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class SmsParserTest {

    private val body = "Dear Customer your Account 1*****5678 has been debited with ETB 1,250.00 " +
        "on 10/11/2026 at 14:05:09 for Payment to ABC Supermarket. " +
        "Your Current Balance is ETB 8,400.50. Thank you."

    private val multiRules = SmsParsingRules(
        masterRegex = "debited|credited",
        useMultiRegex = true,
        incomeRegex = "credited",
        expenseRegex = "debited",
        amountRegex = "ETB\\s*([0-9,.]+)",
        dateTimeRegex = "on (\\d{2})/(\\d{2})/(\\d{4}) at (\\d{2}):(\\d{2}):(\\d{2})",
        descriptionRegex = "for (.+?)\\. Your",
        balanceRegex = "Balance is ETB ([0-9,]+\\.\\d{2})",
    )

    private fun parse(rules: SmsParsingRules, text: String = body) = SmsParser.parse(
        body = text,
        rules = rules,
        accountName = "CBE",
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
        result.description shouldBe "Account 1*****5678\nABC Supermarket"
    }

    @Test
    fun `amount falls back to master group 1`() {
        val result = parse(multiRules.copy(masterRegex = "debited with ETB ([0-9,.]+)", amountRegex = "ETB"))

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
        val result = parse(SmsParsingRules(masterRegex = "ETB ([0-9,.]+)", useMultiRegex = false))

        result.transaction!!.amount shouldBe 1250.0
        result.transaction!!.type shouldBe TransactionType.EXPENSE
        result.transaction!!.description shouldBe null
    }

    @Test
    fun `sender filter matches address or contact name`() {
        SmsParser.senderMatches("cbe", "CBE", null) shouldBe true
        SmsParser.senderMatches("Bank", "+251911000000", "My Bank") shouldBe true
        SmsParser.senderMatches("CBE", "Telebirr", null) shouldBe false
        SmsParser.senderMatches(null, "CBE", null) shouldBe false
    }
}
