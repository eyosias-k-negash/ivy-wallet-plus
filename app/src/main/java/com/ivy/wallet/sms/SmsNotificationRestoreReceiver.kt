package com.ivy.wallet.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Brings back the notifications of unreviewed SMS transactions after a reboot or app update. */
@AndroidEntryPoint
class SmsNotificationRestoreReceiver : BroadcastReceiver() {

    @Inject
    lateinit var notificationManager: SmsTransactionNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val pendingResult = goAsync()
        scope.launch {
            try {
                notificationManager.restorePendingNotifications()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
