package com.ivy.wallet.sms

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.SubscriptionManager
import com.ivy.base.model.TransactionType
import com.ivy.data.repository.AccountRepository
import com.ivy.legacy.utils.isNotNullOrBlank
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountRepository: AccountRepository

    @Inject
    lateinit var notificationManager: SmsTransactionNotificationManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val subscriptionId = intent.getIntExtra("subscription", -1)

        for (sms in messages) {
            val sender = sms.originatingAddress ?: continue
            val body = sms.messageBody ?: continue

            scope.launch {
                processSms(context, sender, body, subscriptionId)
            }
        }
    }

    private suspend fun processSms(context: Context, sender: String, body: String, subscriptionId: Int) {
        val contactName = getContactName(context, sender)
        val accounts = accountRepository.findAll().filter { it.smsAutoListenEnabled }

        val matchingAccount = accounts.find { account ->
            // 1. Sender Identification (Bank name or number)
            val senderMatch = account.smsSenderPhone?.let { target ->
                sender.contains(target, ignoreCase = true) || 
                    (contactName?.contains(target, ignoreCase = true) ?: false)
            } ?: false
            
            if (!senderMatch) return@find false

            // 2. Safety Check: SIM Slot & Receiver Phone verification
            // If a specific SIM Slot (Subscription ID) is set, it MUST match the one that received the SMS
            if (account.smsSubscriptionId != null && account.smsSubscriptionId != subscriptionId) {
                return@find false
            }

            // If a Receiving Phone Number is set, verify it belongs to the SIM that received the SMS
            if (account.smsReceiverPhone.isNotNullOrBlank()) {
                val currentSimPhone = getSimPhoneNumber(context, subscriptionId)
                // We use 'contains' to handle variations in phone number formatting (e.g. + prefix, country codes)
                if (currentSimPhone != null && !currentSimPhone.contains(account.smsReceiverPhone!!)) {
                    return@find false
                }
            }
            
            true
        } ?: return

        val regex = matchingAccount.smsParsingRegex ?: com.ivy.legacy.Constants.DEFAULT_SMS_REGEX
        val pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE)
        val matcher = pattern.matcher(body)

        if (matcher.find()) {
            val amount: Double
            val transactionType: TransactionType
            var title: String
            var dateTimeMillis = System.currentTimeMillis()

            if (matchingAccount.useMultiRegex) {
                // 1. Amount Extraction
                amount = matchingAccount.smsAmountRegex?.let { regexStr ->
                    val m = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(body)
                    if (m.find()) m.group(1)?.replace(",", "")?.toDoubleOrNull() else null
                } ?: (matcher.group(1)?.replace(",", "")?.toDoubleOrNull() ?: return)

                // 2. Type Detection
                val isIncome = matchingAccount.smsIncomeRegex?.let { regexStr ->
                    Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(body).find()
                } ?: false
                
                val isExpense = matchingAccount.smsExpenseRegex?.let { regexStr ->
                    Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(body).find()
                } ?: false

                transactionType = when {
                    isIncome -> TransactionType.INCOME
                    isExpense -> TransactionType.EXPENSE
                    else -> detectTypeByKeywords(body)
                }

                // 3. Date/Time Extraction
                matchingAccount.smsDateTimeRegex?.let { regexStr ->
                    val m = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(body)
                    if (m.find()) {
                        val dateStr = m.group(1)
                        if (dateStr != null) {
                            // Simple parser attempt, in a real app this might need more robust handling
                            try {
                                val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                dateTimeMillis = format.parse(dateStr)?.time ?: dateTimeMillis
                            } catch (_: Exception) {
                            }
                        }
                    }
                }

                // 4. Description Extraction
                val description = matchingAccount.smsDescriptionRegex?.let { regexStr ->
                    val m = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(body)
                    if (m.find()) m.group(1) else null
                }
                
                // 5. Balance Extraction (Optional, for logging or future use)
                val balance = matchingAccount.smsBalanceRegex?.let { regexStr ->
                    val m = Pattern.compile(regexStr, Pattern.CASE_INSENSITIVE).matcher(body)
                    if (m.find()) m.group(1) else null
                }

                val hash = generateHash(body)
                val typePrefix = if (transactionType == TransactionType.INCOME) "IN" else "OUT"
                title = description ?: "AUTO $typePrefix ${matchingAccount.name.value} $hash"
                if (balance != null) {
                    title += " (Bal: $balance)"
                }
            } else {
                // Single Regex Legacy Logic
                val amountStr = matcher.group(1)?.replace(",", "")
                amount = amountStr?.toDoubleOrNull() ?: return
                transactionType = detectTypeByKeywords(body)
                val hash = generateHash(body)
                val typePrefix = if (transactionType == TransactionType.INCOME) "IN" else "OUT"
                title = "AUTO $typePrefix ${matchingAccount.name.value} $hash"
            }

            notificationManager.showSmsTransactionNotification(
                amount = amount,
                type = transactionType,
                title = title,
                dateTime = dateTimeMillis,
                accountId = matchingAccount.id.value,
                tagIds = matchingAccount.autoTagId?.let { listOf(it.value) } ?: emptyList(),
                accountName = matchingAccount.name.value
            )
        }
    }

    private fun detectTypeByKeywords(body: String): TransactionType {
        val incomeKeywords = listOf("credited", "received", "deposited", "added")
        val expenseKeywords = listOf("debited", "sent", "withdrawn", "spent", "paid", "purchased")

        return when {
            incomeKeywords.any { body.contains(it, ignoreCase = true) } -> TransactionType.INCOME
            expenseKeywords.any { body.contains(it, ignoreCase = true) } -> TransactionType.EXPENSE
            else -> TransactionType.EXPENSE // Default to expense if unknown
        }
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

    private fun getContactName(context: Context, phoneNumber: String): String? {
        return try {
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI, 
                Uri.encode(phoneNumber)
            )
            val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    cursor.getString(0)
                } else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun generateHash(text: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(6)
            .uppercase()
    }

    companion object {
    }
}
