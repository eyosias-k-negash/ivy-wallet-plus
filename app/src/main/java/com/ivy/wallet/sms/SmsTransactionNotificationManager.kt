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
        transactionId: UUID,
        amount: Double,
        type: TransactionType,
        accountName: String
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

        val notification = notificationService.defaultIvyNotification(IvyNotificationChannel.SMS_TRANSACTION)
            .setContentTitle("Transaction created: $accountName")
            .setContentText("Tap to edit transaction for $amount")
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        notificationService.showNotification(notification, transactionId.hashCode())
    }
}
