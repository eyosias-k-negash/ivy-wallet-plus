package com.ivy.transaction

import com.ivy.base.model.TransactionType
import com.ivy.legacy.datamodel.Account
import io.kotest.matchers.shouldBe
import org.junit.Test

class TransactionTypeConversionTest {

    private val checking = Account(name = "Checking", color = 0, currency = "USD")
    private val savings = Account(name = "Savings", color = 0, currency = "EUR")

    @Test
    fun `income becomes a transfer into its account, with no source`() {
        val result = convertTransactionType(
            from = TransactionType.INCOME,
            to = TransactionType.TRANSFER,
            account = checking,
            toAccount = null,
            amount = 1000.0,
            toAmount = 1000.0,
        )

        result shouldBe ConvertedTransaction(account = null, toAccount = checking, amount = 1000.0)
    }

    @Test
    fun `expense becomes a transfer out of its account, with no destination`() {
        val result = convertTransactionType(
            from = TransactionType.EXPENSE,
            to = TransactionType.TRANSFER,
            account = checking,
            toAccount = null,
            amount = 1000.0,
            toAmount = 1000.0,
        )

        result shouldBe ConvertedTransaction(account = checking, toAccount = null, amount = 1000.0)
    }

    @Test
    fun `transfer becomes income on the destination, with the amount it received`() {
        val result = convertTransactionType(
            from = TransactionType.TRANSFER,
            to = TransactionType.INCOME,
            account = checking,
            toAccount = savings,
            amount = 1000.0,
            toAmount = 920.0,
        )

        result shouldBe ConvertedTransaction(account = savings, toAccount = null, amount = 920.0)
    }

    @Test
    fun `transfer becomes an expense on the source, with the amount it sent`() {
        val result = convertTransactionType(
            from = TransactionType.TRANSFER,
            to = TransactionType.EXPENSE,
            account = checking,
            toAccount = savings,
            amount = 1000.0,
            toAmount = 920.0,
        )

        result shouldBe ConvertedTransaction(account = checking, toAccount = null, amount = 1000.0)
    }

    @Test
    fun `transfer without a destination becomes income on its source`() {
        val result = convertTransactionType(
            from = TransactionType.TRANSFER,
            to = TransactionType.INCOME,
            account = checking,
            toAccount = null,
            amount = 1000.0,
            toAmount = 1000.0,
        )

        result shouldBe ConvertedTransaction(account = checking, toAccount = null, amount = 1000.0)
    }

    @Test
    fun `transfer without a source becomes an expense on its destination`() {
        val result = convertTransactionType(
            from = TransactionType.TRANSFER,
            to = TransactionType.EXPENSE,
            account = null,
            toAccount = checking,
            amount = 1000.0,
            toAmount = 1000.0,
        )

        result shouldBe ConvertedTransaction(account = checking, toAccount = null, amount = 1000.0)
    }

    @Test
    fun `income to transfer and back keeps the account and amount`() {
        val transfer = convertTransactionType(
            from = TransactionType.INCOME,
            to = TransactionType.TRANSFER,
            account = checking,
            toAccount = null,
            amount = 1000.0,
            toAmount = 1000.0,
        )
        val income = convertTransactionType(
            from = TransactionType.TRANSFER,
            to = TransactionType.INCOME,
            account = transfer.account,
            toAccount = transfer.toAccount,
            amount = transfer.amount,
            toAmount = transfer.amount,
        )

        income shouldBe ConvertedTransaction(account = checking, toAccount = null, amount = 1000.0)
    }

    @Test
    fun `income and expense swap without touching the account`() {
        val result = convertTransactionType(
            from = TransactionType.INCOME,
            to = TransactionType.EXPENSE,
            account = checking,
            toAccount = null,
            amount = 1000.0,
            toAmount = 1000.0,
        )

        result shouldBe ConvertedTransaction(account = checking, toAccount = null, amount = 1000.0)
    }

    @Test
    fun `choosing the same type changes nothing`() {
        val result = convertTransactionType(
            from = TransactionType.TRANSFER,
            to = TransactionType.TRANSFER,
            account = checking,
            toAccount = savings,
            amount = 1000.0,
            toAmount = 920.0,
        )

        result shouldBe ConvertedTransaction(account = checking, toAccount = savings, amount = 1000.0)
    }
}
