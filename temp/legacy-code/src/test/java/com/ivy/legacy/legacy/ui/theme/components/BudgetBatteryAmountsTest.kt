package com.ivy.legacy.legacy.ui.theme.components

import io.kotest.matchers.shouldBe
import org.junit.Test

class BudgetBatteryAmountsTest {

    @Test
    fun `without planned payments it shows what's left, as before`() {
        val result = budgetBatteryAmounts(expenses = 300.0, planned = 0.0, budget = 1000.0)

        result.headline shouldBe BudgetBatteryHeadline.LEFT_TO_SPEND
        result.amount shouldBe 700.0
        result.percentSpent shouldBe 0.3
        result.percentCommitted shouldBe 0.3
    }

    @Test
    fun `planned payments come off what's free to spend`() {
        val result = budgetBatteryAmounts(expenses = 300.0, planned = 450.0, budget = 1000.0)

        result.headline shouldBe BudgetBatteryHeadline.FREE_TO_SPEND
        result.amount shouldBe 250.0
        result.percentSpent shouldBe 0.3
        result.percentCommitted shouldBe 0.75
    }

    @Test
    fun `planned payments that fill the budget exactly leave nothing free`() {
        val result = budgetBatteryAmounts(expenses = 400.0, planned = 600.0, budget = 1000.0)

        result.headline shouldBe BudgetBatteryHeadline.FREE_TO_SPEND
        result.amount shouldBe 0.0
    }

    @Test
    fun `planned payments pushing past the budget show the overshoot`() {
        val result = budgetBatteryAmounts(expenses = 450.0, planned = 600.0, budget = 1000.0)

        result.headline shouldBe BudgetBatteryHeadline.PLANNED_EXCEEDS_BUDGET
        result.amount shouldBe 50.0
        result.percentCommitted shouldBe 1.05
    }

    @Test
    fun `actual overspend wins over planned payments`() {
        val result = budgetBatteryAmounts(expenses = 1200.0, planned = 300.0, budget = 1000.0)

        result.headline shouldBe BudgetBatteryHeadline.BUDGET_EXCEEDED
        result.amount shouldBe 200.0
    }

    @Test
    fun `overspend without planned payments is unchanged`() {
        val result = budgetBatteryAmounts(expenses = 1250.0, planned = 0.0, budget = 1000.0)

        result.headline shouldBe BudgetBatteryHeadline.BUDGET_EXCEEDED
        result.amount shouldBe 250.0
    }
}
