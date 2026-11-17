package com.ivy.legacy.sms

import android.content.Context
import com.ivy.wallet.android.notification.NotificationService
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Transactions created from an SMS that the user hasn't reviewed yet.
 *
 * Android clears notifications on reboot and force stop, so the list is kept here and
 * their notifications are posted again from it. A transaction leaves the list when the
 * user taps Save or Delete on it, or swipes its notification away.
 */
@Singleton
class SmsReviewQueue @Inject constructor(
    @ApplicationContext context: Context,
    private val notificationService: NotificationService,
) {
    private val prefs = context.getSharedPreferences(PREFS_FILENAME, Context.MODE_PRIVATE)

    @Synchronized
    fun add(transactionId: UUID) {
        write(read() + transactionId.toString())
    }

    @Synchronized
    fun pending(): Set<UUID> = read().mapNotNull {
        runCatching { UUID.fromString(it) }.getOrNull()
    }.toSet()

    /** Drops the transaction from the list, leaving its notification alone. */
    @Synchronized
    fun forget(transactionId: UUID) {
        val ids = read()
        if (transactionId.toString() in ids) {
            write(ids - transactionId.toString())
        }
    }

    /** Drops the transaction from the list and removes its notification. */
    fun markReviewed(transactionId: UUID) {
        forget(transactionId)
        notificationService.dismissNotification(notificationId(transactionId))
    }

    // getStringSet's result mustn't be modified, so always work on a copy
    private fun read(): Set<String> = prefs.getStringSet(KEY_PENDING, emptySet()).orEmpty().toSet()

    private fun write(ids: Set<String>) {
        prefs.edit().putStringSet(KEY_PENDING, ids).apply()
    }

    companion object {
        private const val PREFS_FILENAME = "sms_review_queue"
        private const val KEY_PENDING = "pending_transaction_ids"

        fun notificationId(transactionId: UUID): Int = transactionId.hashCode()
    }
}
