package com.ivy.legacy.notification

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.ivy.base.legacy.SharedPrefs
import com.ivy.base.model.TransactionType
import com.ivy.base.time.TimeConverter
import com.ivy.base.time.TimeProvider
import com.ivy.data.db.dao.read.AccountDao
import com.ivy.data.db.dao.read.CategoryDao
import com.ivy.data.model.Expense
import com.ivy.data.model.Income
import com.ivy.data.model.Transaction
import com.ivy.data.model.TransactionId
import com.ivy.data.model.Transfer
import com.ivy.data.model.getFromAccount
import com.ivy.data.model.getFromValue
import com.ivy.data.repository.TransactionRepository
import com.ivy.domain.AppStarter
import com.ivy.legacy.utils.format
import com.ivy.legacy.utils.ivyMinTime
import com.ivy.legacy.utils.timeNowLocal
import com.ivy.legacy.utils.toEpochSeconds
import com.ivy.wallet.android.notification.IvyNotificationChannel
import com.ivy.wallet.android.notification.NotificationService
import com.ivy.wallet.domain.pure.transaction.isOverdue
import dagger.hilt.android.qualifiers.ApplicationContext
import timber.log.Timber
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * Alerts the user about planned payments that are past their due date and still unpaid.
 *
 * Each overdue payment gets its own notification, bundled under a summary that makes the sound,
 * and they come back every morning until the payment is paid or skipped.
 */
@Suppress("LongParameterList")
class OverduePaymentReminderLogic @Inject constructor(
    @ApplicationContext
    private val appContext: Context,
    private val transactionRepository: TransactionRepository,
    private val accountDao: AccountDao,
    private val categoryDao: CategoryDao,
    private val notificationService: NotificationService,
    private val sharedPrefs: SharedPrefs,
    private val appStarter: AppStarter,
    private val timeProvider: TimeProvider,
    private val timeConverter: TimeConverter,
) {
    companion object {
        private const val UNIQUE_WORK_NAME = "overdue_payment_reminder_work"
        private const val REMINDER_HOUR = 9
        private const val REMINDER_INTERVAL_HOURS = 24L

        // Every overdue notification shares this id and is told apart by its tag
        private const val NOTIFICATION_ID = 2
        private const val PAYMENT_TAG_PREFIX = "overdue_payment:"
        private const val SUMMARY_TAG = "overdue_payment_summary"
        private const val GROUP_KEY = "com.ivy.wallet.OVERDUE_PAYMENTS"
        private const val SUMMARY_REQUEST_CODE = 2
        private const val SORT_KEY_DIGITS = 6
    }

    fun scheduleReminder() {
        val timeNowLocal = timeNowLocal()
        val todayReminderTime = timeNowLocal
            .withHour(REMINDER_HOUR)
            .withMinute(0)
            .withSecond(0)

        val nextReminderTime = if (todayReminderTime.isAfter(timeNowLocal)) {
            todayReminderTime
        } else {
            todayReminderTime.plusDays(1)
        }
        val initialDelaySeconds = nextReminderTime.toEpochSeconds() - timeNowLocal.toEpochSeconds()

        val workBuilder = PeriodicWorkRequestBuilder<OverduePaymentReminderWorker>(
            REMINDER_INTERVAL_HOURS,
            TimeUnit.HOURS
        )
            .setInitialDelay(initialDelaySeconds, TimeUnit.SECONDS)

        WorkManager
            .getInstance(appContext)
            .enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                workBuilder.build()
            )
    }

    /**
     * Shows a notification for every overdue payment, including ones the user swiped away,
     * and alerts once for the whole bundle.
     */
    suspend fun notifyOverdue() {
        update(alert = true)
    }

    /**
     * Silently brings the notifications already in the shade up to date: drops payments that were
     * paid, skipped, deleted or rescheduled, and recounts the summary. Never adds new ones.
     */
    @Suppress("TooGenericExceptionCaught")
    suspend fun refresh() {
        try {
            update(alert = false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to refresh overdue payment notifications")
        }
    }

    private suspend fun update(alert: Boolean) {
        val shownTags = notificationService.shownNotificationTags(NOTIFICATION_ID)
        val showNotifications = sharedPrefs.getBoolean(SharedPrefs.SHOW_NOTIFICATIONS, true)
        val overdue = if (showNotifications) findOverdue() else emptyList()

        val overdueTags = overdue.map { it.id.notificationTag() }.toSet()
        shownTags
            .filter { it != SUMMARY_TAG && it !in overdueTags }
            .forEach { notificationService.dismissNotification(NOTIFICATION_ID, it) }

        val toShow = if (alert) {
            overdue
        } else {
            overdue.filter { it.id.notificationTag() in shownTags }
        }
        if (toShow.isEmpty()) {
            notificationService.dismissNotification(NOTIFICATION_ID, SUMMARY_TAG)
            return
        }

        val payments = describe(toShow)
        payments.forEachIndexed { index, payment ->
            showPayment(payment, sortKey = index)
        }
        showSummary(payments, alert = alert)
    }

    private suspend fun findOverdue(): List<Transaction> {
        val today = timeProvider.localDateNow()
        val startOfToday = with(timeConverter) { today.atStartOfDay().toUTC() }
        return transactionRepository
            .findAllDueToBetween(startDate = ivyMinTime(), endDate = startOfToday)
            .filter { !it.settled && isOverdue(it, today) }
    }

    private suspend fun describe(transactions: List<Transaction>): List<OverduePayment> {
        val accountNames = accountDao.findAll().associate { it.id to it.name }
        val categoryNames = categoryDao.findAll().associate { it.id to it.name }
        val today = timeProvider.localDateNow()

        return transactions.map { transaction ->
            val value = transaction.getFromValue()
            val amount = "${value.amount.value.format(value.asset.code)} ${value.asset.code}"
            val fromAccount = accountNames[transaction.getFromAccount().value]
            val accounts = when (transaction) {
                is Expense -> fromAccount?.let { "from $it" }
                is Income -> fromAccount?.let { "to $it" }
                is Transfer -> {
                    val toAccount = accountNames[transaction.toAccount.value]
                    listOfNotNull(fromAccount?.let { "from $it" }, toAccount?.let { "to $it" })
                        .joinToString(" ")
                        .ifEmpty { null }
                }
            }
            val dueDate = with(timeConverter) { transaction.time.toLocalDate() }

            OverduePayment(
                id = transaction.id,
                type = transaction.type(),
                name = transaction.title?.value
                    ?: transaction.category?.let { categoryNames[it.value] }
                    ?: transaction.type().defaultName(),
                amount = amount,
                details = listOfNotNull(amount, accounts).joinToString(" ") +
                    " · ${dueText(dueDate, today)}",
                daysOverdue = ChronoUnit.DAYS.between(dueDate, today),
            )
        }
    }

    private fun showPayment(payment: OverduePayment, sortKey: Int) {
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.OVERDUE_PAYMENT,
                priority = NotificationCompat.PRIORITY_MAX
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // The post time means nothing here, the due date is in the text
            .setShowWhen(false)
            .setContentTitle("${payment.name} is overdue")
            .setContentText(payment.details)
            .setStyle(NotificationCompat.BigTextStyle().bigText(payment.details))
            .setContentIntent(payment.openIntent())
            .setGroup(GROUP_KEY)
            .setSortKey(sortKey.toString().padStart(SORT_KEY_DIGITS, '0'))
            // The summary makes the sound, once for the whole bundle
            .setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_SUMMARY)

        notificationService.showNotification(
            notification,
            NOTIFICATION_ID,
            payment.id.notificationTag()
        )
    }

    private fun showSummary(payments: List<OverduePayment>, alert: Boolean) {
        val single = payments.singleOrNull()
        val notification = notificationService
            .defaultIvyNotification(
                channel = IvyNotificationChannel.OVERDUE_PAYMENT,
                priority = NotificationCompat.PRIORITY_MAX
            )
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            // The post time means nothing here, the due date is in the text
            .setShowWhen(false)
            .setGroup(GROUP_KEY)
            .setGroupSummary(true)
            .setSilent(!alert)

        if (single != null) {
            notification
                .setContentTitle("${single.name} is overdue")
                .setContentText(single.details)
                .setContentIntent(single.openIntent())
        } else {
            val title = "${payments.size} overdue planned payments"
            val style = NotificationCompat.InboxStyle().setBigContentTitle(title)
            payments.forEach { style.addLine("${it.name} · ${it.amount} · ${it.daysOverdueText()}") }
            notification
                .setContentTitle(title)
                .setContentText(payments.joinToString(", ") { it.name })
                .setStyle(style)
                .setNumber(payments.size)
                .setContentIntent(
                    // Home lists them all in its Overdue section
                    pendingIntent(
                        intent = appStarter.getRootIntent().apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        },
                        requestCode = SUMMARY_REQUEST_CODE
                    )
                )
        }

        notificationService.showNotification(notification, NOTIFICATION_ID, SUMMARY_TAG)
    }

    private fun OverduePayment.openIntent(): PendingIntent = pendingIntent(
        intent = appStarter.getEditTransactionIntent(id.value, type),
        requestCode = id.value.hashCode()
    )

    private fun pendingIntent(intent: Intent, requestCode: Int): PendingIntent =
        PendingIntent.getActivity(
            appContext,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun dueText(dueDate: LocalDate, today: LocalDate): String {
        val pattern = if (dueDate.year == today.year) "MMM d" else "MMM d, yyyy"
        val date = dueDate.format(DateTimeFormatter.ofPattern(pattern))
        val days = ChronoUnit.DAYS.between(dueDate, today)
        val ago = if (days == 1L) "yesterday" else "$days days ago"
        return "due $date, $ago"
    }

    private fun OverduePayment.daysOverdueText(): String =
        if (daysOverdue == 1L) "1 day overdue" else "$daysOverdue days overdue"

    private fun TransactionId.notificationTag(): String = "$PAYMENT_TAG_PREFIX$value"

    private fun Transaction.type(): TransactionType = when (this) {
        is Expense -> TransactionType.EXPENSE
        is Income -> TransactionType.INCOME
        is Transfer -> TransactionType.TRANSFER
    }

    private fun TransactionType.defaultName(): String = when (this) {
        TransactionType.EXPENSE -> "Planned payment"
        TransactionType.INCOME -> "Planned income"
        TransactionType.TRANSFER -> "Planned transfer"
    }

    private data class OverduePayment(
        val id: TransactionId,
        val type: TransactionType,
        val name: String,
        val amount: String,
        val details: String,
        val daysOverdue: Long,
    )
}
