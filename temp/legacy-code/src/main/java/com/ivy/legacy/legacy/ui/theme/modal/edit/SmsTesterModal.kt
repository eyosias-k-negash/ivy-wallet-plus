package com.ivy.wallet.ui.theme.modal.edit

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.sms.SmsAmountSource
import com.ivy.legacy.sms.SmsParseResult
import com.ivy.legacy.sms.SmsParser
import com.ivy.legacy.sms.SmsParsingRules
import com.ivy.legacy.sms.SmsRegexStep
import com.ivy.legacy.sms.SmsTypeSource
import com.ivy.legacy.sms.lookupContactName
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.components.IvyOutlinedTextField
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalPositiveButton
import com.ivy.wallet.ui.theme.modal.ModalTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

private const val MAX_INBOX_SMS = 1000
private const val MAX_HIDDEN_CHAR_KINDS = 8

private val listDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.getDefault())
private val fullDateFormatter = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm:ss", Locale.getDefault())

@Immutable
private data class InboxSms(
    val id: Long,
    val address: String,
    val contactName: String?,
    val body: String,
    val dateMillis: Long,
    val subscriptionId: Int?,
)

/**
 * Lets the user pick any SMS from the inbox and shows, step by step, how the
 * SMS receiver would parse it with the regexes currently in the account form.
 */
@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxScope.SmsTesterModal(
    visible: Boolean,
    /** The picked SMS is kept while this stays the same, so you can edit a regex and re-test. */
    sessionKey: Any?,
    rules: SmsParsingRules,
    senderFilter: String,
    subscriptionId: Int?,
    accountName: String,
    dismiss: () -> Unit,
) {
    val context = LocalContext.current
    val modalId = remember { UUID.randomUUID() }

    var inbox by remember { mutableStateOf<List<InboxSms>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selected by remember(sessionKey) { mutableStateOf<InboxSms?>(null) }
    var query by remember(sessionKey) { mutableStateOf(TextFieldValue("")) }
    var onlyFromSender by remember(sessionKey) { mutableStateOf(senderFilter.isNotBlank()) }
    val listState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        try {
            inbox = withContext(Dispatchers.IO) { loadInboxSms(context) }
            loadError = null
        } catch (e: SecurityException) {
            loadError = "Can't read SMS: ${e.message}"
        }
    }

    IvyModal(
        id = modalId,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = {
            if (selected != null) {
                ModalPositiveButton(
                    text = "Other SMS",
                    iconStart = R.drawable.ic_back
                ) {
                    selected = null
                }
            }
        },
        scrollState = null,
        includeActionsRowPadding = false,
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(text = "Test SMS parsing")

        Spacer(Modifier.height(8.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = "Runs the same parser as the SMS receiver, using the values in the account form " +
                "(unsaved edits included).",
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )

        Spacer(Modifier.height(16.dp))

        val sms = selected
        if (sms == null) {
            SmsPicker(
                inbox = inbox,
                loadError = loadError,
                query = query,
                onQueryChange = { query = it },
                senderFilter = senderFilter,
                onlyFromSender = onlyFromSender,
                onOnlyFromSenderChange = { onlyFromSender = it },
                listState = listState,
                onSelect = { selected = it }
            )
        } else {
            SmsBreakdown(
                sms = sms,
                rules = rules,
                senderFilter = senderFilter,
                subscriptionId = subscriptionId,
                accountName = accountName,
            )
        }
    }
}

private fun loadInboxSms(context: Context): List<InboxSms> {
    val projection = arrayOf(
        Telephony.Sms._ID,
        Telephony.Sms.ADDRESS,
        Telephony.Sms.BODY,
        Telephony.Sms.DATE,
        Telephony.Sms.SUBSCRIPTION_ID,
    )
    val messages = mutableListOf<InboxSms>()
    context.contentResolver.query(
        Telephony.Sms.Inbox.CONTENT_URI,
        projection,
        null,
        null,
        Telephony.Sms.Inbox.DEFAULT_SORT_ORDER
    )?.use { cursor ->
        val idIndex = cursor.getColumnIndexOrThrow(Telephony.Sms._ID)
        val addressIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.ADDRESS)
        val bodyIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.BODY)
        val dateIndex = cursor.getColumnIndexOrThrow(Telephony.Sms.DATE)
        val subIndex = cursor.getColumnIndex(Telephony.Sms.SUBSCRIPTION_ID)
        while (messages.size < MAX_INBOX_SMS && cursor.moveToNext()) {
            val address = cursor.getString(addressIndex) ?: continue
            val body = cursor.getString(bodyIndex) ?: continue
            messages += InboxSms(
                id = cursor.getLong(idIndex),
                address = address,
                contactName = null,
                body = body,
                dateMillis = cursor.getLong(dateIndex),
                subscriptionId = if (subIndex >= 0 && !cursor.isNull(subIndex)) cursor.getInt(subIndex) else null,
            )
        }
    }

    if (!context.canReadContacts()) return messages
    val contactNames = messages.map { it.address }.distinct().associateWith { lookupContactName(context, it) }
    return messages.map { it.copy(contactName = contactNames[it.address]) }
}

private fun Context.canReadContacts(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) ==
        PackageManager.PERMISSION_GRANTED

@Composable
private fun ColumnScope.SmsPicker(
    inbox: List<InboxSms>?,
    loadError: String?,
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    senderFilter: String,
    onlyFromSender: Boolean,
    onOnlyFromSenderChange: (Boolean) -> Unit,
    listState: LazyListState,
    onSelect: (InboxSms) -> Unit,
) {
    IvyOutlinedTextField(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth(),
        value = query,
        hint = "Search sender or text",
        onValueChanged = onQueryChange
    )

    if (senderFilter.isNotBlank()) {
        Spacer(Modifier.height(12.dp))
        IvyCheckboxWithText(
            modifier = Modifier.padding(start = 16.dp),
            text = "Only senders matching \"$senderFilter\"",
            checked = onlyFromSender,
            onCheckedChange = onOnlyFromSenderChange
        )
    }

    Spacer(Modifier.height(8.dp))

    val q = query.text.trim()
    val filtered = remember(inbox, q, onlyFromSender, senderFilter) {
        inbox.orEmpty().filter { sms ->
            val senderOk = !onlyFromSender || senderFilter.isBlank() ||
                SmsParser.senderMatches(senderFilter, sms.address, sms.contactName)
            val queryOk = q.isEmpty() ||
                sms.address.contains(q, ignoreCase = true) ||
                sms.contactName?.contains(q, ignoreCase = true) == true ||
                sms.body.contains(q, ignoreCase = true)
            senderOk && queryOk
        }
    }

    val emptyText = when {
        loadError != null -> loadError
        inbox == null -> "Loading messages…"
        inbox.isEmpty() -> "Your SMS inbox is empty."
        filtered.isEmpty() && onlyFromSender ->
            "No SMS from a sender matching \"$senderFilter\". Untick the box to see every SMS."
        filtered.isEmpty() -> "No SMS matches your search."
        else -> null
    }
    if (emptyText != null) {
        Text(
            modifier = Modifier.padding(horizontal = 32.dp, vertical = 24.dp),
            text = emptyText,
            style = UI.typo.b2.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.weight(1f))
        return
    }

    LazyColumn(
        modifier = Modifier.weight(1f),
        state = listState
    ) {
        items(items = filtered, key = { it.id }) { sms ->
            SmsRow(
                sms = sms,
                senderMatches = senderFilter.isNotBlank() &&
                    SmsParser.senderMatches(senderFilter, sms.address, sms.contactName),
                onClick = { onSelect(sms) }
            )
        }
        item {
            Spacer(Modifier.height(120.dp))
        }
    }
}

@Composable
private fun SmsRow(
    sms: InboxSms,
    senderMatches: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                modifier = Modifier.weight(1f),
                text = sms.contactName?.let { "$it (${sms.address})" } ?: sms.address,
                style = UI.typo.b2.style(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (senderMatches) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "SENDER MATCH",
                    style = UI.typo.c.style(color = UI.colors.green, fontWeight = FontWeight.ExtraBold)
                )
            }
        }
        Text(
            text = sms.dateMillis.formatWith(listDateFormatter),
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = sms.body,
            style = UI.typo.c.style(fontWeight = FontWeight.Medium),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun ColumnScope.SmsBreakdown(
    sms: InboxSms,
    rules: SmsParsingRules,
    senderFilter: String,
    subscriptionId: Int?,
    accountName: String,
) {
    val result = remember(sms, rules, accountName) {
        SmsParser.parse(
            body = sms.body,
            rules = rules,
            accountName = accountName,
            receivedAtMillis = sms.dateMillis
        )
    }
    val senderOk = SmsParser.senderMatches(senderFilter, sms.address, sms.contactName)
    val simOk = subscriptionId == null || sms.subscriptionId == null || sms.subscriptionId == subscriptionId

    LazyColumn(modifier = Modifier.weight(1f)) {
        item { SmsCard(sms) }
        item { PreChecksCard(sms, senderFilter, senderOk, subscriptionId, simOk) }
        item { ModeCard(result) }
        item {
            StepCard(
                title = if (result.useMultiRegex) "Master match" else "SMS parsing regex",
                step = result.master,
                emptyPatternNote = "Empty pattern: matches every SMS.",
                needsGroups = !result.useMultiRegex,
                groupLabels = if (result.useMultiRegex) emptyList() else listOf("amount"),
                outcome = when {
                    result.master.error != null -> "Invalid regex. No transaction." to UI.colors.red
                    result.master.matched -> "Matched. Parsing continues." to UI.colors.green
                    else -> "No match. The receiver ignores this SMS." to UI.colors.red
                }
            )
        }
        if (result.useMultiRegex) {
            multiRegexSteps(result)
        } else {
            item { SingleRegexSummary(result) }
        }
        item { ResultCard(result, senderOk, simOk) }
        item { Spacer(Modifier.height(120.dp)) }
    }
}

private fun LazyListScope.multiRegexSteps(result: SmsParseResult) {
    item {
        StepCard(
            title = "Amount",
            step = result.amountStep,
            needsGroups = true,
            groupLabels = listOf("amount"),
            outcome = amountOutcome(result)
        )
    }
    item {
        StepCard(
            title = "Income identification",
            step = result.incomeStep,
            outcome = when {
                result.incomeStep?.matched == true -> "Matched: type is INCOME." to UI.colors.green
                else -> "No match: not identified as income." to UI.colors.gray
            }
        )
    }
    item {
        StepCard(
            title = "Expense identification",
            step = result.expenseStep,
            outcome = when {
                result.typeSource == SmsTypeSource.INCOME_REGEX && result.expenseStep?.matched == true ->
                    "Matched, but the income regex wins." to UI.colors.orange
                result.expenseStep?.matched == true -> "Matched: type is EXPENSE." to UI.colors.green
                else -> "No match: not identified as expense." to UI.colors.gray
            }
        )
    }
    item { TypeSummary(result) }
    item {
        StepCard(
            title = "Date/time",
            step = result.dateTimeStep,
            needsGroups = true,
            groupLabels = listOf("day", "month", "year", "hour", "minute", "second"),
            outcome = dateOutcome(result)
        )
    }
    item {
        StepCard(
            title = "Description",
            step = result.descriptionStep,
            needsGroups = true,
            showAllMatches = true,
            outcome = descriptionOutcome(result)
        )
    }
    item {
        StepCard(
            title = "Balance",
            step = result.balanceStep,
            needsGroups = true,
            groupLabels = listOf("balance"),
            outcome = when {
                result.balance != null -> "Appended to the description as \"(Bal: ${result.balance})\"." to UI.colors.green
                result.balanceStep?.isSet != true -> "Not set." to UI.colors.gray
                else -> "No balance captured." to UI.colors.orange
            }
        )
    }
}

@Composable
private fun amountOutcome(result: SmsParseResult): Pair<String, Color> {
    val amount = result.amount
    val step = result.amountStep
    return when {
        result.amountSource == SmsAmountSource.AMOUNT_REGEX && amount != null ->
            "Amount ${amount.plain()} (read from \"${result.amountText}\")." to UI.colors.green
        amount != null -> {
            val why = if (step?.isSet == true) "This regex gave no number" else "Not set"
            "$why, so master group 1 is used: ${amount.plain()} (from \"${result.amountText}\")." to UI.colors.orange
        }
        result.amountText != null ->
            "\"${result.amountText}\" isn't a number after removing commas. No transaction." to UI.colors.red
        else -> "No amount here or in master group 1. No transaction." to UI.colors.red
    }
}

@Composable
private fun dateOutcome(result: SmsParseResult): Pair<String, Color> {
    val step = result.dateTimeStep
    val parsed = result.parsedDateTimeMillis
    val firstMatch = step?.matches?.firstOrNull()
    return when {
        parsed != null -> "Date: ${parsed.formatWith(fullDateFormatter)}" to UI.colors.green
        step == null || !step.isSet -> "Not set: the SMS receive time is used." to UI.colors.gray
        firstMatch == null -> "No match: the SMS receive time is used." to UI.colors.orange
        firstMatch.groups.size < 3 ->
            "Needs at least 3 groups: (day)(month)(year). Receive time is used." to UI.colors.red
        else -> "Couldn't build a date from these groups. Receive time is used." to UI.colors.red
    }
}

@Composable
private fun descriptionOutcome(result: SmsParseResult): Pair<String, Color> {
    val step = result.descriptionStep
    return when {
        step == null || !step.isSet -> "Not set: no description." to UI.colors.gray
        result.description != null ->
            "Every group of every match, joined with new lines (see the result below)." to UI.colors.green
        step.matched -> "Matched, but no group captured any text." to UI.colors.red
        else -> "No match: no description." to UI.colors.red
    }
}

@Composable
private fun SmsCard(sms: InboxSms) {
    val clipboard = LocalClipboardManager.current
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = sms.contactName?.let { "$it (${sms.address})" } ?: sms.address,
                    style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = buildString {
                        append(sms.dateMillis.formatWith(fullDateFormatter))
                        sms.subscriptionId?.let { append(" · SIM subscription $it") }
                        append(" · ${sms.body.length} chars")
                    },
                    style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
                )
            }
            Text(
                modifier = Modifier
                    .clickable { clipboard.setText(AnnotatedString(sms.body)) }
                    .padding(8.dp),
                text = "COPY",
                style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
            )
        }
        Spacer(Modifier.height(8.dp))
        SelectionContainer {
            Text(text = visibleText(sms.body), style = monoStyle())
        }
        val hidden = hiddenCharacters(sms.body)
        if (hidden.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "Hidden or special characters: " + hidden.joinToString(", "),
                style = UI.typo.c.style(color = UI.colors.orange, fontWeight = FontWeight.SemiBold)
            )
            Text(
                text = "These are often lost when copying an SMS to a computer. In Java regex, \\s and " +
                    "a typed space don't match a no-break space; use [\\s\\u00A0] or \\p{Z}.",
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }
    }
}

@Composable
private fun PreChecksCard(
    sms: InboxSms,
    senderFilter: String,
    senderOk: Boolean,
    subscriptionId: Int?,
    simOk: Boolean,
) {
    InfoCard {
        CardTitle("Account checks (before parsing)")
        Spacer(Modifier.height(8.dp))
        val (senderText, senderColor) = when {
            senderFilter.isEmpty() ->
                "Sender filter is empty, so every sender matches this account." to UI.colors.orange
            senderOk -> "Sender matches \"$senderFilter\"." to UI.colors.green
            else -> "Sender doesn't match \"$senderFilter\". The receiver skips this account for this SMS." to
                UI.colors.red
        }
        Text(text = senderText, style = UI.typo.c.style(color = senderColor, fontWeight = FontWeight.SemiBold))
        if (!senderOk && sms.contactName == null && !LocalContext.current.canReadContacts()) {
            Text(
                text = "Contact names can't be checked without the Contacts permission.",
                style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
            )
        }
        Spacer(Modifier.height(4.dp))
        val (simText, simColor) = when {
            subscriptionId == null -> "Any SIM is accepted." to UI.colors.gray
            sms.subscriptionId == null -> "This SMS has no SIM info, so the SIM check can't be tested." to
                UI.colors.gray
            simOk -> "SIM matches (subscription $subscriptionId)." to UI.colors.green
            else -> "SMS came in on SIM subscription ${sms.subscriptionId}, but the account expects " +
                "$subscriptionId. The receiver skips this account for this SMS." to UI.colors.red
        }
        Text(text = simText, style = UI.typo.c.style(color = simColor, fontWeight = FontWeight.SemiBold))
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Not simulated: the receiving phone number check, and whether another account " +
                "with a matching sender claims the SMS first.",
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun ModeCard(result: SmsParseResult) {
    InfoCard {
        CardTitle(if (result.useMultiRegex) "Mode: advanced multi-regex" else "Mode: single regex")
        Spacer(Modifier.height(4.dp))
        Text(
            text = buildString {
                append("Every pattern runs with ${SmsParser.FLAGS_LABEL}. ")
                append("DOTALL lets . match new lines, and MULTILINE makes ^ and $ match at each line.")
                if (!result.useMultiRegex) {
                    append(" Only this regex is used; the type comes from keywords.")
                }
            },
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun StepCard(
    title: String,
    step: SmsRegexStep?,
    outcome: Pair<String, Color>,
    emptyPatternNote: String? = null,
    needsGroups: Boolean = false,
    showAllMatches: Boolean = false,
    groupLabels: List<String> = emptyList(),
) {
    InfoCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CardTitle(title, Modifier.weight(1f))
            StepStatus(step)
        }

        val regex = step?.regex
        if (regex != null) {
            Spacer(Modifier.height(8.dp))
            FieldLabel("Pattern")
            if (regex.isEmpty() && emptyPatternNote != null) {
                Text(text = emptyPatternNote, style = UI.typo.c.style(color = UI.colors.orange))
            } else {
                Text(text = visibleText(regex), style = monoStyle())
                val hidden = hiddenCharacters(regex)
                if (hidden.isNotEmpty()) {
                    Text(
                        text = "Special characters in the pattern: " + hidden.joinToString(", ") +
                            ". A phone keyboard may have inserted them.",
                        style = UI.typo.c.style(color = UI.colors.orange, fontWeight = FontWeight.SemiBold)
                    )
                }
            }
        }

        step?.error?.let {
            Spacer(Modifier.height(8.dp))
            Text(text = "Regex error: $it", style = UI.typo.c.style(color = UI.colors.red))
        }

        val matches = step?.matches.orEmpty()
        val shown = if (showAllMatches) matches else matches.take(1)
        shown.forEachIndexed { index, match ->
            Spacer(Modifier.height(8.dp))
            FieldLabel(
                if (showAllMatches) {
                    "Match ${index + 1} of ${matches.size} (at char ${match.range.first + 1}, ${match.value.length} long)"
                } else {
                    "Matched text (at char ${match.range.first + 1}, ${match.value.length} long)"
                }
            )
            Text(text = visibleText(match.value), style = monoStyle())
            if (match.groups.isEmpty() && needsGroups) {
                Text(
                    text = "No capturing groups. Wrap the part you want in ( ).",
                    style = UI.typo.c.style(color = UI.colors.red)
                )
            }
            match.groups.forEachIndexed { groupIndex, group ->
                val label = groupLabels.getOrNull(groupIndex)?.let { " ($it)" }.orEmpty()
                Spacer(Modifier.height(4.dp))
                FieldLabel("Group ${groupIndex + 1}$label")
                if (group == null) {
                    Text(text = "didn't take part in the match", style = UI.typo.c.style(color = UI.colors.gray))
                } else {
                    Text(text = visibleText(group), style = monoStyle())
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = outcome.first,
            style = UI.typo.c.style(color = outcome.second, fontWeight = FontWeight.Bold)
        )
    }
}

@Composable
private fun StepStatus(step: SmsRegexStep?) {
    val (text, color) = when {
        step == null || !step.isSet -> "NOT SET" to UI.colors.gray
        step.error != null -> "INVALID" to UI.colors.red
        step.matches.size > 1 -> "${step.matches.size} MATCHES" to UI.colors.green
        step.matched -> "MATCH" to UI.colors.green
        else -> "NO MATCH" to UI.colors.orange
    }
    Text(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), UI.shapes.rFull)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        text = text,
        style = UI.typo.c.style(color = color, fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
private fun TypeSummary(result: SmsParseResult) {
    InfoCard {
        CardTitle("Type: ${result.type}")
        Spacer(Modifier.height(4.dp))
        Text(
            text = when (result.typeSource) {
                SmsTypeSource.INCOME_REGEX -> "From the income regex."
                SmsTypeSource.EXPENSE_REGEX -> "From the expense regex."
                SmsTypeSource.KEYWORDS -> "Neither regex matched, so keywords decided " +
                    "(credited/received/deposited/added → income, otherwise expense)."
            },
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun SingleRegexSummary(result: SmsParseResult) {
    InfoCard {
        CardTitle("Amount and type")
        Spacer(Modifier.height(4.dp))
        val amount = result.amount
        Text(
            text = when {
                amount != null -> "Amount ${amount.plain()} from group 1 (\"${result.amountText}\")."
                result.amountText != null -> "Group 1 \"${result.amountText}\" isn't a number."
                else -> "Group 1 captured nothing."
            },
            style = UI.typo.c.style(
                color = if (amount != null) UI.colors.green else UI.colors.red,
                fontWeight = FontWeight.SemiBold
            )
        )
        Text(
            text = "Type ${result.type}, from keywords. Description and date aren't parsed in this mode.",
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )
    }
}

@Composable
private fun ResultCard(result: SmsParseResult, senderOk: Boolean, simOk: Boolean) {
    val transaction = result.transaction
    InfoCard {
        if (transaction == null) {
            CardTitle("No transaction would be created", color = UI.colors.red)
            Spacer(Modifier.height(4.dp))
            Text(
                text = result.failure.orEmpty(),
                style = UI.typo.c.style(color = UI.colors.red, fontWeight = FontWeight.SemiBold)
            )
        } else {
            CardTitle("Transaction that would be created", color = UI.colors.green)
            Spacer(Modifier.height(8.dp))
            ResultField("Type", transaction.type.name)
            ResultField("Amount", transaction.amount.plain())
            ResultField(
                "Date",
                transaction.dateTimeMillis.formatWith(fullDateFormatter) +
                    if (result.parsedDateTimeMillis != null) " (from the SMS text)" else " (receive time)"
            )
            ResultField("Title", transaction.title)
            FieldLabel("Description")
            val description = transaction.description
            if (description.isNullOrBlank()) {
                Text(text = "(none)", style = UI.typo.c.style(color = UI.colors.gray))
            } else {
                Text(text = visibleText(description), style = monoStyle())
            }
        }

        if (!senderOk || !simOk) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "In real use this SMS wouldn't reach this account: see the account checks above.",
                style = UI.typo.c.style(color = UI.colors.orange, fontWeight = FontWeight.Bold)
            )
        }
    }
}

@Composable
private fun ResultField(label: String, value: String) {
    FieldLabel(label)
    Text(text = value, style = monoStyle())
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun InfoCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .background(UI.colors.medium, UI.shapes.r4)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top,
        content = content
    )
}

@Composable
private fun CardTitle(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = UI.colors.pureInverse,
) {
    Text(
        modifier = modifier,
        text = text,
        style = UI.typo.b2.style(color = color, fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
private fun monoStyle(): TextStyle = UI.typo.c.style(fontWeight = FontWeight.Normal)
    .copy(fontFamily = FontFamily.Monospace)

/** Shows new lines, tabs, no-break spaces and other invisible characters as markers. */
@Composable
private fun visibleText(text: String): AnnotatedString {
    val markerStyle = SpanStyle(color = UI.colors.orange, fontWeight = FontWeight.Bold)
    return remember(text, markerStyle) {
        buildAnnotatedString {
            for (ch in text) {
                val marker = invisibleMarker(ch)
                when {
                    ch == '\n' -> {
                        withStyle(markerStyle) { append("⏎") }
                        append('\n')
                    }
                    marker != null -> withStyle(markerStyle) { append(marker) }
                    else -> append(ch)
                }
            }
        }
    }
}

private fun invisibleMarker(ch: Char): String? = when {
    ch == '\r' -> "␍"
    ch == '\t' -> "⇥"
    ch == ' ' -> "⍽"
    ch == ' ' || ch == '\n' -> null
    Character.isISOControl(ch) ||
        Character.getType(ch) == Character.FORMAT.toInt() ||
        Character.isSpaceChar(ch) -> "‹U+%04X›".format(ch.code)
    else -> null
}

/** Lists every character outside printable ASCII, e.g. "U+00A0 NO-BREAK SPACE ×2". */
private fun hiddenCharacters(text: String): List<String> {
    val counts = text.filter { it != '\n' && (it.code < 0x20 || it.code > 0x7E) }
        .groupingBy { it }
        .eachCount()
    if (counts.isEmpty()) return emptyList()
    val described = counts.entries.take(MAX_HIDDEN_CHAR_KINDS).map { (ch, count) ->
        val name = Character.getName(ch.code) ?: "unknown"
        "U+%04X %s ×%d".format(ch.code, name, count)
    }
    val more = counts.size - described.size
    return if (more > 0) described + "$more more" else described
}

private fun Double.plain(): String = BigDecimal.valueOf(this).toPlainString()

private fun Long.formatWith(formatter: DateTimeFormatter): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).format(formatter)
