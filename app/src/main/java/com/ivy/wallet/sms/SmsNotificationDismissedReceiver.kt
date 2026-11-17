package com.ivy.wallet.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.ivy.legacy.sms.SmsReviewQueue
import dagger.hilt.android.AndroidEntryPoint
import java.util.UUID
import javax.inject.Inject

/** The user swiped an SMS transaction's notification away, so don't bring it back. */
@AndroidEntryPoint
class SmsNotificationDismissedReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reviewQueue: SmsReviewQueue

    override fun onReceive(context: Context, intent: Intent) {
        val transactionId = intent.getStringExtra(EXTRA_TRANSACTION_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?: return
        reviewQueue.forget(transactionId)
    }

    companion object {
        const val EXTRA_TRANSACTION_ID = "transaction_id"
    }
}
