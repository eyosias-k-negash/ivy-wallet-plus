package com.ivy.legacy.sms

import java.math.BigDecimal

/** Compares an account's balance in the app with the balance its bank SMS reports. */
object SmsBalanceCheck {
    // Less than half a cent apart is rounding, not a missing transaction
    private val TOLERANCE = BigDecimal("0.005")

    /**
     * What has to be added to [appBalance] to reach [smsBalance]: positive means income,
     * negative an expense. Null when the two already match.
     */
    fun difference(appBalance: BigDecimal, smsBalance: Double): BigDecimal? {
        val difference = BigDecimal.valueOf(smsBalance) - appBalance
        return difference.takeIf { it.abs() >= TOLERANCE }
    }
}
