package com.ivy.legacy.sms

import io.kotest.matchers.shouldBe
import org.junit.Test
import java.math.BigDecimal

class SmsBalanceCheckTest {

    @Test
    fun `matching balances need no rebalance`() {
        SmsBalanceCheck.difference(BigDecimal("8400.50"), 8400.5) shouldBe null
    }

    @Test
    fun `less than half a cent apart counts as matching`() {
        SmsBalanceCheck.difference(BigDecimal("100.004"), 100.0) shouldBe null
        SmsBalanceCheck.difference(BigDecimal("99.996"), 100.0) shouldBe null
    }

    @Test
    fun `app above the bank needs an expense`() {
        SmsBalanceCheck.difference(BigDecimal("8413.00"), 8400.5)!!
            .compareTo(BigDecimal("-12.5")) shouldBe 0
    }

    @Test
    fun `app below the bank needs an income`() {
        SmsBalanceCheck.difference(BigDecimal("-20"), 30.25)!!
            .compareTo(BigDecimal("50.25")) shouldBe 0
    }

    @Test
    fun `a single cent is out of sync`() {
        SmsBalanceCheck.difference(BigDecimal("10.00"), 10.01)!!
            .compareTo(BigDecimal("0.01")) shouldBe 0
    }

    @Test
    fun `a gap under 10 isn't worth a warning`() {
        SmsBalanceCheck.warningDifference(BigDecimal("100.00"), 109.99) shouldBe null
        SmsBalanceCheck.warningDifference(BigDecimal("100.00"), 90.01) shouldBe null
    }

    @Test
    fun `a gap of 10 either way is warned about`() {
        SmsBalanceCheck.warningDifference(BigDecimal("100.00"), 110.0)!!
            .compareTo(BigDecimal("10")) shouldBe 0
        SmsBalanceCheck.warningDifference(BigDecimal("100.00"), 90.0)!!
            .compareTo(BigDecimal("-10")) shouldBe 0
    }

    @Test
    fun `matching balances aren't warned about`() {
        SmsBalanceCheck.warningDifference(BigDecimal("8400.50"), 8400.5) shouldBe null
    }
}
