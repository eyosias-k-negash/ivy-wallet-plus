package com.ivy.legacy.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.ivy.base.model.TransactionType
import com.ivy.legacy.Constants
import java.security.MessageDigest
import java.text.DateFormatSymbols
import java.util.Calendar
import java.util.Locale
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/** The regexes an account uses to turn a bank SMS into a transaction. */
data class SmsParsingRules(
    val masterRegex: String?,
    val useMultiRegex: Boolean,
    val incomeRegex: String? = null,
    val expenseRegex: String? = null,
    val amountRegex: String? = null,
    val dateTimeRegex: String? = null,
    val descriptionRegex: String? = null,
    val balanceRegex: String? = null,
)

data class SmsRegexMatch(
    val value: String,
    val range: IntRange,
    /** Capturing groups 1..n. A group that didn't take part in the match is null. */
    val groups: List<String?>,
)

data class SmsRegexStep(
    /** The pattern that was run, or null when the field is empty. */
    val regex: String?,
    val matches: List<SmsRegexMatch> = emptyList(),
    /** Set when [regex] doesn't compile. */
    val error: String? = null,
) {
    val isSet: Boolean get() = regex != null
    val matched: Boolean get() = matches.isNotEmpty()
    val firstGroup: String? get() = matches.firstOrNull()?.groups?.firstOrNull()
}

enum class SmsAmountSource { AMOUNT_REGEX, MASTER_REGEX }

enum class SmsTypeSource { INCOME_REGEX, EXPENSE_REGEX, KEYWORDS }

data class ParsedSmsTransaction(
    val amount: Double,
    val type: TransactionType,
    val dateTimeMillis: Long,
    val title: String,
    val description: String?,
)

/**
 * Everything [SmsParser.parse] found, step by step, so the SMS tester can show
 * why a message did or didn't become a transaction.
 */
data class SmsParseResult(
    val useMultiRegex: Boolean,
    val master: SmsRegexStep,
    // The steps below only run in multi-regex mode.
    val amountStep: SmsRegexStep? = null,
    val balanceStep: SmsRegexStep? = null,
    val incomeStep: SmsRegexStep? = null,
    val expenseStep: SmsRegexStep? = null,
    val dateTimeStep: SmsRegexStep? = null,
    val descriptionStep: SmsRegexStep? = null,

    val amountText: String?,
    val amountSource: SmsAmountSource,
    val amount: Double?,
    val type: TransactionType,
    val typeSource: SmsTypeSource,
    /** Date read from the SMS text. Null means the receive time is used. */
    val parsedDateTimeMillis: Long?,
    val balance: String?,
    /** Description from the description regex, before the balance is appended. */
    val description: String?,

    /** The transaction the receiver would save, or null when [failure] says why not. */
    val transaction: ParsedSmsTransaction?,
    val failure: String?,
)

object SmsParser {
    const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.DOTALL or Pattern.MULTILINE
    const val FLAGS_LABEL = "CASE_INSENSITIVE, DOTALL, MULTILINE"

    private val incomeKeywords = listOf("credited", "received", "deposited", "added")
    private val expenseKeywords = listOf("debited", "sent", "withdrawn", "spent", "paid", "purchased")

    /** Same check the receiver uses to pick the account an SMS belongs to. */
    fun senderMatches(senderFilter: String?, sender: String, contactName: String?): Boolean {
        if (senderFilter == null) return false
        return sender.contains(senderFilter, ignoreCase = true) ||
            contactName?.contains(senderFilter, ignoreCase = true) == true
    }

    fun parse(
        body: String,
        rules: SmsParsingRules,
        accountName: String,
        receivedAtMillis: Long,
    ): SmsParseResult {
        val masterRegex = rules.masterRegex ?: Constants.DEFAULT_SMS_REGEX
        val master = runStep(masterRegex, body, allMatches = false)
        val masterAmountText = master.firstGroup

        if (!rules.useMultiRegex) {
            val amount = masterAmountText.toAmount()
            val type = detectTypeByKeywords(body)
            return SmsParseResult(
                useMultiRegex = false,
                master = master,
                amountText = masterAmountText,
                amountSource = SmsAmountSource.MASTER_REGEX,
                amount = amount,
                type = type,
                typeSource = SmsTypeSource.KEYWORDS,
                parsedDateTimeMillis = null,
                balance = null,
                description = null,
                transaction = null,
                failure = null,
            ).withOutcome(body, accountName, receivedAtMillis)
        }

        // A blank field means "not set". Running an empty pattern would match every SMS,
        // which turned every message into income when the income field was left empty.
        val amountStep = runStep(rules.amountRegex.orNullIfBlank(), body, allMatches = false)
        val balanceStep = runStep(rules.balanceRegex.orNullIfBlank(), body, allMatches = false)
        val incomeStep = runStep(rules.incomeRegex.orNullIfBlank(), body, allMatches = false)
        val expenseStep = runStep(rules.expenseRegex.orNullIfBlank(), body, allMatches = false)
        val dateTimeStep = runStep(rules.dateTimeRegex.orNullIfBlank(), body, allMatches = false)
        val descriptionStep = runStep(rules.descriptionRegex.orNullIfBlank(), body, allMatches = true)

        val regexAmount = amountStep.firstGroup.toAmount()
        val (amountText, amountSource, amount) = if (regexAmount != null) {
            Triple(amountStep.firstGroup, SmsAmountSource.AMOUNT_REGEX, regexAmount)
        } else {
            Triple(masterAmountText, SmsAmountSource.MASTER_REGEX, masterAmountText.toAmount())
        }

        val (type, typeSource) = when {
            incomeStep.matched -> TransactionType.INCOME to SmsTypeSource.INCOME_REGEX
            expenseStep.matched -> TransactionType.EXPENSE to SmsTypeSource.EXPENSE_REGEX
            else -> detectTypeByKeywords(body) to SmsTypeSource.KEYWORDS
        }

        // Description joins every group of every match.
        val descriptionGroups = descriptionStep.matches.flatMap { it.groups.filterNotNull() }

        return SmsParseResult(
            useMultiRegex = true,
            master = master,
            amountStep = amountStep,
            balanceStep = balanceStep,
            incomeStep = incomeStep,
            expenseStep = expenseStep,
            dateTimeStep = dateTimeStep,
            descriptionStep = descriptionStep,
            amountText = amountText,
            amountSource = amountSource,
            amount = amount,
            type = type,
            typeSource = typeSource,
            parsedDateTimeMillis = dateTimeStep.matches.firstOrNull()?.let {
                parseDateTimeGroups(it.groups, receivedAtMillis)
            },
            balance = balanceStep.firstGroup,
            description = descriptionGroups.takeIf { it.isNotEmpty() }?.joinToString("\n"),
            transaction = null,
            failure = null,
        ).withOutcome(body, accountName, receivedAtMillis)
    }

    private fun SmsParseResult.withOutcome(
        body: String,
        accountName: String,
        receivedAtMillis: Long,
    ): SmsParseResult {
        val invalidStep = listOfNotNull(
            "Master" to master,
            amountStep?.let { "Amount" to it },
            balanceStep?.let { "Balance" to it },
            incomeStep?.let { "Income" to it },
            expenseStep?.let { "Expense" to it },
            dateTimeStep?.let { "Date/time" to it },
            descriptionStep?.let { "Description" to it },
        ).firstOrNull { (_, step) -> step.error != null }

        val failure = when {
            invalidStep != null -> "${invalidStep.first} regex is invalid"
            !master.matched -> "Master regex didn't match"
            amount == null -> "No amount found"
            else -> null
        }
        if (failure != null) return copy(failure = failure)

        val finalDescription = if (balance != null) {
            if (!description.isNullOrBlank()) "$description\n(Bal: $balance)" else "(Bal: $balance)"
        } else {
            description
        }
        return copy(
            transaction = ParsedSmsTransaction(
                amount = amount!!,
                type = type,
                dateTimeMillis = parsedDateTimeMillis ?: receivedAtMillis,
                title = title(type, accountName, body),
                description = finalDescription,
            )
        )
    }

    private fun runStep(regex: String?, body: String, allMatches: Boolean): SmsRegexStep {
        if (regex == null) return SmsRegexStep(regex = null)
        val pattern = try {
            Pattern.compile(regex, FLAGS)
        } catch (e: PatternSyntaxException) {
            return SmsRegexStep(regex = regex, error = e.description ?: e.message)
        }

        val matcher = pattern.matcher(body)
        val matches = mutableListOf<SmsRegexMatch>()
        while (matcher.find()) {
            matches += SmsRegexMatch(
                value = matcher.group(),
                range = matcher.start() until matcher.end(),
                groups = (1..matcher.groupCount()).map { matcher.group(it) },
            )
            if (!allMatches) break
        }
        return SmsRegexStep(regex = regex, matches = matches)
    }

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.toAmount(): Double? = this?.replace(",", "")?.toDoubleOrNull()

    /** Groups are (day)(month)(year), optionally followed by (hour)(minute)(second). */
    fun parseDateTimeGroups(groups: List<String?>, receivedAtMillis: Long): Long? {
        if (groups.size < 3) return null

        return try {
            val calendar = Calendar.getInstance().apply { timeInMillis = receivedAtMillis }
            val day = groups[0]?.toIntOrNull() ?: return null
            val monthStr = groups[1] ?: return null
            val year = groups[2]?.let {
                if (it.length == 2) 2000 + it.toInt() else it.toIntOrNull()
            } ?: calendar.get(Calendar.YEAR)

            val month = monthStr.toIntOrNull()?.minus(1) ?: parseMonthName(monthStr) ?: return null

            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, day)

            if (groups.size >= 6) {
                calendar.set(Calendar.HOUR_OF_DAY, groups[3]?.toIntOrNull() ?: 0)
                calendar.set(Calendar.MINUTE, groups[4]?.toIntOrNull() ?: 0)
                calendar.set(Calendar.SECOND, groups[5]?.toIntOrNull() ?: 0)
            } else {
                calendar.set(Calendar.HOUR_OF_DAY, 0)
                calendar.set(Calendar.MINUTE, 0)
                calendar.set(Calendar.SECOND, 0)
            }
            calendar.set(Calendar.MILLISECOND, 0)

            calendar.timeInMillis
        } catch (_: Exception) {
            null
        }
    }

    private fun parseMonthName(monthName: String): Int? {
        val months = DateFormatSymbols.getInstance(Locale.ENGLISH).shortMonths
        val index = months.indexOfFirst { it.equals(monthName, ignoreCase = true) }
        return if (index != -1) index else null
    }

    fun detectTypeByKeywords(body: String): TransactionType = when {
        incomeKeywords.any { body.contains(it, ignoreCase = true) } -> TransactionType.INCOME
        expenseKeywords.any { body.contains(it, ignoreCase = true) } -> TransactionType.EXPENSE
        else -> TransactionType.EXPENSE // Default to expense if unknown
    }

    private fun title(type: TransactionType, accountName: String, body: String): String {
        val typePrefix = if (type == TransactionType.INCOME) "IN" else "OUT"
        return "AUTO $typePrefix $accountName ${generateHash(body)}"
    }

    private fun generateHash(text: String): String {
        return MessageDigest.getInstance("MD5")
            .digest(text.toByteArray())
            .joinToString("") { "%02x".format(it) }
            .take(6)
            .uppercase()
    }
}

/** Looks up the contact name saved for [phoneNumber]. Needs READ_CONTACTS; returns null without it. */
fun lookupContactName(context: Context, phoneNumber: String): String? {
    return try {
        val uri = Uri.withAppendedPath(
            ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
            Uri.encode(phoneNumber)
        )
        val projection = arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME)
        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else {
                null
            }
        }
    } catch (_: Exception) {
        null
    }
}
