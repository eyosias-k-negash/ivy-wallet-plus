package com.ivy.wallet.sms

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.ivy.base.model.TransactionType
import com.ivy.wallet.RootActivity
import com.ivy.wallet.RootViewModel
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject

class SmsTransactionNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val notificationService: NotificationService
) {
    fun showSmsTransactionNotification(
        amount: Double,
        type: TransactionType,
        title: String,
        dateTime: Long,
        accountId: UUID,
        tagIds: List<UUID>,
        accountName: String
    ) {
        val intent = Intent(context, RootActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtra(RootViewModel.EXTRA_ADD_TRANSACTION_TYPE, type.name)
            putExtra(RootViewModel.EXTRA_AMOUNT, amount)
            putExtra(RootViewModel.EXTRA_TITLE, title)
            putExtra(RootViewModel.EXTRA_DATE_TIME, dateTime)
            putExtra(RootViewModel.EXTRA_ACCOUNT_ID, accountId.toString())
            putExtra(RootViewModel.EXTRA_TAG_IDS, tagIds.map { it.toString() }.toTypedArray())
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            accountId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = notificationService.defaultIvyNotification(IvyNotificationChannel.SMS_TRANSACTION)
            .setContentTitle("Transaction detected: $accountName")
            .setContentText("Tap to add transaction for $amount")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationService.showNotification(notification, accountId.hashCode())
    }
}
