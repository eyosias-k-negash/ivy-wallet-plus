package com.ivy.legacy.datamodel.temp

import com.ivy.data.db.entity.AccountEntity
import com.ivy.legacy.datamodel.Account

fun AccountEntity.toLegacyDomain(): Account = Account(
    name = name,
    currency = currency,
    color = color,
    icon = icon,
    orderNum = orderNum,
    includeInBalance = includeInBalance,
    isSynced = isSynced,
    isDeleted = isDeleted,
    smsAutoListenEnabled = smsAutoListenEnabled,
    smsSenderPhone = smsSenderPhone,
    smsSubscriptionId = smsSubscriptionId,
    smsReceiverPhone = smsReceiverPhone,
    smsParsingRegex = smsParsingRegex,
    useMultiRegex = useMultiRegex,
    smsIncomeRegex = smsIncomeRegex,
    smsExpenseRegex = smsExpenseRegex,
    smsAmountRegex = smsAmountRegex,
    smsDateTimeRegex = smsDateTimeRegex,
    smsDescriptionRegex = smsDescriptionRegex,
    smsBalanceRegex = smsBalanceRegex,
    autoTagId = autoTagId,
    id = id
)
