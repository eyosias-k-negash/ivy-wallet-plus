package com.ivy.wallet.android.notification

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.ContextCompat
import com.ivy.ui.R

enum class IvyNotificationChannel(
    val channelId: String,
    val channelName: String,
    val description: String,
    val importance: Int = NotificationManager.IMPORTANCE_MAX,
    val bypassDnd: Boolean = true
) {
    TRANSACTION_REMINDER(
        channelId = "transaction_reminder",
        channelName = "Transaction reminder",
        description = "Reminding you to record your transactions on a daily basis.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    ),
    SMS_TRANSACTION(
        channelId = "sms_transaction",
        channelName = "SMS Transactions",
        description = "Notifications for transactions detected from SMS.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    ),
    OVERDUE_PAYMENT(
        channelId = "overdue_payment",
        channelName = "Overdue planned payments",
        description = "Daily alerts for planned payments that are past their due date and still unpaid.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        // Only takes effect once the user lets the app override Do Not Disturb
        bypassDnd = true
    ),
    ACCOUNT_OUT_OF_SYNC(
        channelId = "account_out_of_sync",
        channelName = "Account out of sync",
        description = "Warnings when a bank SMS reports a different balance than the app shows.",
        importance = NotificationManager.IMPORTANCE_HIGH,
        bypassDnd = false
    );

    @SuppressLint("WrongConstant")
    fun create(context: Context): NotificationChannel {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        val colorPurple = ContextCompat.getColor(context, R.color.green)
        val channel = NotificationChannel(
            channelId,
            channelName,
            importance
        )
        channel.description = description
        channel.lightColor = colorPurple
        channel.enableLights(true)
        channel.enableVibration(true)
        channel.setBypassDnd(bypassDnd)
        return channel
    }
}
