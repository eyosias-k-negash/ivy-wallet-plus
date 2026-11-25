package com.ivy.legacy.domain

import com.ivy.data.model.AccountId
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.PositiveValue
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.TransactionMetadata
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.PositiveDouble
import java.time.Instant
import java.util.UUID

internal val NOON: Instant = Instant.parse("2026-11-20T12:00:00Z")

private val NO_METADATA = TransactionMetadata(
    recurringRuleId = null,
    paidForDateTime = null,
    loanRecordId = null,
)

internal fun income(account: UUID, amount: Double): Transaction = Income(
    id = TransactionId(UUID.randomUUID()),
    title = null,
    description = null,
    category = null,
    time = NOON,
    settled = true,
    metadata = NO_METADATA,
    tags = emptyList(),
    value = PositiveValue(PositiveDouble.unsafe(amount), AssetCode.USD),
    account = AccountId(account),
)

internal fun expense(account: UUID, amount: Double): Transaction = Expense(
    id = TransactionId(UUID.randomUUID()),
    title = null,
    description = null,
    category = null,
    time = NOON,
    settled = true,
    metadata = NO_METADATA,
    tags = emptyList(),
    value = PositiveValue(PositiveDouble.unsafe(amount), AssetCode.USD),
    account = AccountId(account),
)
