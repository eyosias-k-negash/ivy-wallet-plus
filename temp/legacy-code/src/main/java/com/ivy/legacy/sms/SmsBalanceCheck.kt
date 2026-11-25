package com.ivy.legacy.sms

import java.math.BigDecimal

/** Compares an account's balance in the app with the balance its bank SMS reports. */
object SmsBalanceCheck {
    // Less than half a cent apart is rounding, not a missing transaction
    private val TOLERANCE = BigDecimal("0.005")

    // Smaller gaps, in the account's currency, aren't worth an out-of-sync warning
    private val WARNING_THRESHOLD = BigDecimal.TEN

    /**
     * What has to be added to [appBalance] to reach [smsBalance]: positive means income,
     * negative an expense. Null when the two already match.
     */
    fun difference(appBalance: BigDecimal, smsBalance: Double): BigDecimal? {
        val difference = BigDecimal.valueOf(smsBalance) - appBalance
        return difference.takeIf { it.abs() >= TOLERANCE }
    }

    /** The [difference], or null when it's under 10 either way and so not worth a warning. */
    fun warningDifference(appBalance: BigDecimal, smsBalance: Double): BigDecimal? =
        difference(appBalance, smsBalance)?.takeIf { it.abs() >= WARNING_THRESHOLD }
}
