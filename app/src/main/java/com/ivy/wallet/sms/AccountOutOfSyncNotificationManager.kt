package com.ivy.wallet.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeProvider
import com.ivy.data.model.Account
import com.ivy.data.model.AccountId
import com.ivy.data.repository.AccountRepository
import com.ivy.legacy.sms.SmsBalanceCheck
import com.ivy.legacy.utils.format
import com.ivy.navigation.EditTransactionScreen
import com.ivy.wallet.RootActivity
import com.ivy.wallet.RootViewModel
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import com.ivy.wallet.domain.action.account.CalcAccBalanceAct
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import dagger.hilt.android.qualifiers.ApplicationContext
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Warns when a bank SMS reports a different balance than the app shows for its account.
 *
 * Each account has at most one warning. The next SMS for the account replaces it, or removes
 * it once the balances match again.
 */
@Suppress("LongParameterList")
class AccountOutOfSyncNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService,
    private val accountRepository: AccountRepository,
    private val smsCategories: SmsCategories,
    private val calcAccBalanceAct: CalcAccBalanceAct,
    private val timeProvider: TimeProvider,
) {
    /**
     * Compares [account]'s balance with [smsBalance], reported by the SMS that just created
     * a transaction dated [transactionTime].
     */
    suspend fun check(account: Account, smsBalance: Double, transactionTime: Instant) {
        // The SMS's own time can be a little ahead of the phone's clock, and its transaction must count
        val appBalance = balance(account, until = maxOf(timeProvider.utcNow(), transactionTime))
        val difference = SmsBalanceCheck.difference(appBalance, smsBalance)
        if (difference == null) {
            dismiss(account.id.value)
        } else {
            show(account, smsBalance, appBalance, difference)
        }
    }

    fun dismiss(accountId: UUID) {
        notificationService.dismissNotification(NOTIFICATION_ID, accountId.notificationTag())
    }

    /**
     * The Add transaction screen that brings the account to [smsBalance], in the Rebalance
     * category. The difference is worked out again because the user may have fixed some of it
     * since the warning, for example by correcting the SMS transaction. Null when nothing is left.
     */
    suspend fun rebalanceScreen(accountId: UUID, smsBalance: Double): EditTransactionScreen? {
        val account = accountRepository.findById(AccountId(accountId)) ?: return null
        return SmsBalanceCheck.difference(balance(account), smsBalance)?.let { difference ->
            EditTransactionScreen(
                initialTransactionId = null,
                type = difference.transactionType(),
                accountId = accountId,
                categoryId = smsCategories.findOrCreate(REBALANCE_CATEGORY, icon = "calculator"),
                amount = difference.abs().toDouble(),
            )
        }
    }

    private suspend fun balance(account: Account, until: Instant? = null): BigDecimal =
        calcAccBalanceAct(
            CalcAccBalanceAct.Input(account = account, range = until?.let { ClosedTimeRange.to(it) })
        ).balance

    private fun show(account: Account, smsBalance: Double, appBalance: BigDecimal, difference: BigDecimal) {
        val accountId = account.id.value
        val currency = account.asset.code
        fun Double.withCurrency() = "${format(currency)} $currency"

        val balances = "Bank says ${smsBalance.withCurrency()}, app shows ${appBalance.toDouble().withCurrency()}"
        val type = if (difference.transactionType() == TransactionType.INCOME) "income" else "expense"
        val details = "$balances. Tap to add a ${difference.abs().toDouble().withCurrency()} $REBALANCE_CATEGORY $type."

        val rebalanceIntent = activityIntent(requestCode = "${accountId.notificationTag()}:rebalance".hashCode()) {
            putExtra(RootViewModel.EXTRA_REBALANCE_ACCOUNT_ID, accountId.toString())
            putExtra(RootViewModel.EXTRA_REBALANCE_SMS_BALANCE, smsBalance)
        }
        val fixManuallyIntent = activityIntent(requestCode = "${accountId.notificationTag()}:fix".hashCode()) {
            putExtra(RootViewModel.EXTRA_OPEN_ACCOUNT_ID, accountId.toString())
        }
        val dismissIntent = PendingIntent.getBroadcast(
            context,
            accountId.notificationTag().hashCode(),
            Intent(context, AccountOutOfSyncDismissReceiver::class.java)
                .putExtra(AccountOutOfSyncDismissReceiver.EXTRA_ACCOUNT_ID, accountId.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Like the SMS transaction's notification, it stays after "Clear all" until acted on
        val notification = notificationService.defaultIvyNotification(
            channel = IvyNotificationChannel.ACCOUNT_OUT_OF_SYNC,
            autoCancel = false,
        )
            .setContentTitle("Out of sync: ${account.name.value}")
            .setContentText(balances)
            .setStyle(NotificationCompat.BigTextStyle().bigText(details))
            .setContentIntent(rebalanceIntent)
            .addAction(0, "Dismiss", dismissIntent)
            .addAction(0, "Fix manually", fixManuallyIntent)
            .setOngoing(true)
            // The next SMS updates a warning that's still shown without a second alert
            .setOnlyAlertOnce(true)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationService.showNotification(notification, NOTIFICATION_ID, accountId.notificationTag())
    }

    private fun activityIntent(requestCode: Int, extras: Intent.() -> Unit): PendingIntent =
        PendingIntent.getActivity(
            context,
            requestCode,
            Intent(context, RootActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                extras()
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun BigDecimal.transactionType(): TransactionType =
        if (signum() > 0) TransactionType.INCOME else TransactionType.EXPENSE

    private fun UUID.notificationTag(): String = "$TAG_PREFIX$this"

    companion object {
        const val REBALANCE_CATEGORY = "Rebalance"

        // Every warning shares this id and is told apart by its account's tag
        private const val NOTIFICATION_ID = 3
        private const val TAG_PREFIX = "account_out_of_sync:"
    }
}
