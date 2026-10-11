package com.ivy.wallet.domain.deprecated.logic.model

import androidx.compose.ui.graphics.Color
import java.util.UUID

data class CreateAccountData(
    val name: String,
    val currency: String,
    val color: Color,
    val icon: String?,
    val balance: Double,
    val includeBalance: Boolean = true,

    val smsAutoListenEnabled: Boolean = false,
    val smsSenderPhone: String? = null,
    val smsSubscriptionId: Int? = null,
    val smsReceiverPhone: String? = null,
    val smsParsingRegex: String? = null,
    val useMultiRegex: Boolean = false,
    val smsIncomeRegex: String? = null,
    val smsExpenseRegex: String? = null,
    val smsAmountRegex: String? = null,
    val smsDateTimeRegex: String? = null,
    val smsDescriptionRegex: String? = null,
    val smsBalanceRegex: String? = null,
    val autoTagId: UUID? = null,
)
