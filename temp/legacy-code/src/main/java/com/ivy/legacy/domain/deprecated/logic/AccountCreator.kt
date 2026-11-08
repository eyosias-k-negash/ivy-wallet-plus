package com.ivy.legacy.domain.deprecated.logic

import androidx.compose.ui.graphics.toArgb
import arrow.core.raise.either
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.model.Account as DomainAccount
import com.ivy.data.model.AccountId
import com.ivy.data.model.primitive.AssetCode
import com.ivy.data.model.primitive.ColorInt
import com.ivy.data.model.primitive.IconAsset
import com.ivy.data.model.primitive.NotBlankTrimmedString
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.CurrencyRepository
import com.ivy.data.repository.TagRepository
import com.ivy.base.time.TimeProvider
import com.ivy.legacy.utils.ioThread
import com.ivy.wallet.domain.deprecated.logic.WalletAccountLogic
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.domain.pure.util.nextOrderNum
import java.util.UUID
import javax.inject.Inject
import com.ivy.legacy.datamodel.Account as LegacyAccount

class AccountCreator @Inject constructor(
    private val accountLogic: WalletAccountLogic,
    private val accountDao: AccountDao,
    private val accountRepository: AccountRepository,
    private val currencyRepository: CurrencyRepository,
    private val tagRepository: TagRepository,
    private val timeProvider: TimeProvider,
) {

    suspend fun createAccount(
        data: CreateAccountData,
        onRefreshUI: suspend () -> Unit
    ) {
        ioThread {
            val autoTagId = if (data.smsAutoListenEnabled && data.autoTagId == null) {
                createCanonicalTag(data.name)
            } else {
                data.autoTagId?.let { com.ivy.data.model.TagId(it) }
            }

            val account = either {
                DomainAccount(
                    id = AccountId(value = UUID.randomUUID()),
                    name = NotBlankTrimmedString.from(data.name).bind(),
                    asset = AssetCode.from(data.currency).bind(),
                    color = ColorInt(data.color.toArgb()),
                    icon = data.icon?.let(IconAsset::from)?.getOrNull(),
                    includeInBalance = data.includeBalance,
                    orderNum = accountDao.findMaxOrderNum().nextOrderNum(),
                    smsAutoListenEnabled = data.smsAutoListenEnabled,
                    smsSenderPhone = data.smsSenderPhone,
                    smsSubscriptionId = data.smsSubscriptionId,
                    smsReceiverPhone = data.smsReceiverPhone,
                    smsParsingRegex = data.smsParsingRegex,
                    useMultiRegex = data.useMultiRegex,
                    smsIncomeRegex = data.smsIncomeRegex,
                    smsExpenseRegex = data.smsExpenseRegex,
                    smsAmountRegex = data.smsAmountRegex,
                    smsDateTimeRegex = data.smsDateTimeRegex,
                    smsDescriptionRegex = data.smsDescriptionRegex,
                    smsBalanceRegex = data.smsBalanceRegex,
                    autoTagId = autoTagId,
                )
            }.getOrNull() ?: return@ioThread
            accountRepository.save(account)

            val legacyAccount = LegacyAccount(
                name = data.name,
                currency = data.currency,
                color = data.color.toArgb(),
                icon = data.icon,
                includeInBalance = data.includeBalance,
                orderNum = accountDao.findMaxOrderNum().nextOrderNum(),
                isSynced = false,
                smsAutoListenEnabled = data.smsAutoListenEnabled,
                smsSenderPhone = data.smsSenderPhone,
                smsSubscriptionId = data.smsSubscriptionId,
                smsReceiverPhone = data.smsReceiverPhone,
                smsParsingRegex = data.smsParsingRegex,
                useMultiRegex = data.useMultiRegex,
                smsIncomeRegex = data.smsIncomeRegex,
                smsExpenseRegex = data.smsExpenseRegex,
                smsAmountRegex = data.smsAmountRegex,
                smsDateTimeRegex = data.smsDateTimeRegex,
                smsDescriptionRegex = data.smsDescriptionRegex,
                smsBalanceRegex = data.smsBalanceRegex,
                autoTagId = autoTagId?.value,
                id = account.id.value
            )
            accountLogic.adjustBalance(
                account = legacyAccount,
                actualBalance = 0.0,
                newBalance = data.balance
            )
        }

        onRefreshUI()
    }

    private suspend fun createCanonicalTag(accountName: String): com.ivy.data.model.TagId {
        val tagName = "AUTO-$accountName"
        val existingTags = tagRepository.findByText(tagName)
        if (existingTags.isNotEmpty()) {
            return existingTags.first().id
        }

        val tag = com.ivy.data.model.Tag(
            id = com.ivy.data.model.TagId(UUID.randomUUID()),
            name = NotBlankTrimmedString.from(tagName).getOrNull()!!,
            description = "Auto-created tag for SMS transactions",
            color = ColorInt(0), // Default color
            icon = null,
            orderNum = 0.0,
            creationTimestamp = timeProvider.utcNow()
        )
        tagRepository.save(tag)
        return tag.id
    }

    suspend fun editAccount(
        legacyAccount: LegacyAccount,
        newBalance: Double,
        onRefreshUI: suspend () -> Unit
    ) {
        val updatedLegacyAccount = legacyAccount.copy(
            isSynced = false
        )
        ioThread {
            val autoTagId = if (legacyAccount.smsAutoListenEnabled && legacyAccount.autoTagId == null) {
                createCanonicalTag(legacyAccount.name)
            } else {
                legacyAccount.autoTagId?.let { com.ivy.data.model.TagId(it) }
            }

            val account = updatedLegacyAccount.copy(autoTagId = autoTagId?.value)
                .toDomainAccount(currencyRepository).getOrNull()
                ?: return@ioThread
            accountRepository.save(account)

            accountLogic.adjustBalance(
                account = updatedLegacyAccount.copy(autoTagId = autoTagId?.value),
                actualBalance = accountLogic.calculateAccountBalance(updatedLegacyAccount),
                newBalance = newBalance
            )
        }

        onRefreshUI()
    }
}
