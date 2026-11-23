package com.ivy.wallet.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/** The Dismiss button on an account's out-of-sync warning. */
@AndroidEntryPoint
class AccountOutOfSyncDismissReceiver : BroadcastReceiver() {

    @Inject
    lateinit var outOfSyncNotificationManager: AccountOutOfSyncNotificationManager

    override fun onReceive(context: Context, intent: Intent) {
        val accountId = intent.getStringExtra(EXTRA_ACCOUNT_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        outOfSyncNotificationManager.dismiss(accountId)
    }

    companion object {
        const val EXTRA_ACCOUNT_ID = "account_id"
    }
}
