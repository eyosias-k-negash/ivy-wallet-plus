package com.ivy.legacy.sms

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import com.ivy.base.model.TransactionType
import com.ivy.legacy.Constants
import java.math.BigDecimal
import java.security.MessageDigest
import java.time.DateTimeException
import java.time.Instant
import java.time.LocalDateTime
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
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
    val feeRegex: String? = null,
    // The account's Capture menu. A field that's off isn't used, even when a pattern finds it.
    val captureDate: Boolean = true,
    val captureDescription: Boolean = true,
    val captureBalance: Boolean = true,
    val captureFee: Boolean = false,
)

/**
 * Names the parser reads from named groups, like `(?<amount>[\d,.]+)`.
 *
 * Java doesn't allow the same name twice in one pattern, so a name may end in digits:
 * `(?<day>\d\d)/(?<month>\d\d)|(?<month2>\d\d)-(?<day2>\d\d)` reads either layout.
 */
enum class SmsGroup(val key: String, val fieldLabel: String?) {
    AMOUNT("amount", "amount"),
    BALANCE("balance", "balance"),
    DESCRIPTION("description", "description"),
    FEE("fee", "fee"),
    INCOME("income", null),
    EXPENSE("expense", null),
    DAY("day", "date/time"),
    MONTH("month", "date/time"),
    YEAR("year", "date/time"),
    HOUR("hour", "date/time"),
    MINUTE("minute", "date/time"),
    SECOND("second", "date/time"),
    AMPM("ampm", "date/time");

    companion object {
        val DATE_TIME = setOf(DAY, MONTH, YEAR, HOUR, MINUTE, SECOND, AMPM)

        /** Date/time groups in a pattern without named groups, in group order. */
        val POSITIONAL_DATE_TIME = listOf(DAY, MONTH, YEAR, HOUR, MINUTE, SECOND)

        /** The group a name stands for, ignoring case and trailing digits, or null if the parser doesn't read it. */
        fun of(name: String): SmsGroup? {
            val key = name.trimEnd { it.isDigit() }.lowercase()
            return entries.firstOrNull { it.key == key }
        }
    }
}

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
    /**
     * Name of each capturing group, null for an unnamed one. Empty when the pattern has no named
     * groups: its values are then read by group position, as before named groups were supported.
     */
    val groupNames: List<String?> = emptyList(),
    /** Named groups that are ignored, e.g. a misspelled name. */
    val warnings: List<String> = emptyList(),
) {
    val isSet: Boolean get() = regex != null
    val matched: Boolean get() = matches.isNotEmpty()
    val firstGroup: String? get() = matches.firstOrNull()?.groups?.firstOrNull()
    val usesNames: Boolean get() = groupNames.isNotEmpty()
}

/** Values of the groups named after [group] that took part in [match], in pattern order. */
fun SmsRegexStep.named(group: SmsGroup, match: SmsRegexMatch? = matches.firstOrNull()): List<String> {
    if (match == null) return emptyList()
    return groupNames.indices.mapNotNull { i ->
        match.groups.getOrNull(i)?.takeIf { groupNames[i]?.let(SmsGroup::of) == group }
    }
}

fun SmsRegexStep.hasGroupFor(groups: Set<SmsGroup>): Boolean =
    groupNames.any { name -> name?.let(SmsGroup::of) in groups }

/** Which pattern a value was read from. */
enum class SmsValueSource { FIELD_REGEX, MASTER_REGEX }

enum class SmsTypeSource { INCOME_REGEX, EXPENSE_REGEX, MASTER_REGEX, KEYWORDS }

data class ParsedSmsTransaction(
    val amount: Double,
    val type: TransactionType,
    val dateTimeMillis: Long,
    val title: String,
    val description: String?,
    /** The account balance the SMS reports after this transaction, if a pattern found one. */
    val balance: Double?,
    /** A fee a pattern found, saved as its own expense. Null when there's none or fee capture is off. */
    val fee: Double? = null,
)

/**
 * Everything [SmsParser.parse] found, step by step, so the SMS tester can show
 * why a message did or didn't become a transaction.
 */
data class SmsParseResult(
    val rules: SmsParsingRules,
    val useMultiRegex: Boolean,
    val master: SmsRegexStep,
    // The steps below only run in multi-regex mode.
    val amountStep: SmsRegexStep? = null,
    val balanceStep: SmsRegexStep? = null,
    val incomeStep: SmsRegexStep? = null,
    val expenseStep: SmsRegexStep? = null,
    val dateTimeStep: SmsRegexStep? = null,
    val descriptionStep: SmsRegexStep? = null,
    val feeStep: SmsRegexStep? = null,

    // What the patterns found. The transaction only uses what the Capture menu turns on.
    // A source is null when no pattern gave the value.
    val amountText: String?,
    val amountSource: SmsValueSource?,
    val amount: Double?,
    val type: TransactionType,
    val typeSource: SmsTypeSource,
    /** Date read from the SMS text. */
    val parsedDateTimeMillis: Long?,
    val dateTimeSource: SmsValueSource?,
    /** Why a date pattern that matched didn't give a date. */
    val dateTimeProblem: String?,
    val balance: String?,
    val balanceSource: SmsValueSource?,
    /** Description from the description regex, before the balance is appended. */
    val description: String?,
    val descriptionSource: SmsValueSource?,
    /** Every fee value found, added up. Null when there's none or one isn't a number. */
    val fee: Double? = null,
    val feeSource: SmsValueSource? = null,

    /** The transaction the receiver would save, or null when [failure] says why not. */
    val transaction: ParsedSmsTransaction?,
    val failure: String?,
)

object SmsParser {
    const val FLAGS = Pattern.CASE_INSENSITIVE or Pattern.DOTALL or Pattern.MULTILINE
    const val FLAGS_LABEL = "CASE_INSENSITIVE, DOTALL, MULTILINE"

    private const val NAMED_GROUP_START = "(?<"
    private const val TWO_DIGIT_YEAR_BASE = 2000
    private const val FULL_YEAR_DIGITS = 4
    private const val HOURS_PER_HALF_DAY = 12
    private const val MIN_MONTH_NAME_LENGTH = 3
    private val MONTHS = 1..12

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
        val multi = rules.useMultiRegex
        val master = runStep(
            regex = rules.masterRegex ?: Constants.DEFAULT_SMS_REGEX,
            body = body,
            allMatches = false,
            reads = SmsGroup.entries.toSet(),
        )

        // A blank field means "not set". Running an empty pattern would match every SMS,
        // which turned every message into income when the income field was left empty.
        fun fieldStep(regex: String?, reads: Set<SmsGroup>, allMatches: Boolean = false): SmsRegexStep? =
            if (multi) runStep(regex.orNullIfBlank(), body, allMatches, reads) else null

        val amountStep = fieldStep(rules.amountRegex, setOf(SmsGroup.AMOUNT))
        val balanceStep = fieldStep(rules.balanceRegex, setOf(SmsGroup.BALANCE))
        val incomeStep = fieldStep(rules.incomeRegex, emptySet())
        val expenseStep = fieldStep(rules.expenseRegex, emptySet())
        val dateTimeStep = fieldStep(rules.dateTimeRegex, SmsGroup.DATE_TIME)
        val descriptionStep = fieldStep(rules.descriptionRegex, setOf(SmsGroup.DESCRIPTION), allMatches = true)
        val feeStep = fieldStep(rules.feeRegex, setOf(SmsGroup.FEE), allMatches = true)

        // Each value comes from its own pattern, then from the master pattern. Without named
        // groups, master group 1 is the amount and the master gives nothing else.
        val fieldAmountText = amountStep?.namedOrFirst(SmsGroup.AMOUNT)
        val masterAmountText = master.namedOrFirst(SmsGroup.AMOUNT)
        val fieldAmount = fieldAmountText.toAmount()
        val masterAmount = masterAmountText.toAmount()
        val (amountText, amountSource, amount) = when {
            fieldAmount != null -> Triple(fieldAmountText, SmsValueSource.FIELD_REGEX, fieldAmount)
            masterAmount != null -> Triple(masterAmountText, SmsValueSource.MASTER_REGEX, masterAmount)
            else -> Triple(masterAmountText ?: fieldAmountText, null, null)
        }

        val masterType = when {
            master.named(SmsGroup.INCOME).isNotEmpty() -> TransactionType.INCOME
            master.named(SmsGroup.EXPENSE).isNotEmpty() -> TransactionType.EXPENSE
            else -> null
        }
        val (type, typeSource) = when {
            incomeStep?.matched == true -> TransactionType.INCOME to SmsTypeSource.INCOME_REGEX
            expenseStep?.matched == true -> TransactionType.EXPENSE to SmsTypeSource.EXPENSE_REGEX
            masterType != null -> masterType to SmsTypeSource.MASTER_REGEX
            else -> detectTypeByKeywords(body) to SmsTypeSource.KEYWORDS
        }

        val fieldDate = dateTimeStep?.let { readDateTime(it, receivedAtMillis, positional = true) }
        val masterDate = readDateTime(master, receivedAtMillis, positional = false)
        val (dateTimeMillis, dateTimeSource) = pick(fieldDate?.millis, masterDate?.millis)
        val dateTimeProblem = fieldDate?.problem ?: masterDate?.problem?.takeIf { dateTimeMillis == null }

        // Description joins every group of every match, or only the description groups if there are names.
        val fieldDescription = descriptionStep?.let { step ->
            step.matches.flatMap { match ->
                if (step.usesNames) step.named(SmsGroup.DESCRIPTION, match) else match.groups.filterNotNull()
            }
        }
        val (descriptionLines, descriptionSource) = pick(
            fieldDescription?.takeIf { it.isNotEmpty() },
            master.named(SmsGroup.DESCRIPTION).takeIf { it.isNotEmpty() },
        )

        val (balance, balanceSource) = pick(
            balanceStep?.namedOrFirst(SmsGroup.BALANCE),
            master.named(SmsGroup.BALANCE).firstOrNull(),
        )

        // Fee adds up every value, so a service charge and its VAT become one fee: every group of
        // every match, or only the fee groups if there are names.
        val fieldFee = feeStep?.let { step ->
            step.matches.flatMap { match ->
                if (step.usesNames) step.named(SmsGroup.FEE, match) else match.groups.filterNotNull()
            }
        }
        val (feeParts, feeSource) = pick(
            fieldFee?.takeIf { it.isNotEmpty() },
            master.named(SmsGroup.FEE).takeIf { it.isNotEmpty() },
        )

        return SmsParseResult(
            rules = rules,
            useMultiRegex = multi,
            master = master,
            amountStep = amountStep,
            balanceStep = balanceStep,
            incomeStep = incomeStep,
            expenseStep = expenseStep,
            dateTimeStep = dateTimeStep,
            descriptionStep = descriptionStep,
            feeStep = feeStep,
            amountText = amountText,
            amountSource = amountSource,
            amount = amount,
            type = type,
            typeSource = typeSource,
            parsedDateTimeMillis = dateTimeMillis,
            dateTimeSource = dateTimeSource,
            dateTimeProblem = dateTimeProblem,
            balance = balance,
            balanceSource = balanceSource,
            description = descriptionLines?.joinToString("\n"),
            descriptionSource = descriptionSource,
            fee = feeParts?.sumAmounts(),
            feeSource = feeSource,
            transaction = null,
            failure = null,
        ).withOutcome(body, accountName, receivedAtMillis)
    }

    private fun <T : Any> pick(fromField: T?, fromMaster: T?): Pair<T?, SmsValueSource?> = when {
        fromField != null -> fromField to SmsValueSource.FIELD_REGEX
        fromMaster != null -> fromMaster to SmsValueSource.MASTER_REGEX
        else -> null to null
    }

    private fun SmsParseResult.withOutcome(
        body: String,
        accountName: String,
        receivedAtMillis: Long,
    ): SmsParseResult {
        // A broken pattern for a field the Capture menu turned off doesn't stop the transaction.
        val invalidStep = listOfNotNull(
            "Master" to master,
            amountStep?.let { "Amount" to it },
            balanceStep?.takeIf { rules.captureBalance }?.let { "Balance" to it },
            incomeStep?.let { "Income" to it },
            expenseStep?.let { "Expense" to it },
            dateTimeStep?.takeIf { rules.captureDate }?.let { "Date/time" to it },
            descriptionStep?.takeIf { rules.captureDescription }?.let { "Description" to it },
            feeStep?.takeIf { rules.captureFee }?.let { "Fee" to it },
        ).firstOrNull { (_, step) -> step.error != null }

        val failure = when {
            invalidStep != null -> "${invalidStep.first} regex is invalid"
            !master.matched -> "Master regex didn't match"
            amount == null -> "No amount found"
            else -> null
        }
        if (failure != null) return copy(failure = failure)

        val capturedDescription = description.takeIf { rules.captureDescription }
        val capturedBalance = balance.takeIf { rules.captureBalance }
        val finalDescription = if (capturedBalance != null) {
            if (!capturedDescription.isNullOrBlank()) {
                "$capturedDescription\n(Bal: $capturedBalance)"
            } else {
                "(Bal: $capturedBalance)"
            }
        } else {
            capturedDescription
        }
        return copy(
            transaction = ParsedSmsTransaction(
                amount = amount!!,
                type = type,
                dateTimeMillis = parsedDateTimeMillis?.takeIf { rules.captureDate } ?: receivedAtMillis,
                title = title(type, accountName, body),
                description = finalDescription,
                balance = capturedBalance.toAmount(),
                fee = fee?.takeIf { rules.captureFee && it > 0.0 },
            )
        )
    }

    private fun runStep(regex: String?, body: String, allMatches: Boolean, reads: Set<SmsGroup>): SmsRegexStep {
        if (regex == null) return SmsRegexStep(regex = null)
        val pattern = try {
            Pattern.compile(regex, FLAGS)
        } catch (e: PatternSyntaxException) {
            return SmsRegexStep(regex = regex, error = e.description ?: e.message)
        }

        val matcher = pattern.matcher(body)
        val scannedNames = scanGroupNames(regex)
        val namesReadable = scannedNames.size == matcher.groupCount()
        val groupNames = if (namesReadable && scannedNames.any { it != null }) scannedNames else emptyList()
        val warnings = when {
            !namesReadable && scannedNames.any { it != null } ->
                listOf("Couldn't read the group names in this pattern, so groups are read by position.")
            else -> nameWarnings(groupNames, reads)
        }

        val matches = mutableListOf<SmsRegexMatch>()
        while (matcher.find()) {
            matches += SmsRegexMatch(
                value = matcher.group(),
                range = matcher.start() until matcher.end(),
                groups = (1..matcher.groupCount()).map { matcher.group(it) },
            )
            if (!allMatches) break
        }
        return SmsRegexStep(regex = regex, matches = matches, groupNames = groupNames, warnings = warnings)
    }

    private fun nameWarnings(groupNames: List<String?>, reads: Set<SmsGroup>): List<String> =
        groupNames.filterNotNull().distinct().mapNotNull { name ->
            val group = SmsGroup.of(name)
            when {
                group == null -> "<$name> isn't a name the parser reads, so it's ignored."
                group in reads -> null
                group.fieldLabel == null -> "<$name> is ignored here. Only the master pattern reads it."
                else -> "<$name> is ignored here. It's read from the ${group.fieldLabel} or master pattern."
            }
        }

    /**
     * Name of each capturing group in [regex], in group order, null for an unnamed group.
     * Android's regex API can't list group names, so they're read from the pattern text.
     * The caller checks the count against the compiled pattern's group count.
     */
    internal fun scanGroupNames(regex: String): List<String?> {
        val names = mutableListOf<String?>()
        var classDepth = 0
        var i = 0
        while (i < regex.length) {
            val c = regex[i]
            when {
                c == '\\' && regex.getOrNull(i + 1) == 'Q' -> {
                    // \Q...\E quotes everything in between
                    val end = regex.indexOf("\\E", startIndex = i + 2)
                    i = if (end == -1) regex.length else end + 1
                }
                c == '\\' -> i++
                classDepth > 0 -> when (c) {
                    '[' -> classDepth++
                    ']' -> classDepth--
                }
                c == '[' -> {
                    classDepth = 1
                    // A ] right after [ or [^ is a literal
                    if (regex.getOrNull(i + 1) == '^') i++
                    if (regex.getOrNull(i + 1) == ']') i++
                }
                c == '(' && regex.getOrNull(i + 1) != '?' -> names += null
                regex.startsWith(NAMED_GROUP_START, i) &&
                    regex.getOrNull(i + NAMED_GROUP_START.length).isAsciiLetter() -> {
                    val nameStart = i + NAMED_GROUP_START.length
                    val end = regex.indexOf('>', startIndex = nameStart)
                    if (end != -1) names += regex.substring(nameStart, end)
                }
            }
            i++
        }
        return names
    }

    private fun Char?.isAsciiLetter(): Boolean = this != null && (this in 'a'..'z' || this in 'A'..'Z')

    private fun String?.orNullIfBlank(): String? = this?.takeIf { it.isNotBlank() }

    private fun String?.toAmount(): Double? = this?.replace(",", "")?.toDoubleOrNull()

    /** The values added up, or null when one of them isn't a number. */
    private fun List<String>.sumAmounts(): Double? {
        val parts = map { it.replace(",", "").toBigDecimalOrNull() }
        return parts.takeIf { null !in it }?.filterNotNull()?.fold(BigDecimal.ZERO, BigDecimal::add)?.toDouble()
    }

    /** Like [SmsRegexStep.named], but a pattern without named groups gives its group 1. */
    private fun SmsRegexStep.namedOrFirst(group: SmsGroup): String? =
        if (usesNames) named(group).firstOrNull() else firstGroup

    private class DateTimeRead(val millis: Long? = null, val problem: String? = null)

    /**
     * Reads the date from the step's first match, or returns null when there's nothing to read.
     * With [positional], a pattern without named groups is read as (day)(month)(year)(hour)(minute)(second).
     */
    private fun readDateTime(step: SmsRegexStep, receivedAtMillis: Long, positional: Boolean): DateTimeRead? {
        val match = step.matches.firstOrNull()
        val named = step.hasGroupFor(SmsGroup.DATE_TIME) || (step.usesNames && positional)
        val values = when {
            match == null || (!named && !positional) -> null
            named -> SmsGroup.DATE_TIME.associateWith { step.named(it, match).firstOrNull() }
            else -> SmsGroup.POSITIONAL_DATE_TIME.zip(match.groups).toMap()
        } ?: return null
        return try {
            DateTimeRead(millis = buildDateTime(values, receivedAtMillis))
        } catch (e: DateTimeException) {
            DateTimeRead(problem = e.message)
        }
    }

    /**
     * Builds a date from day, month and year, optionally with hour, minute, second and AM/PM.
     * A missing year is the year the SMS arrived, and a missing time is midnight.
     *
     * @throws DateTimeException saying what's wrong when the values don't make a date
     */
    fun buildDateTime(
        values: Map<SmsGroup, String?>,
        receivedAtMillis: Long,
        zone: ZoneId = ZoneId.systemDefault(),
    ): Long {
        fun text(group: SmsGroup) = values[group]?.trim()

        val dayText = text(SmsGroup.DAY)
        val monthText = text(SmsGroup.MONTH)
        if (dayText == null || monthText == null) {
            throw DateTimeException("Needs a day and a month, but no day or month group took part in the match.")
        }
        val day = number(dayText, "Day")
        val month = readMonth(monthText, day)
        val year = text(SmsGroup.YEAR)?.let { readYear(it) } ?: Instant.ofEpochMilli(receivedAtMillis).atZone(zone).year
        if (!YearMonth.of(year, month).isValidDay(day)) {
            val monthName = Month.of(month).getDisplayName(TextStyle.FULL, Locale.ENGLISH)
            throw DateTimeException("Day $day doesn't exist in $monthName $year.")
        }

        return LocalDateTime.of(
            year,
            month,
            day,
            to24Hour(text(SmsGroup.HOUR)?.let { number(it, "Hour") } ?: 0, text(SmsGroup.AMPM)),
            text(SmsGroup.MINUTE)?.let { number(it, "Minute") } ?: 0,
            text(SmsGroup.SECOND)?.let { number(it, "Second") } ?: 0,
        ).atZone(zone).toInstant().toEpochMilli()
    }

    private fun number(text: String, label: String): Int =
        text.toIntOrNull() ?: throw DateTimeException("$label \"$text\" isn't a number.")

    private fun readMonth(text: String, day: Int): Int {
        val month = text.toIntOrNull() ?: parseMonthName(text)
            ?: throw DateTimeException("Month \"$text\" isn't a number or an English month name.")
        if (month !in MONTHS) {
            val swapped = if (day in MONTHS) " Are the day and month the wrong way round?" else ""
            throw DateTimeException("Month $month doesn't exist.$swapped")
        }
        return month
    }

    private fun readYear(text: String): Int {
        val year = number(text, "Year")
        return when (text.length) {
            2 -> TWO_DIGIT_YEAR_BASE + year
            FULL_YEAR_DIGITS -> year
            else -> throw DateTimeException("Year \"$text\" should have 2 or 4 digits.")
        }
    }

    /** Turns a 12-hour clock [hour] into 0 to 23 when there's an AM/PM marker. */
    private fun to24Hour(hour: Int, ampm: String?): Int {
        val marker = ampm?.lowercase()?.firstOrNull() ?: return hour
        if (hour !in 1..HOURS_PER_HALF_DAY) {
            throw DateTimeException("Hour $hour isn't 1 to 12, as an AM/PM time needs.")
        }
        return when (marker) {
            'a' -> hour % HOURS_PER_HALF_DAY
            'p' -> hour % HOURS_PER_HALF_DAY + HOURS_PER_HALF_DAY
            else -> throw DateTimeException("\"$ampm\" isn't AM or PM.")
        }
    }

    /** "Jan", "Sept" or "January". */
    private fun parseMonthName(monthName: String): Int? {
        val name = monthName.trimEnd('.').lowercase()
        if (name.length < MIN_MONTH_NAME_LENGTH) return null
        return Month.entries.firstOrNull { it.name.lowercase().startsWith(name) }?.value
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

    /** Title of the fee expense saved next to the SMS's transaction, sharing its code. */
    fun feeTitle(accountName: String, body: String): String = "AUTO FEE $accountName ${generateHash(body)}"

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
