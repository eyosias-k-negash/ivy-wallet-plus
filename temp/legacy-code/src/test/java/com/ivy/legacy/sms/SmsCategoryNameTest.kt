package com.ivy.legacy.sms

import io.kotest.matchers.shouldBe
import org.junit.Test

class SmsCategoryNameTest {

    private fun match(name: String, vararg existing: String) =
        SmsCategoryName.match(name, existing.toList()) { it }

    @Test
    fun `same name in another case or spacing is reused`() {
        match("Fees", "Food", "fees") shouldBe "fees"
        match("Rebalance", " REBALANCE ") shouldBe " REBALANCE "
        match("Bank fees", "Bank  Fees") shouldBe "Bank  Fees"
    }

    @Test
    fun `singular or plural is reused`() {
        match("Fees", "Fee") shouldBe "Fee"
        match("Rebalance", "Rebalances") shouldBe "Rebalances"
    }

    @Test
    fun `exact name wins over its singular or plural`() {
        match("Fees", "Fee", "Fees") shouldBe "Fees"
    }

    @Test
    fun `other names aren't reused`() {
        match("Fees", "Coffee", "Free", "Feeds") shouldBe null
        match("Fees") shouldBe null
    }
}
