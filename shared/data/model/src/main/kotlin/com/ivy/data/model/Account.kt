package com.ivy.data.model

import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.model.sync.Identifiable
import com.ivy.data.model.sync.UniqueId
import java.util.UUID

@JvmInline
value class AccountId(override val value: UUID) : UniqueId

data class Account(
    override val id: AccountId,
    val name: NotBlankTrimmedString,
    val asset: AssetCode,
    val color: ColorInt,
    val icon: IconAsset?,
    val includeInBalance: Boolean,
    override val orderNum: Double,

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
    val autoTagId: TagId? = null,
) : Identifiable<AccountId>, Reorderable
