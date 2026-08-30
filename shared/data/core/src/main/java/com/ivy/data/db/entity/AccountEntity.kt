package com.ivy.data.db.entity

import androidx.annotation.Keep
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.ivy.base.kotlinxserilzation.KSerializerUUID
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.*

@Suppress("DataClassDefaultValues")
@Keep
@Serializable
@Entity(tableName = "accounts")
data class AccountEntity(
    @SerialName("name")
    val name: String,
    @SerialName("currency")
    val currency: String? = null,
    @SerialName("color")
    val color: Int,
    @SerialName("icon")
    val icon: String? = null,
    @SerialName("orderNum")
    val orderNum: Double = 0.0,
    @SerialName("includeInBalance")
    val includeInBalance: Boolean = true,

    @SerialName("smsAutoListenEnabled")
    @ColumnInfo(defaultValue = "0")
    val smsAutoListenEnabled: Boolean = false,
    @SerialName("smsSenderPhone")
    val smsSenderPhone: String? = null,
    @SerialName("smsSubscriptionId")
    val smsSubscriptionId: Int? = null,
    @SerialName("smsReceiverPhone")
    val smsReceiverPhone: String? = null,
    @SerialName("smsParsingRegex")
    val smsParsingRegex: String? = null,
    @SerialName("useMultiRegex")
    @ColumnInfo(defaultValue = "0")
    val useMultiRegex: Boolean = false,
    @SerialName("smsIncomeRegex")
    val smsIncomeRegex: String? = null,
    @SerialName("smsExpenseRegex")
    val smsExpenseRegex: String? = null,
    @SerialName("smsAmountRegex")
    val smsAmountRegex: String? = null,
    @SerialName("smsDateTimeRegex")
    val smsDateTimeRegex: String? = null,
    @SerialName("smsDescriptionRegex")
    val smsDescriptionRegex: String? = null,
    @SerialName("smsBalanceRegex")
    val smsBalanceRegex: String? = null,
    @SerialName("autoTagId")
    @Serializable(with = KSerializerUUID::class)
    val autoTagId: UUID? = null,

    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isSynced")
    val isSynced: Boolean = false,
    @Deprecated("Obsolete field used for cloud sync. Can't be deleted because of backwards compatibility")
    @SerialName("isDeleted")
    val isDeleted: Boolean = false,

    @PrimaryKey
    @SerialName("id")
    @Serializable(with = KSerializerUUID::class)
    val id: UUID = UUID.randomUUID()
)
