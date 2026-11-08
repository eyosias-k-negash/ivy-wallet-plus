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
import android.util.Log
import com.ivy.base.model.TransactionType
import com.ivy.data.repository.AccountRepository
import timber.log.Timber
import com.ivy.legacy.utils.isNotNullOrBlank
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.security.MessageDigest
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject

private const val TAG = "SmsReceiverDebug"

@AndroidEntryPoint
class SmsReceiver : BroadcastReceiver() {

    @Inject
    lateinit var accountRepository: AccountRepository

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
                for (sms in messages) {
                    val sender = sms.originatingAddress ?: continue
                    val body = sms.messageBody ?: continue
                    Log.i(TAG, "SMS from: $sender")

                    processSms(context, sender, body, subscriptionId)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun processSms(context: Context, sender: String, body: String, subscriptionId: Int) {
        val contactName = getContactName(context, sender)
        Log.i(TAG, "Processing SMS from $sender (Contact: $contactName), subId: $subscriptionId")
        val accounts = accountRepository.findAll().filter { it.smsAutoListenEnabled }
        Log.d(TAG, "Found ${accounts.size} accounts with SMS auto-listen enabled")

        val matchingAccount = accounts.find { account ->
            // 1. Sender Identification (Bank name or number)
            val senderMatch = account.smsSenderPhone?.let { target ->
                sender.contains(target, ignoreCase = true) || 
                    (contactName?.contains(target, ignoreCase = true) ?: false)
            } ?: false
            
            if (!senderMatch) {
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

        val flags = Pattern.CASE_INSENSITIVE or Pattern.DOTALL or Pattern.MULTILINE
        val masterRegex = matchingAccount.smsParsingRegex ?: com.ivy.legacy.Constants.DEFAULT_SMS_REGEX
        val masterMatcher = Pattern.compile(masterRegex, flags).matcher(body)

        // 1. Validation: Positive match anywhere in the SMS
        if (!masterMatcher.find()) {
            Log.i(TAG, "Master Regex mismatch. Regex: $masterRegex")
            return
        }
        Log.i(TAG, "Master Regex match found.")

        val amount: Double
        val transactionType: TransactionType
        var title: String
        var dateTimeMillis = System.currentTimeMillis()

        if (matchingAccount.useMultiRegex) {
            Log.i(TAG, "Using Advanced Multi-Regex parsing")
            
            // 2. Amount Extraction: 1st capturing group
            amount = matchingAccount.smsAmountRegex?.let { regexStr ->
                val m = Pattern.compile(regexStr, flags).matcher(body)
                val match = if (m.find() && m.groupCount() >= 1) {
                    m.group(1)?.replace(",", "")?.toDoubleOrNull()
                } else null
                Log.i(TAG, "Amount Regex Match: $regexStr -> $match")
                match
            } ?: run {
                val match = if (masterMatcher.groupCount() >= 1) masterMatcher.group(1)?.replace(",", "")?.toDoubleOrNull() else null
                Log.i(TAG, "Using Master Regex for amount. Result: $match")
                match
            } ?: return

            // 3. Balance Extraction: 1st capturing group
            val balanceValue = matchingAccount.smsBalanceRegex?.let { regexStr ->
                val m = Pattern.compile(regexStr, flags).matcher(body)
                val match = if (m.find() && m.groupCount() >= 1) m.group(1) else null
                Log.i(TAG, "Balance Regex Match: $regexStr -> $match")
                match
            }

            // 4. Type Detection
            val isIncome = matchingAccount.smsIncomeRegex?.let { regexStr ->
                val match = Pattern.compile(regexStr, flags).matcher(body).find()
                Log.i(TAG, "Income Regex Match: $regexStr -> $match")
                match
            } ?: false
            
            val isExpense = matchingAccount.smsExpenseRegex?.let { regexStr ->
                val match = Pattern.compile(regexStr, flags).matcher(body).find()
                Log.i(TAG, "Expense Regex Match: $regexStr -> $match")
                match
            } ?: false

            transactionType = when {
                isIncome -> TransactionType.INCOME
                isExpense -> TransactionType.EXPENSE
                else -> {
                    val kwType = detectTypeByKeywords(body)
                    Log.i(TAG, "Type detected by keywords: $kwType")
                    kwType
                }
            }
            Log.i(TAG, "Final Transaction Type: $transactionType")

            // 5. Date/Time Extraction: (DD),(MM),(YYYY), (HH), (mm), (ss)
            matchingAccount.smsDateTimeRegex?.let { regexStr ->
                val m = Pattern.compile(regexStr, flags).matcher(body)
                if (m.find()) {
                    val result = parseDateTimeGroups(m)
                    Log.i(TAG, "DateTime Regex Match: $regexStr -> Result Millis: $result")
                    dateTimeMillis = result ?: dateTimeMillis
                } else {
                    Log.i(TAG, "DateTime Regex: $regexStr -> No match found")
                }
            }

            // 6. Description Extraction: Join all groups from all matches (Global behavior)
            val description = matchingAccount.smsDescriptionRegex?.let { regexStr ->
                val m = Pattern.compile(regexStr, flags).matcher(body)
                val allGroups = mutableListOf<String>()
                while (m.find()) {
                    for (i in 1..m.groupCount()) {
                        m.group(i)?.let { allGroups.add(it) }
                    }
                }
                val result = if (allGroups.isNotEmpty()) allGroups.joinToString("\n") else null
                Log.i(TAG, "Description Regex Match: $regexStr -> Result: $result")
                result
            }

            val hash = generateHash(body)
            val typePrefix = if (transactionType == TransactionType.INCOME) "IN" else "OUT"
            title = "AUTO $typePrefix ${matchingAccount.name.value} $hash"
            
            val finalDescription = if (balanceValue != null) {
                if (description.isNotNullOrBlank()) "$description\n(Bal: $balanceValue)" else "(Bal: $balanceValue)"
            } else {
                description
            }

            notificationManager.showSmsTransactionNotification(
                amount = amount,
                type = transactionType,
                title = title,
                description = finalDescription,
                dateTime = dateTimeMillis,
                accountId = matchingAccount.id.value,
                tagIds = matchingAccount.autoTagId?.let { listOf(it.value) } ?: emptyList(),
                accountName = matchingAccount.name.value
            )
        } else {
            // Single Regex Legacy Logic
            val amountStr = if (masterMatcher.groupCount() >= 1) masterMatcher.group(1) else null
            amount = amountStr?.replace(",", "")?.toDoubleOrNull() ?: return
            transactionType = detectTypeByKeywords(body)
            val hash = generateHash(body)
            val typePrefix = if (transactionType == TransactionType.INCOME) "IN" else "OUT"
            title = "AUTO $typePrefix ${matchingAccount.name.value} $hash"

            notificationManager.showSmsTransactionNotification(
                amount = amount,
                type = transactionType,
                title = title,
                description = null,
                dateTime = dateTimeMillis,
                accountId = matchingAccount.id.value,
                tagIds = matchingAccount.autoTagId?.let { listOf(it.value) } ?: emptyList(),
                accountName = matchingAccount.name.value
            )
        }
    }

    private fun parseDateTimeGroups(matcher: java.util.regex.Matcher): Long? {
        val count = matcher.groupCount()
        if (count < 3) return null

        try {
            val calendar = Calendar.getInstance()
            val day = matcher.group(1)?.toIntOrNull() ?: return null
            val monthStr = matcher.group(2) ?: return null
            val year = matcher.group(3)?.let { 
                if (it.length == 2) 2000 + it.toInt() else it.toIntOrNull() 
            } ?: calendar.get(Calendar.YEAR)

            val month = monthStr.toIntOrNull()?.minus(1) ?: parseMonthName(monthStr) ?: return null

            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)

            if (count >= 6) {
                val hour = matcher.group(4)?.toIntOrNull() ?: 0
                val minute = matcher.group(5)?.toIntOrNull() ?: 0
                val second = matcher.group(6)?.toIntOrNull() ?: 0
                calendar.set(Calendar.HOUR_OF_DAY, hour)
                calendar.set(Calendar.MINUTE, minute)
                calendar.set(Calendar.SECOND, second)
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
            }
            calendar.set(Calendar.MILLISECOND, 0)

            return calendar.timeInMillis
        } catch (_: Exception) {
            return null
        }
    }

    private fun parseMonthName(monthName: String): Int? {
        val months = DateFormatSymbols.getInstance(Locale.ENGLISH).shortMonths
        val index = months.indexOfFirst { it.equals(monthName, ignoreCase = true) }
        return if (index != -1) index else null
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
