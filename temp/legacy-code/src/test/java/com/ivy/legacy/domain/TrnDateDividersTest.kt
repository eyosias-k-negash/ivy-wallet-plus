package com.ivy.legacy.domain

import arrow.core.Some
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.domain.pure.transaction.transactionsWithDateDividers
import com.ivy.wallet.domain.data.TransactionHistoryDateDivider
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Test

class TrnDateDividersTest {
    private val account = Account(name = "Checking", color = 0, currency = "USD")

    private suspend fun dividers(
        transactions: List<com.ivy.data.model.Transaction>,
        excludedFromStats: Set<java.util.UUID> = emptySet(),
    ) = transactionsWithDateDividers(
        transactions = transactions,
        baseCurrencyCode = "USD",
        accountRepository = mockk(relaxed = true),
        getAccount = { account },
        exchange = { _, amount -> Some(amount) },
        excludedFromStats = excludedFromStats,
    )

    @Test
    fun `every transaction counts when nothing is excluded`() = runTest {
        val items = dividers(
            listOf(
                income(account.id, 500.0),
                expense(account.id, 40.0),
                expense(account.id, 1000.0),
            )
        )

        val divider = items.first() as TransactionHistoryDateDivider
        divider.income shouldBe 500.0
        divider.expenses shouldBe 1040.0
    }

    @Test
    fun `excluded transactions are listed but left out of the day's totals`() = runTest {
        val rent = expense(account.id, 1000.0)
        val refund = income(account.id, 200.0)

        val items = dividers(
            transactions = listOf(
                income(account.id, 500.0),
                expense(account.id, 40.0),
                rent,
                refund,
            ),
            excludedFromStats = setOf(rent.id.value, refund.id.value),
        )

        items shouldHaveSize 5
        val divider = items.first() as TransactionHistoryDateDivider
        divider.income shouldBe 500.0
        divider.expenses shouldBe 40.0
    }
}
