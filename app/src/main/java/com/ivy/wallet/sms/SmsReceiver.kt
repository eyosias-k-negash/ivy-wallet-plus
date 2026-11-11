package com.ivy.wallet.sms

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import android.telephony.SubscriptionManager
import android.util.Log
import com.ivy.base.model.TransactionType
import com.ivy.data.model.TagId
import com.ivy.data.model.primitive.AssociationId
import com.ivy.data.repository.AccountRepository
import com.ivy.data.repository.TagRepository
import com.ivy.data.repository.TransactionRepository
import com.ivy.data.repository.mapper.TransactionMapper
import com.ivy.legacy.datamodel.temp.toDomain
import com.ivy.legacy.sms.SmsParser
import com.ivy.legacy.sms.SmsParsingRules
import com.ivy.legacy.sms.lookupContactName
import com.ivy.legacy.utils.isNotNullOrBlank
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

private const val TAG = "SmsReceiverDebug"

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var transactionRepository: TransactionRepository

    @Inject
    lateinit var tagRepository: TagRepository

    @Inject
    lateinit var transactionMapper: TransactionMapper

    @Inject
    lateinit var notificationManager: SmsTransactionNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        Log.i(TAG, "SMS Received: ${intent.action}")
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val subscriptionId = intent.getIntExtra("subscription", -1)
        Log.i(TAG, "SMS Info: messages count: ${messages.size}, subId: $subscriptionId")

        val pendingResult = goAsync()
        scope.launch {
            try {
                // A long SMS arrives as several parts in one broadcast. Join them so the
                // regexes see the whole message, like the SMS app shows it.
                for ((sender, parts) in messages.groupBy { it.originatingAddress }) {
                    if (sender == null) continue
                    val body = parts.joinToString("") { it.messageBody.orEmpty() }
                    if (body.isEmpty()) continue
                    Log.i(TAG, "SMS from: $sender (${parts.size} part(s))")

                    processSms(context, sender, body, subscriptionId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(context: Context, sender: String, body: String, subscriptionId: Int) {
        val contactName = lookupContactName(context, sender)
        Log.i(TAG, "Processing SMS from $sender (Contact: $contactName), subId: $subscriptionId")
        val accounts = accountRepository.findAll().filter { it.smsAutoListenEnabled }
        Log.d(TAG, "Found ${accounts.size} accounts with SMS auto-listen enabled")

        val matchingAccount = accounts.find { account ->
            // 1. Sender Identification (Bank name or number)
            if (!SmsParser.senderMatches(account.smsSenderPhone, sender, contactName)) {
                return@find false
            }

            // 2. Safety Check: SIM Slot & Receiver Phone verification
            if (account.smsSubscriptionId != null && account.smsSubscriptionId != subscriptionId) {
                return@find false
            }

            if (account.smsReceiverPhone.isNotNullOrBlank()) {
                val currentSimPhone = getSimPhoneNumber(context, subscriptionId)
                if (currentSimPhone != null && !currentSimPhone.contains(account.smsReceiverPhone!!)) {
                    return@find false
                }
            }
            
            true
        } ?: run {
            Log.i(TAG, "No matching account found for this SMS")
            return
        }

        Log.i(TAG, "Match found for account: ${matchingAccount.name.value}")

        val rules = SmsParsingRules(
            masterRegex = matchingAccount.smsParsingRegex,
            useMultiRegex = matchingAccount.useMultiRegex,
            incomeRegex = matchingAccount.smsIncomeRegex,
            expenseRegex = matchingAccount.smsExpenseRegex,
            amountRegex = matchingAccount.smsAmountRegex,
            dateTimeRegex = matchingAccount.smsDateTimeRegex,
            descriptionRegex = matchingAccount.smsDescriptionRegex,
            balanceRegex = matchingAccount.smsBalanceRegex,
        )
        val result = SmsParser.parse(
            body = body,
            rules = rules,
            accountName = matchingAccount.name.value,
            receivedAtMillis = System.currentTimeMillis(),
        )
        val transaction = result.transaction ?: run {
            Log.i(TAG, "SMS not converted: ${result.failure}")
            return
        }
        Log.i(TAG, "Parsed ${transaction.type} transaction (multi-regex: ${result.useMultiRegex})")

        saveTransactionAndNotify(
            amount = transaction.amount,
            transactionType = transaction.type,
            title = transaction.title,
            description = transaction.description,
            dateTimeMillis = transaction.dateTimeMillis,
            accountId = matchingAccount.id.value,
            tagIds = matchingAccount.autoTagId?.let { listOf(it.value) } ?: emptyList(),
            accountName = matchingAccount.name.value
        )
    }

    private suspend fun saveTransactionAndNotify(
        amount: Double,
        transactionType: TransactionType,
        title: String,
        description: String?,
        dateTimeMillis: Long,
        accountId: UUID,
        tagIds: List<UUID>,
        accountName: String
    ) {
        val transactionId = UUID.randomUUID()
        val legacyTransaction = com.ivy.base.legacy.Transaction(
            id = transactionId,
            accountId = accountId,
            type = transactionType,
            amount = BigDecimal.valueOf(amount),
            title = title,
            description = description,
            dateTime = Instant.ofEpochMilli(dateTimeMillis)
        )

        val domainTrn = legacyTransaction.toDomain(transactionMapper)
        domainTrn?.let {
            transactionRepository.save(it)
        }

        for (tagId in tagIds) {
            tagRepository.associateTagToEntity(
                associationId = AssociationId(transactionId),
                tagId = TagId(tagId)
            )
        }

        notificationManager.showSmsTransactionNotification(
            transactionId = transactionId,
            amount = amount,
            type = transactionType,
            accountName = accountName
        )
    }

    @SuppressLint("MissingPermission")
    private fun getSimPhoneNumber(context: Context, subId: Int): String? {
        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                subscriptionManager?.getPhoneNumber(subId)
            } else {
                @Suppress("DEPRECATION")
                subscriptionManager?.activeSubscriptionInfoList?.find { it.subscriptionId == subId }?.number
            }
        } catch (_: Exception) {
            null
        }
    }

    companion object {
    }
}
