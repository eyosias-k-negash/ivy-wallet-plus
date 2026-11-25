package com.ivy.wallet.ui.theme.modal.edit

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.legacy.Constants
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.sms.SmsGroup
import com.ivy.legacy.sms.SmsLocationAccess
import com.ivy.legacy.sms.SmsParser
import com.ivy.legacy.sms.SmsParsingRules
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.components.IvyOutlinedTextField
import kotlinx.serialization.InternalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

/** The SMS part of the account form. Remember one per form session, like the form's other values. */
@Stable
internal class SmsSettingsState(account: Account?) {
    var autoListenEnabled by mutableStateOf(account?.smsAutoListenEnabled ?: false)

    // Profile: which SMS belong to the account
    var senderPhone by mutableStateOf(TextFieldValue(account?.smsSenderPhone ?: ""))
    var subscriptionIdText by mutableStateOf(TextFieldValue(account?.smsSubscriptionId?.toString() ?: ""))
    var receiverPhoneText by mutableStateOf(TextFieldValue(account?.smsReceiverPhone ?: ""))

    // Capture menu: what the transaction gets besides its amount and type
    var captureLocation by mutableStateOf(account?.smsCaptureLocation ?: true)
    var captureDate by mutableStateOf(account?.smsCaptureDate ?: true)
    var captureFee by mutableStateOf(account?.smsCaptureFee ?: false)
    var captureDescription by mutableStateOf(account?.smsCaptureDescription ?: true)
    var captureBalance by mutableStateOf(account?.smsCaptureBalance ?: true)

    // SMS structure: the regexes
    var masterRegex by mutableStateOf(TextFieldValue(account?.smsParsingRegex ?: Constants.DEFAULT_SMS_REGEX))
    var useMultiRegex by mutableStateOf(account?.useMultiRegex ?: false)
    var incomeRegex by mutableStateOf(TextFieldValue(account?.smsIncomeRegex ?: ""))
    var expenseRegex by mutableStateOf(TextFieldValue(account?.smsExpenseRegex ?: ""))
    var amountRegex by mutableStateOf(TextFieldValue(account?.smsAmountRegex ?: ""))
    var dateTimeRegex by mutableStateOf(TextFieldValue(account?.smsDateTimeRegex ?: ""))
    var descriptionRegex by mutableStateOf(TextFieldValue(account?.smsDescriptionRegex ?: ""))
    var balanceRegex by mutableStateOf(TextFieldValue(account?.smsBalanceRegex ?: ""))
    var feeRegex by mutableStateOf(TextFieldValue(account?.smsFeeRegex ?: ""))

    // Collapsed parts show what can be filled in for you; expanded ones add the fields to type in.
    var profileExpanded by mutableStateOf(false)
    var captureExpanded by mutableStateOf(false)
    var structureExpanded by mutableStateOf(false)
    var advancedExpanded by mutableStateOf(true)

    val subscriptionId: Int? get() = subscriptionIdText.text.toIntOrNull()

    val advancedRegexes: List<TextFieldValue>
        get() = listOf(incomeRegex, expenseRegex, amountRegex, dateTimeRegex, descriptionRegex, balanceRegex, feeRegex)

    fun rules(): SmsParsingRules = SmsParsingRules(
        masterRegex = masterRegex.text,
        useMultiRegex = useMultiRegex,
        incomeRegex = incomeRegex.text,
        expenseRegex = expenseRegex.text,
        amountRegex = amountRegex.text,
        dateTimeRegex = dateTimeRegex.text,
        descriptionRegex = descriptionRegex.text,
        balanceRegex = balanceRegex.text,
        feeRegex = feeRegex.text,
        captureDate = captureDate,
        captureDescription = captureDescription,
        captureBalance = captureBalance,
        captureFee = captureFee,
    )

    /** Every value the account saves, so the form can tell whether leaving it would lose edits. */
    fun values(): List<Any?> = listOf(
        autoListenEnabled, senderPhone.text, subscriptionIdText.text, receiverPhoneText.text,
        captureLocation, captureDate, captureFee, captureDescription, captureBalance,
        masterRegex.text, useMultiRegex,
    ) + advancedRegexes.map { it.text }

    fun selectSim(context: Context, subId: Int?) {
        val simChanged = subId != subscriptionId
        subscriptionIdText = TextFieldValue(subId?.toString() ?: "")
        val phone = subId?.let { getSimPhoneNumber(context, it) }
        if (phone.isNotNullOrBlank()) {
            receiverPhoneText = TextFieldValue(phone!!)
        } else if (simChanged) {
            // Keeping the previous SIM's number could make the receiver reject SMS on this one.
            receiverPhoneText = TextFieldValue("")
        }
    }

    fun importConfig(config: SmsRegexConfig) {
        masterRegex = TextFieldValue(config.masterMatch ?: "")
        useMultiRegex = config.useMultiRegex
        incomeRegex = TextFieldValue(config.incomeRegex ?: "")
        expenseRegex = TextFieldValue(config.expenseRegex ?: "")
        amountRegex = TextFieldValue(config.amountRegex ?: "")
        dateTimeRegex = TextFieldValue(config.dateTimeRegex ?: "")
        descriptionRegex = TextFieldValue(config.descriptionRegex ?: "")
        balanceRegex = TextFieldValue(config.balanceRegex ?: "")
        feeRegex = TextFieldValue(config.feeRegex ?: "")
    }
}

@OptIn(InternalSerializationApi::class)
@Serializable
internal data class SmsRegexConfig(
    val masterMatch: String? = null,
    val useMultiRegex: Boolean = false,
    val incomeRegex: String? = null,
    val expenseRegex: String? = null,
    val amountRegex: String? = null,
    val dateTimeRegex: String? = null,
    val descriptionRegex: String? = null,
    val balanceRegex: String? = null,
    val feeRegex: String? = null,
)

/**
 * The account form's SMS settings: the Auto Listen switch and, when it's on, three parts
 * (Profile, Capture menu, SMS structure).
 */
@Composable
internal fun ColumnScope.SmsSettings(
    state: SmsSettingsState,
    onSampleSms: () -> Unit,
    onTestSms: () -> Unit,
) {
    IvyCheckboxWithText(
        modifier = Modifier
            .padding(start = 16.dp)
            .align(Alignment.Start),
        text = "Auto Listen to SMS",
        checked = state.autoListenEnabled
    ) {
        state.autoListenEnabled = it
    }

    if (!state.autoListenEnabled) return

    Spacer(Modifier.height(8.dp))
    ProfileSection(state, onSampleSms)
    Spacer(Modifier.height(12.dp))
    CaptureSection(state)
    Spacer(Modifier.height(12.dp))
    StructureSection(state, onTestSms)
}

@Composable
private fun ProfileSection(state: SmsSettingsState, onSampleSms: () -> Unit) {
    val context = LocalContext.current
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
        onResult = { uri ->
            uri?.let { getContactNameFromUri(context, it) }?.let {
                state.senderPhone = TextFieldValue(it)
            }
        }
    )
    val expanded = state.profileExpanded

    SmsSection(
        title = "Profile",
        summary = "Which SMS belong to this account",
        expandLabel = "MANUAL",
        expanded = expanded,
        onExpandedChange = { state.profileExpanded = it },
    ) {
        AutoFillRow(
            description = "Fill in the bank and SIM from an SMS the bank sent you.",
            action = "SAMPLE SMS",
            onClick = onSampleSms
        )

        Spacer(Modifier.height(12.dp))
        FieldLabel("Bank phone no. / contact")
        if (expanded) {
            Box(
                modifier = Modifier.fieldPadding(),
                contentAlignment = Alignment.CenterEnd
            ) {
                IvyOutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = state.senderPhone,
                    hint = "Sender name or number",
                    onValueChanged = { state.senderPhone = it }
                )
                TextAction(modifier = Modifier.padding(end = 12.dp), text = "PICK") {
                    contactPickerLauncher.launch(null)
                }
            }
        } else {
            ValueBox(
                text = state.senderPhone.text.ifBlank { "Not set" },
                isSet = state.senderPhone.text.isNotBlank(),
                onClick = { state.profileExpanded = true },
                action = "PICK",
                onAction = { contactPickerLauncher.launch(null) }
            )
        }
        FieldHelp(
            if (state.senderPhone.text.isBlank()) {
                "Empty matches every sender. Pick the bank's contact, or type its name or number."
            } else {
                "An SMS counts when its sender, or the sender's contact name, contains this."
            },
            color = if (state.senderPhone.text.isBlank()) UI.colors.orange else Gray
        )

        Spacer(Modifier.height(12.dp))
        FieldLabel("Contact SIM no.")
        if (expanded) {
            IvyOutlinedTextField(
                modifier = Modifier.fieldPadding(),
                value = state.receiverPhoneText,
                hint = "Receiving phone number",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                onValueChanged = { state.receiverPhoneText = it }
            )
            FieldHelp("Used to verify that the SMS arrived on the correct SIM.")

            Spacer(Modifier.height(12.dp))
            FieldLabel("SIM slot (subscription ID)")
            IvyOutlinedTextField(
                modifier = Modifier.fieldPadding(),
                value = state.subscriptionIdText,
                hint = "Any SIM",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                onValueChanged = { state.subscriptionIdText = it }
            )
            FieldHelp("Only SMS that arrive on this SIM count. Leave it empty for any SIM.")
            TextAction(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .align(Alignment.CenterHorizontally),
                text = "CHOOSE A DETECTED SIM"
            ) {
                state.profileExpanded = false
            }
        } else {
            SimSubscriptionPicker(
                modifier = Modifier.fieldPadding(),
                subId = state.subscriptionId,
                receiverPhone = state.receiverPhoneText.text,
                onSubIdChange = { state.selectSim(context, it) },
                onManualEntryClick = { state.profileExpanded = true }
            )
            FieldHelp("Optional. Only SMS that arrive on this SIM count.")
        }
    }
}

@Composable
private fun CaptureSection(state: SmsSettingsState) {
    val context = LocalContext.current
    var hasLocationAccess by remember(state) {
        mutableStateOf(SmsLocationAccess.hasFullAccess(context))
    }
    // Android 11+ only grants background location on its own, after foreground location.
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasLocationAccess = SmsLocationAccess.hasFullAccess(context)
            if (!granted) {
                android.widget.Toast.makeText(
                    context,
                    "Choose \"Allow all the time\" so SMS transactions get a location",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    )
    val foregroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            hasLocationAccess = SmsLocationAccess.hasFullAccess(context)
            when {
                !SmsLocationAccess.hasForeground(context) -> android.widget.Toast.makeText(
                    context,
                    "Allow location access so SMS transactions get a location",
                    android.widget.Toast.LENGTH_LONG
                ).show()

                !SmsLocationAccess.hasBackground(context) ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            }
        }
    )
    val requestLocationAccess: () -> Unit = {
        when {
            !SmsLocationAccess.hasForeground(context) ->
                foregroundLocationLauncher.launch(SmsLocationAccess.foregroundPermissions)

            !SmsLocationAccess.hasBackground(context) ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            else -> hasLocationAccess = true
        }
    }

    val expanded = state.captureExpanded

    // A value comes from its own regex, which only the advanced multi-regex runs, or from a
    // same-named group in the master regex.
    val masterGroups = remember(state.masterRegex.text) {
        SmsParser.scanGroupNames(state.masterRegex.text).mapNotNull { it?.let(SmsGroup::of) }.toSet()
    }
    fun regexNote(regex: TextFieldValue, group: SmsGroup): String? {
        val hasOwnRegex = state.useMultiRegex && regex.text.isNotBlank()
        return "NO REGEX".takeIf { !hasOwnRegex && group !in masterGroups }
    }

    val captured = listOfNotNull(
        "Location".takeIf { state.captureLocation },
        "Date".takeIf { state.captureDate },
        "Fee".takeIf { state.captureFee },
        "Description".takeIf { state.captureDescription },
        "Balance".takeIf { state.captureBalance },
    )

    SmsSection(
        title = "Capture menu",
        summary = captured.joinToString(", ").ifEmpty { "Only the amount and type" },
        expandLabel = "DETAILS",
        expanded = expanded,
        onExpandedChange = { state.captureExpanded = it },
    ) {
        if (!state.useMultiRegex) {
            FieldHelp(
                "Without the advanced multi-regex, date, fee, description and balance only come " +
                    "from named groups in the master regex, like (?<day>…)."
            )
            Spacer(Modifier.height(4.dp))
        }

        CaptureOption(
            label = "Location",
            checked = state.captureLocation,
            onCheckedChange = {
                state.captureLocation = it
                if (it && !hasLocationAccess) requestLocationAccess()
            },
            details = "Saves where your phone was when the SMS arrived.".takeIf { expanded },
        )
        if (state.captureLocation && !hasLocationAccess) {
            FieldHelp(
                "Needs location access set to \"Allow all the time\".",
                color = UI.colors.orange
            )
            TextAction(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .align(Alignment.CenterHorizontally),
                text = "ALLOW LOCATION ACCESS",
                onClick = requestLocationAccess
            )
        }

        CaptureOption(
            label = "Date",
            checked = state.captureDate,
            onCheckedChange = { state.captureDate = it },
            note = regexNote(state.dateTimeRegex, SmsGroup.DAY).takeIf { state.captureDate },
            details = (
                "Dates the transaction from the SMS text, with the Date/time regex or the master's " +
                    "day and month groups. Otherwise, or when no date is found, it's dated when the SMS arrives."
                ).takeIf { expanded },
        )
        CaptureOption(
            label = "Fee",
            checked = state.captureFee,
            onCheckedChange = { state.captureFee = it },
            note = regexNote(state.feeRegex, SmsGroup.FEE).takeIf { state.captureFee },
            details = (
                "Saves the fee, from the Fee regex or the master's fee groups, as its own expense in " +
                    "your Fees category. Leave it off if the SMS amount already includes the fee."
                ).takeIf { expanded },
        )
        CaptureOption(
            label = "Description",
            checked = state.captureDescription,
            onCheckedChange = { state.captureDescription = it },
            note = regexNote(state.descriptionRegex, SmsGroup.DESCRIPTION)
                .takeIf { state.captureDescription },
            details = "Fills the description from the Description regex or the master's description groups."
                .takeIf { expanded },
        )
        CaptureOption(
            label = "Balance & rebalance",
            checked = state.captureBalance,
            onCheckedChange = { state.captureBalance = it },
            note = regexNote(state.balanceRegex, SmsGroup.BALANCE).takeIf { state.captureBalance },
            details = (
                "Adds the balance, from the Balance regex or the master's balance group, to the " +
                    "description, and warns when it's 10 or more away from the app's balance. Tapping the warning " +
                    "adds the rebalancing transaction."
                ).takeIf { expanded },
        )
    }
}

@Composable
private fun StructureSection(state: SmsSettingsState, onTestSms: () -> Unit) {
    val context = LocalContext.current
    val configPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val jsonStr = stream.bufferedReader().readText()
                        state.importConfig(json.decodeFromString<SmsRegexConfig>(jsonStr))
                        android.widget.Toast.makeText(
                            context,
                            "Config imported successfully!",
                            android.widget.Toast.LENGTH_SHORT
                        ).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(
                        context,
                        "Failed to import config: ${e.message}",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    )
    val expanded = state.structureExpanded
    val advancedSetCount = state.advancedRegexes.count { it.text.isNotBlank() }
    val advancedTotal = state.advancedRegexes.size

    SmsSection(
        title = "SMS structure",
        summary = if (state.useMultiRegex) {
            "Advanced multi-regex: $advancedSetCount of $advancedTotal regexes set"
        } else {
            "Simple: one master regex"
        },
        expandLabel = "MANUAL",
        expanded = expanded,
        onExpandedChange = { state.structureExpanded = it },
    ) {
        AutoFillRow(
            description = "Load every regex from a JSON config file.",
            action = "IMPORT CONFIG",
            onClick = { configPickerLauncher.launch(arrayOf("application/json", "text/*")) }
        )

        // Every SMS goes through the master regex, so it stays in view; MANUAL adds the advanced ones.
        Spacer(Modifier.height(16.dp))
        SubsectionTitle("Simple")
        FieldLabel(if (state.useMultiRegex) "Master match regex" else "Master regex")
        IvyOutlinedTextField(
            modifier = Modifier.fieldPadding(),
            value = state.masterRegex,
            hint = "Group 1 or (?<amount>…)",
            onValueChanged = { state.masterRegex = it }
        )
        FieldHelp(
            if (state.useMultiRegex) {
                "An SMS must match it before the advanced regexes run. Its named groups fill in " +
                    "what those find nothing for, and its group 1 is the amount without names."
            } else {
                "An SMS must match it. Group 1, or a group named amount, is the amount. Name other " +
                    "groups, like (?<day>…), to read more. Empty uses a generic pattern."
            }
        )

        if (expanded) {
            Spacer(Modifier.height(16.dp))
            AdvancedHeader(state, setCount = advancedSetCount, total = advancedTotal)
            if (state.useMultiRegex && state.advancedExpanded) {
                AdvancedRegexFields(state)
            }
        }

        Spacer(Modifier.height(12.dp))
        TextAction(
            modifier = Modifier.align(Alignment.CenterHorizontally),
            text = "TEST WITH AN SMS",
            onClick = onTestSms
        )
    }
}

@Composable
private fun AdvancedHeader(state: SmsSettingsState, setCount: Int, total: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IvyCheckboxWithText(
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f),
            text = "Use advanced multi-regex",
            checked = state.useMultiRegex
        ) {
            state.useMultiRegex = it
            if (it) state.advancedExpanded = true
        }
        if (state.useMultiRegex) {
            ExpandToggle(
                label = if (state.advancedExpanded) "HIDE" else "SHOW",
                expanded = state.advancedExpanded,
                onClick = { state.advancedExpanded = !state.advancedExpanded }
            )
        }
    }
    when {
        !state.useMultiRegex -> FieldHelp(
            "Separate regexes for the type, amount, date, description, balance and fee."
        )
        !state.advancedExpanded -> FieldHelp("$setCount of $total regexes set.")
    }
}

@Composable
private fun AdvancedRegexFields(state: SmsSettingsState) {
    val notCaptured = "OFF IN CAPTURE MENU"
    RegexField(
        label = "Income identification",
        value = state.incomeRegex,
        hint = "Matches income SMS",
        onValueChange = { state.incomeRegex = it }
    )
    RegexField(
        label = "Expense identification",
        value = state.expenseRegex,
        hint = "Matches expense SMS",
        onValueChange = { state.expenseRegex = it }
    )
    RegexField(
        label = "Amount",
        value = state.amountRegex,
        hint = "Group 1 or (?<amount>…)",
        onValueChange = { state.amountRegex = it }
    )
    RegexField(
        label = "Date/time",
        value = state.dateTimeRegex,
        hint = "(?<day>…), (?<month>…), (?<year>…)…",
        note = notCaptured.takeIf { !state.captureDate },
        onValueChange = { state.dateTimeRegex = it }
    )
    RegexField(
        label = "Description",
        value = state.descriptionRegex,
        hint = "Every group or (?<description>…)",
        note = notCaptured.takeIf { !state.captureDescription },
        onValueChange = { state.descriptionRegex = it }
    )
    RegexField(
        label = "Balance",
        value = state.balanceRegex,
        hint = "Group 1 or (?<balance>…)",
        note = notCaptured.takeIf { !state.captureBalance },
        onValueChange = { state.balanceRegex = it }
    )
    RegexField(
        label = "Fee",
        value = state.feeRegex,
        hint = "Every group or (?<fee>…), added up",
        note = notCaptured.takeIf { !state.captureFee },
        onValueChange = { state.feeRegex = it }
    )
}

@Composable
private fun RegexField(
    label: String,
    value: TextFieldValue,
    hint: String,
    onValueChange: (TextFieldValue) -> Unit,
    note: String? = null,
) {
    Spacer(Modifier.height(12.dp))
    FieldLabel(label, note = note)
    IvyOutlinedTextField(
        modifier = Modifier.fieldPadding(),
        value = value,
        hint = hint,
        onValueChanged = onValueChange
    )
}

/**
 * A card with a header that switches between collapsed and expanded. Unlike a plain accordion,
 * the collapsed card still shows [content]: it reads [expanded] to add its manual fields.
 */
@Composable
private fun SmsSection(
    title: String,
    summary: String,
    expandLabel: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(UI.colors.medium, UI.shapes.r4)
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = if (expanded) "Hide" else expandLabel.lowercase()) {
                    onExpandedChange(!expanded)
                }
                .padding(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = UI.typo.b1.style(fontWeight = FontWeight.ExtraBold)
                )
                Text(
                    text = summary,
                    style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.Medium)
                )
            }
            Spacer(Modifier.width(8.dp))
            ExpandToggle(label = if (expanded) "HIDE" else expandLabel, expanded = expanded, onClick = null)
        }

        content()

        Spacer(Modifier.height(16.dp))
    }
}

/** "MANUAL ⌄" next to a header. [onClick] is null when the whole header is the button. */
@Composable
private fun ExpandToggle(label: String, expanded: Boolean, onClick: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .clip(UI.shapes.rFull)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
        )
        Spacer(Modifier.width(4.dp))
        IvyIcon(
            modifier = Modifier
                .height(10.dp)
                .rotate(if (expanded) 180f else 0f),
            icon = R.drawable.ic_expandarrow,
            tint = UI.colors.primary,
            contentDescription = if (expanded) "Collapse" else "Expand"
        )
    }
}

@Composable
private fun AutoFillRow(description: String, action: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier.weight(1f),
            text = description,
            style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.Medium)
        )
        Spacer(Modifier.width(8.dp))
        Text(
            modifier = Modifier
                .clip(UI.shapes.rFull)
                .background(UI.colors.primary.copy(alpha = 0.1f), UI.shapes.rFull)
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            text = action,
            style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
        )
    }
}

@Composable
private fun CaptureOption(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    note: String? = null,
    details: String? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 4.dp, end = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IvyCheckboxWithText(
            modifier = Modifier.weight(1f),
            text = label,
            checked = checked,
            onCheckedChange = onCheckedChange
        )
        if (note != null) {
            Spacer(Modifier.width(8.dp))
            NoteTag(note)
        }
    }
    if (details != null) {
        Text(
            modifier = Modifier.padding(start = 56.dp, end = 24.dp, bottom = 8.dp),
            text = details,
            style = UI.typo.c.style(color = Gray)
        )
    }
}

/** A read-only value with an action, standing in for a text field while its part is collapsed. */
@Composable
private fun ValueBox(
    text: String,
    isSet: Boolean,
    onClick: () -> Unit,
    action: String,
    onAction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fieldPadding()
            .clip(UI.shapes.rFull)
            .border(
                width = 2.dp,
                color = if (isSet) UI.colors.primary else UI.colors.gray,
                shape = UI.shapes.rFull
            )
            .background(
                if (isSet) UI.colors.primary.copy(alpha = 0.1f) else Color.Transparent,
                UI.shapes.rFull
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onClick)
                .padding(start = 24.dp, end = 8.dp, top = 16.dp, bottom = 16.dp),
            text = text,
            style = UI.typo.b2.style(
                color = if (isSet) UI.colors.pureInverse else UI.colors.gray,
                fontWeight = if (isSet) FontWeight.Bold else FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )
        )
        TextAction(modifier = Modifier.padding(end = 12.dp), text = action, onClick = onAction)
    }
}

@Composable
private fun SubsectionTitle(text: String) {
    Text(
        modifier = Modifier.padding(start = 20.dp, bottom = 8.dp),
        text = text,
        style = UI.typo.b2.style(fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
private fun FieldLabel(text: String, note: String? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 16.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = text.uppercase(),
            style = UI.typo.c.style(color = Gray, fontWeight = FontWeight.ExtraBold)
        )
        if (note != null) NoteTag(note, color = Gray)
    }
}

@Composable
private fun FieldHelp(text: String, color: Color = Gray) {
    Text(
        modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
        text = text,
        style = UI.typo.c.style(color = color)
    )
}

@Composable
private fun NoteTag(text: String, color: Color = UI.colors.orange) {
    Text(
        modifier = Modifier
            .background(color.copy(alpha = 0.15f), UI.shapes.rFull)
            .padding(horizontal = 8.dp, vertical = 2.dp),
        text = text,
        style = UI.typo.c.style(color = color, fontWeight = FontWeight.ExtraBold)
    )
}

@Composable
private fun TextAction(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Text(
        modifier = modifier
            .clip(UI.shapes.rFull)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        text = text,
        style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
    )
}

private fun Modifier.fieldPadding(): Modifier = padding(horizontal = 12.dp).fillMaxWidth()

internal fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun getContactNameFromUri(context: Context, contactUri: Uri): String? {
    return try {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
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

// READ_PHONE_STATE lists the SIMs; READ_PHONE_NUMBERS lets Android tell us each SIM's number.
internal val simPermissions = listOf(
    Manifest.permission.READ_PHONE_STATE,
    Manifest.permission.READ_PHONE_NUMBERS
)

/**
 * "Carrier (subscription ID): phone number · SIM name". The number is left out when it's unknown,
 * and the name when the user hasn't given the SIM one.
 */
internal fun formatSim(context: Context, info: SubscriptionInfo?, subId: Int, phone: String?): String =
    buildString {
        val carrier = info?.carrierName?.takeIf { it.isNotBlank() }?.toString() ?: "SIM"
        append("$carrier ($subId)")
        if (phone.isNotNullOrBlank()) append(": $phone")
        val name = info?.let { simName(context, it) }
        if (name.isNotNullOrBlank() && !name.equals(carrier, ignoreCase = true)) append(" · $name")
    }

private fun simName(context: Context, info: SubscriptionInfo): String? {
    // Samsung keeps the names set in its SIM manager here, one per slot, not in the SIM's display name.
    val samsungName = try {
        Settings.System.getString(context.contentResolver, "select_name_${info.simSlotIndex + 1}")
    } catch (_: SecurityException) {
        null
    }
    return samsungName ?: info.displayName?.toString()
}

@SuppressLint("MissingPermission")
@Composable
private fun SimSubscriptionPicker(
    subId: Int?,
    receiverPhone: String,
    onSubIdChange: (Int?) -> Unit,
    onManualEntryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val subscriptionManager = remember {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    }
    var expanded by remember { mutableStateOf(false) }
    // Re-read when the list opens or the SIM changes, in case Phone access was just granted.
    val activeSubscriptions = remember(subId, expanded) {
        try {
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }
    // The list opens either way. Without access it can only offer Manual Entry.
    val simPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { expanded = true }
    )

    val selectedSim = activeSubscriptions.find { it.subscriptionId == subId }
    // Shows the saved receiving number, which is what the SMS receiver checks.
    val displayText = subId?.let { formatSim(context, selectedSim, it, receiverPhone) }
        ?: "Select SIM Slot"

    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(UI.shapes.rFull)
                .border(
                    width = 2.dp,
                    color = if (subId == null) UI.colors.gray else UI.colors.primary,
                    shape = UI.shapes.rFull
                )
                .background(UI.colors.primary.copy(alpha = 0.1f), UI.shapes.rFull)
                .clickable {
                    val missing = simPermissions.filterNot { context.hasPermission(it) }
                    if (missing.isEmpty()) {
                        expanded = true
                    } else {
                        simPermissionLauncher.launch(missing.toTypedArray())
                    }
                }
                .padding(vertical = 16.dp, horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = displayText,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            )
            Spacer(Modifier.width(8.dp))
            IvyIcon(
                modifier = Modifier.height(12.dp),
                icon = R.drawable.ic_expandarrow,
                tint = UI.colors.pureInverse
            )
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            activeSubscriptions.forEach { info ->
                val phone = getSimPhoneNumber(context, info.subscriptionId)
                DropdownMenuItem(
                    text = {
                        Text(
                            text = formatSim(context, info, info.subscriptionId, phone),
                            fontWeight = FontWeight.Bold
                        )
                    },
                    onClick = {
                        onSubIdChange(info.subscriptionId)
                        expanded = false
                    }
                )
            }
            if (activeSubscriptions.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No SIMs detected") },
                    onClick = { expanded = false },
                    enabled = false
                )
            }
            DropdownMenuItem(
                text = { Text("Clear Selection") },
                onClick = {
                    onSubIdChange(null)
                    expanded = false
                }
            )
            DropdownMenuItem(
                text = { Text("Manual Entry") },
                onClick = {
                    onManualEntryClick()
                    expanded = false
                }
            )
        }
    }
}

@SuppressLint("MissingPermission")
private fun getSimPhoneNumber(context: Context, subId: Int): String? {
    val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            subscriptionManager?.getPhoneNumber(subId)
        } else {
            subscriptionManager?.activeSubscriptionInfoList?.find { it.subscriptionId == subId }?.number
        }
    } catch (_: Exception) {
        null
    }
}
