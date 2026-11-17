package com.ivy.wallet.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.base.model.TransactionType
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.TransactionId
import com.ivy.data.model.Transfer
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.legacy.sms.SmsReviewQueue
import com.ivy.wallet.RootActivity
import com.ivy.wallet.RootViewModel
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class SmsTransactionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService,
    private val reviewQueue: SmsReviewQueue,
    private val transactionRepository: TransactionRepository,
    private val accountRepository: AccountRepository,
) {
    fun showSmsTransactionNotification(
        transactionId: UUID,
        amount: Double,
        type: TransactionType,
        accountName: String,
        timeMillis: Long = System.currentTimeMillis(),
        silent: Boolean = false,
    ) {
        val intent = Intent(context, RootActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(RootViewModel.EXTRA_EDIT_TRANSACTION_ID, transactionId.toString())
            putExtra(RootViewModel.EXTRA_TRANSACTION_TYPE, type.name)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            transactionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = PendingIntent.getBroadcast(
            context,
            transactionId.hashCode(),
            Intent(context, SmsNotificationDismissedReceiver::class.java)
                .putExtra(SmsNotificationDismissedReceiver.EXTRA_TRANSACTION_ID, transactionId.toString()),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stays after a tap and is skipped by "Clear all", so it's there until the
        // transaction is saved or deleted. Since Android 14 it can still be swiped away.
        val notification = notificationService.defaultIvyNotification(
            channel = IvyNotificationChannel.SMS_TRANSACTION,
            autoCancel = false,
        )
            .setContentTitle("Transaction created: $accountName")
            .setContentText("Tap to edit transaction for $amount")
            .setContentIntent(pendingIntent)
            .setDeleteIntent(dismissIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(silent)
            .setWhen(timeMillis)
            .setShowWhen(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationService.showNotification(notification, SmsReviewQueue.notificationId(transactionId))
    }

    /**
     * Posts the notifications of unreviewed SMS transactions again, without sound.
     * Needed after a reboot or force stop, which clear all of the app's notifications.
     */
    suspend fun restorePendingNotifications() {
        for (transactionId in reviewQueue.pending()) {
            val transaction = transactionRepository.findById(TransactionId(transactionId))
            if (transaction == null) {
                // Deleted somewhere other than its edit screen
                reviewQueue.forget(transactionId)
                continue
            }
            val (amount, type, accountId) = when (transaction) {
                is Income -> Triple(transaction.value.amount.value, TransactionType.INCOME, transaction.account)
                is Expense -> Triple(transaction.value.amount.value, TransactionType.EXPENSE, transaction.account)
                is Transfer -> Triple(transaction.fromValue.amount.value, TransactionType.TRANSFER, transaction.fromAccount)
            }
            val accountName = accountRepository.findById(accountId)?.name?.value.orEmpty()

            showSmsTransactionNotification(
                transactionId = transactionId,
                amount = amount,
                type = type,
                accountName = accountName,
                timeMillis = transaction.time.toEpochMilli(),
                silent = true,
            )
        }
    }
}
