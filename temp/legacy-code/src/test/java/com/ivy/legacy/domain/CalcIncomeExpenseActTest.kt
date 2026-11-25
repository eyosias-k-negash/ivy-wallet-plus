package com.ivy.legacy.domain

import arrow.core.Some
import com.ivy.domain.usecase.wallet.ExcludedStatsTagsUseCase
import com.ivy.legacy.datamodel.Account
import com.ivy.wallet.domain.action.account.AccTrnsAct
import com.ivy.wallet.domain.action.exchange.ExchangeAct
import com.ivy.wallet.domain.action.wallet.CalcIncomeExpenseAct
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

class CalcIncomeExpenseActTest {
    private val checking = Account(name = "Checking", color = 0, currency = "USD")
    private val savings = Account(name = "Savings", color = 0, currency = "USD")

    private val rent = expense(checking.id, 1000.0)
    private val refund = income(savings.id, 300.0)
    private val transactions = mapOf(
        checking.id to listOf(income(checking.id, 500.0), expense(checking.id, 40.0), rent),
        savings.id to listOf(refund, expense(savings.id, 10.0)),
    )

    private val accTrnsAct = mockk<AccTrnsAct>()
    private val exchangeAct = mockk<ExchangeAct>()
    private val excludedStatsTagsUseCase = mockk<ExcludedStatsTagsUseCase>()

    private val act = CalcIncomeExpenseAct(accTrnsAct, exchangeAct, excludedStatsTagsUseCase)

    @Before
    fun setUp() {
        coEvery { accTrnsAct(any()) } answers {
            transactions.getValue(firstArg<AccTrnsAct.Input>().accountId)
        }
        coEvery { exchangeAct(any()) } answers { Some(firstArg<ExchangeAct.Input>().amount) }
    }

    private suspend fun calc() = act(
        CalcIncomeExpenseAct.Input(
            baseCurrency = "USD",
            accounts = listOf(checking, savings),
            range = ClosedTimeRange(from = NOON.minusSeconds(60), to = NOON.plusSeconds(60)),
        )
    )

    @Test
    fun `every transaction counts when nothing is excluded`() = runTest {
        coEvery { excludedStatsTagsUseCase.excludedTransactionIds() } returns emptySet()

        val result = calc()

        result.income.compareTo(BigDecimal("800")) shouldBe 0
        result.expense.compareTo(BigDecimal("1050")) shouldBe 0
    }

    @Test
    fun `transactions with excluded tags are left out`() = runTest {
        coEvery {
            excludedStatsTagsUseCase.excludedTransactionIds()
        } returns setOf(rent.id.value, refund.id.value)

        val result = calc()

        result.income.compareTo(BigDecimal("500")) shouldBe 0
        result.expense.compareTo(BigDecimal("50")) shouldBe 0
    }
}
