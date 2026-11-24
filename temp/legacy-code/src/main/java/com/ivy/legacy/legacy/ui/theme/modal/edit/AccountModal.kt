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
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraintsScope
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.legacy.ui.IvyColorPicker
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.sms.ParsedSmsTransaction
import com.ivy.legacy.sms.SmsLocationAccess
import com.ivy.legacy.sms.SmsParsingRules
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.legacy.utils.toUpperCaseLocal
import com.ivy.navigation.EditTransactionScreen
import com.ivy.navigation.navigation
import com.ivy.ui.R
import com.ivy.wallet.domain.data.IvyCurrency
import com.ivy.wallet.domain.deprecated.logic.model.CreateAccountData
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.components.IvyCheckboxWithText
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.components.IvyOutlinedTextField
import com.ivy.wallet.ui.theme.modal.ChooseIconModal
import com.ivy.wallet.ui.theme.modal.CurrencyModal
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalAddSave
import com.ivy.wallet.ui.theme.modal.ModalAmountSection
import com.ivy.wallet.ui.theme.modal.ModalTitle
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.InternalSerializationApi
import java.util.UUID

private val json = Json {
    ignoreUnknownKeys = true
    isLenient = true
    encodeDefaults = true
}

@Deprecated("Old design system. Use `:ivy-design` and Material3")
data class AccountModalData(
    val account: Account?,
    val baseCurrency: String,
    val balance: Double,
    val adjustBalanceMode: Boolean = false,
    val forceNonZeroBalance: Boolean = false,
    val autoFocusKeyboard: Boolean = true,
    val id: UUID = UUID.randomUUID()
)

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxWithConstraintsScope.AccountModal(
    modal: AccountModalData?,
    onCreateAccount: (CreateAccountData) -> Unit,
    onEditAccount: (Account, balance: Double) -> Unit,
    dismiss: () -> Unit,
) {
    val account = modal?.account
    var nameTextFieldValue by remember(modal) {
        mutableStateOf(selectEndTextFieldValue(account?.name))
    }
    var color by remember(modal) {
        mutableStateOf(account?.color?.let { Color(it) } ?: Ivy)
    }
    var amount by remember(modal) {
        mutableStateOf(modal?.balance ?: 0.0)
    }
    var currencyCode by remember(modal) {
        mutableStateOf(account?.currency ?: modal?.baseCurrency ?: "")
    }
    var icon by remember(modal) {
        mutableStateOf(account?.icon)
    }
    var includeInBalance by remember(modal) {
        mutableStateOf(account?.includeInBalance ?: true)
    }

    var smsAutoListenEnabled by remember(modal) {
        mutableStateOf(account?.smsAutoListenEnabled ?: false)
    }
    var smsSenderPhone by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsSenderPhone ?: ""))
    }
    var smsSubscriptionIdText by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsSubscriptionId?.toString() ?: ""))
    }
    var smsReceiverPhoneText by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsReceiverPhone ?: ""))
    }
    var manualSimEntry by remember(modal) {
        mutableStateOf(false)
    }
    var smsParsingRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsParsingRegex ?: com.ivy.legacy.Constants.DEFAULT_SMS_REGEX))
    }
    var useMultiRegex by remember(modal) {
        mutableStateOf(account?.useMultiRegex ?: false)
    }
    var smsIncomeRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsIncomeRegex ?: ""))
    }
    var smsExpenseRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsExpenseRegex ?: ""))
    }
    var smsAmountRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsAmountRegex ?: ""))
    }
    var smsDateTimeRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsDateTimeRegex ?: ""))
    }
    var smsDescriptionRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsDescriptionRegex ?: ""))
    }
    var smsBalanceRegex by remember(modal) {
        mutableStateOf(TextFieldValue(account?.smsBalanceRegex ?: ""))
    }
    var smsCaptureLocation by remember(modal) {
        mutableStateOf(account?.smsCaptureLocation ?: true)
    }

    var amountModalVisible by remember { mutableStateOf(false) }
    var currencyModalVisible by remember { mutableStateOf(false) }
    var chooseIconModalVisible by remember(modal) {
        mutableStateOf(false)
    }
    var smsTesterVisible by remember(modal) {
        mutableStateOf(false)
    }
    var smsSamplePickerVisible by remember(modal) {
        mutableStateOf(false)
    }

    // Every value that save() writes, so the form can tell whether leaving it would lose edits.
    val formValues = listOf<Any?>(
        nameTextFieldValue.text, color, amount, currencyCode, icon, includeInBalance,
        smsAutoListenEnabled, smsSenderPhone.text, smsSubscriptionIdText.text, smsReceiverPhoneText.text,
        smsParsingRegex.text, useMultiRegex, smsIncomeRegex.text, smsExpenseRegex.text, smsAmountRegex.text,
        smsDateTimeRegex.text, smsDescriptionRegex.text, smsBalanceRegex.text, smsCaptureLocation,
    )
    val savedFormValues = remember(modal) { formValues }
    val hasUnsavedChanges = formValues != savedFormValues

    val context = LocalContext.current
    val nav = navigation()
    val forceNonZeroBalance = modal?.forceNonZeroBalance ?: false

    var hasSmsLocationAccess by remember(modal) {
        mutableStateOf(SmsLocationAccess.hasFullAccess(context))
    }
    // Android 11+ only grants background location on its own, after foreground location.
    val backgroundLocationLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { granted ->
            hasSmsLocationAccess = SmsLocationAccess.hasFullAccess(context)
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
            hasSmsLocationAccess = SmsLocationAccess.hasFullAccess(context)
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
    val requestSmsLocationAccess: () -> Unit = {
        when {
            !SmsLocationAccess.hasForeground(context) ->
                foregroundLocationLauncher.launch(SmsLocationAccess.foregroundPermissions)

            !SmsLocationAccess.hasBackground(context) ->
                backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            else -> hasSmsLocationAccess = true
        }
    }

    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact(),
        onResult = { uri ->
            uri?.let {
                val name = getContactNameFromUri(context, it)
                if (name != null) {
                    smsSenderPhone = TextFieldValue(name)
                }
            }
        }
    )

    var showAfterSmsAccess by remember { mutableStateOf<() -> Unit>({}) }
    val smsAccessLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = {
            if (context.hasPermission(Manifest.permission.READ_SMS)) {
                showAfterSmsAccess()
            } else {
                android.widget.Toast.makeText(
                    context,
                    "Allow SMS access to pick a message",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            }
        }
    )
    // Only READ_SMS is required; the other permissions add detail to the SMS list.
    val withSmsAccess: (permissions: List<String>, show: () -> Unit) -> Unit = { permissions, show ->
        val missing = permissions.filterNot { context.hasPermission(it) }
        if (missing.isEmpty()) {
            show()
        } else {
            showAfterSmsAccess = show
            smsAccessLauncher.launch(missing.toTypedArray())
        }
    }
    val openSmsTester = {
        // Contacts lets the tester match a sender saved as a contact name.
        withSmsAccess(listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS)) {
            smsTesterVisible = true
        }
    }
    val openSmsSamplePicker = {
        // Contacts and Phone let the list show contact and SIM names, and fill in the SIM's number.
        withSmsAccess(listOf(Manifest.permission.READ_SMS, Manifest.permission.READ_CONTACTS) + simPermissions) {
            smsSamplePickerVisible = true
        }
    }

    val selectSim: (Int?) -> Unit = { subId ->
        val simChanged = subId != smsSubscriptionIdText.text.toIntOrNull()
        smsSubscriptionIdText = TextFieldValue(subId?.toString() ?: "")
        val phone = subId?.let { getSimPhoneNumber(context, it) }
        if (phone.isNotNullOrBlank()) {
            smsReceiverPhoneText = TextFieldValue(phone!!)
        } else if (simChanged) {
            // Keeping the previous SIM's number could make the receiver reject SMS on this one.
            smsReceiverPhoneText = TextFieldValue("")
        }
    }

    val smsConfigPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                try {
                    context.contentResolver.openInputStream(it)?.use { stream ->
                        val jsonStr = stream.bufferedReader().readText()
                        val config = json.decodeFromString<SmsRegexConfig>(jsonStr)
                        smsParsingRegex = TextFieldValue(config.masterMatch ?: "")
                        useMultiRegex = config.useMultiRegex
                        smsIncomeRegex = TextFieldValue(config.incomeRegex ?: "")
                        smsExpenseRegex = TextFieldValue(config.expenseRegex ?: "")
                        smsAmountRegex = TextFieldValue(config.amountRegex ?: "")
                        smsDateTimeRegex = TextFieldValue(config.dateTimeRegex ?: "")
                        smsDescriptionRegex = TextFieldValue(config.descriptionRegex ?: "")
                        smsBalanceRegex = TextFieldValue(config.balanceRegex ?: "")
                        android.widget.Toast.makeText(context, "Config imported successfully!", android.widget.Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                    android.widget.Toast.makeText(context, "Failed to import config: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    )

    IvyModal(
        id = modal?.id,
        visible = modal != null,
        dismiss = dismiss,
        shiftIfKeyboardShown = false,
        PrimaryAction = {
            ModalAddSave(
                item = modal?.account,
                enabled = nameTextFieldValue.text.isNotNullOrBlank() && (!forceNonZeroBalance || amount > 0)
            ) {
                save(
                    account = account,
                    nameTextFieldValue = nameTextFieldValue,
                    currency = currencyCode,
                    color = color,
                    icon = icon,
                    amount = amount,
                    includeInBalance = includeInBalance,
                    smsAutoListenEnabled = smsAutoListenEnabled,
                    smsSenderPhone = smsSenderPhone.text,
                    smsSubscriptionId = smsSubscriptionIdText.text.toIntOrNull(),
                    smsReceiverPhone = smsReceiverPhoneText.text,
                    smsParsingRegex = smsParsingRegex.text,
                    useMultiRegex = useMultiRegex,
                    smsIncomeRegex = smsIncomeRegex.text,
                    smsExpenseRegex = smsExpenseRegex.text,
                    smsAmountRegex = smsAmountRegex.text,
                    smsDateTimeRegex = smsDateTimeRegex.text,
                    smsDescriptionRegex = smsDescriptionRegex.text,
                    smsBalanceRegex = smsBalanceRegex.text,
                    smsCaptureLocation = smsCaptureLocation,

                    onCreateAccount = onCreateAccount,
                    onEditAccount = onEditAccount,
                    dismiss = dismiss
                )
            }
        }
    ) {
        onScreenStart {
            if (modal?.adjustBalanceMode == true) {
                amountModalVisible = true
            }
        }

        Spacer(Modifier.height(32.dp))

        ModalTitle(
            text = if (modal?.account != null) {
                stringResource(
                    R.string.edit_account
                )
            } else {
                stringResource(R.string.new_account)
            },
        )

        Spacer(Modifier.height(24.dp))

        IconNameRow(
            hint = stringResource(R.string.account_name),
            defaultIcon = R.drawable.ic_custom_account_m,
            color = color,
            icon = icon,

            autoFocusKeyboard = modal?.autoFocusKeyboard ?: true,

            nameTextFieldValue = nameTextFieldValue,
            setNameTextFieldValue = { nameTextFieldValue = it },
            showChooseIconModal = {
                chooseIconModalVisible = true
            }
        )

        Spacer(Modifier.height(24.dp))

        IvyColorPicker(
            selectedColor = color,
            onColorSelected = { color = it }
        )

        Spacer(modifier = Modifier.height(24.dp))

        Row(
            modifier = Modifier.fillMaxWidth().padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IvyCheckboxWithText(
                modifier = Modifier.padding(start = 16.dp),
                text = "Auto Listen to SMS",
                checked = smsAutoListenEnabled
            ) {
                smsAutoListenEnabled = it
            }

            if (smsAutoListenEnabled) {
                Text(
                    modifier = Modifier
                        .clip(UI.shapes.rFull)
                        .clickable { openSmsSamplePicker() }
                        .padding(8.dp),
                    text = "SAMPLE SMS",
                    style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
                )
            }
        }

        if (smsAutoListenEnabled) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                contentAlignment = Alignment.CenterEnd
            ) {
                IvyOutlinedTextField(
                    modifier = Modifier.fillMaxWidth(),
                    value = smsSenderPhone,
                    hint = "Sender Name or Number",
                    onValueChanged = { smsSenderPhone = it }
                )
                Text(
                    modifier = Modifier
                        .padding(end = 24.dp)
                        .clickable { contactPickerLauncher.launch(null) },
                    text = "PICK",
                    style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (manualSimEntry) {
                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsSubscriptionIdText,
                    hint = "SIM Slot (Subscription ID)",
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    onValueChanged = { smsSubscriptionIdText = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsReceiverPhoneText,
                    hint = "Receiving Phone Number (SIM)",
                    onValueChanged = { smsReceiverPhoneText = it }
                )
                Text(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
                    text = "Used to verify that the SMS arrived on the correct SIM.",
                    style = UI.typo.c.style(color = Gray)
                )
                Text(
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clickable { manualSimEntry = false }
                        .align(Alignment.CenterHorizontally),
                    text = "Switch to Auto Selection",
                    style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.Bold)
                )
            } else {
                SimSubscriptionPicker(
                    subId = smsSubscriptionIdText.text.toIntOrNull(),
                    receiverPhone = smsReceiverPhoneText.text,
                    onSubIdChanged = selectSim,
                    onManualEntryClick = { manualSimEntry = true }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            IvyOutlinedTextField(
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                value = smsParsingRegex,
                hint = if (useMultiRegex) "Master Match Regex" else "SMS Parsing Regex",
                onValueChanged = { smsParsingRegex = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth().padding(end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IvyCheckboxWithText(
                    modifier = Modifier
                        .padding(start = 16.dp),
                    text = "Use Advanced Multi-Regex",
                    checked = useMultiRegex
                ) {
                    useMultiRegex = it
                }

                Text(
                    modifier = Modifier.clickable { smsConfigPickerLauncher.launch(arrayOf("application/json", "text/*")) },
                    text = "IMPORT CONFIG",
                    style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
                )
            }

            if (useMultiRegex) {
                Spacer(modifier = Modifier.height(16.dp))

                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsIncomeRegex,
                    hint = "Income Identification Regex",
                    onValueChanged = { smsIncomeRegex = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsExpenseRegex,
                    hint = "Expense Identification Regex",
                    onValueChanged = { smsExpenseRegex = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsAmountRegex,
                    hint = "Amount Extraction Regex",
                    onValueChanged = { smsAmountRegex = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsDateTimeRegex,
                    hint = "Date/Time Extraction Regex",
                    onValueChanged = { smsDateTimeRegex = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsDescriptionRegex,
                    hint = "Description Extraction Regex",
                    onValueChanged = { smsDescriptionRegex = it }
                )

                Spacer(modifier = Modifier.height(16.dp))

                IvyOutlinedTextField(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    value = smsBalanceRegex,
                    hint = "Balance Extraction Regex",
                    onValueChanged = { smsBalanceRegex = it }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            IvyCheckboxWithText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.Start),
                text = "Capture Location",
                checked = smsCaptureLocation
            ) {
                smsCaptureLocation = it
                if (it && !hasSmsLocationAccess) requestSmsLocationAccess()
            }
            if (smsCaptureLocation) {
                Text(
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 4.dp),
                    text = if (hasSmsLocationAccess) {
                        "Saves where your phone was when the SMS arrived."
                    } else {
                        "Saves where your phone was when the SMS arrived. " +
                            "Needs location access set to \"Allow all the time\"."
                    },
                    style = UI.typo.c.style(color = Gray)
                )
                if (!hasSmsLocationAccess) {
                    Text(
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.CenterHorizontally)
                            .clip(UI.shapes.rFull)
                            .clickable { requestSmsLocationAccess() }
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        text = "ALLOW LOCATION ACCESS",
                        style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clip(UI.shapes.rFull)
                    .clickable { openSmsTester() }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                text = "TEST WITH AN SMS",
                style = UI.typo.c.style(color = UI.colors.primary, fontWeight = FontWeight.ExtraBold)
            )
        }

        Spacer(modifier = Modifier.height(40.dp))

        ModalAmountSection(
            Header = {
                Spacer(Modifier.height(16.dp))

                AccountCurrency(
                    currencyCode = currencyCode
                ) {
                    currencyModalVisible = true
                }

                Spacer(modifier = Modifier.height(16.dp))

                IvyCheckboxWithText(
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .align(Alignment.Start),
                    text = stringResource(R.string.include_account),
                    checked = includeInBalance
                ) {
                    includeInBalance = it
                }
            },
            label = stringResource(R.string.enter_account_balance).uppercase(),
            currency = currencyCode,
            amount = amount,
            amountPaddingTop = 40.dp,
            amountPaddingBottom = 40.dp,
        ) {
            amountModalVisible = true
        }
    }

    val amountModalId = remember(modal, amount) {
        UUID.randomUUID()
    }
    AmountModal(
        id = amountModalId,
        visible = amountModalVisible,
        currency = currencyCode,
        initialAmount = amount,
        showPlusMinus = true,
        dismiss = { amountModalVisible = false }
    ) { newAmount ->
        amount = newAmount

        if (modal?.adjustBalanceMode == true) {
            save(
                account = account,
                nameTextFieldValue = nameTextFieldValue,
                currency = currencyCode,
                color = color,
                icon = icon,
                amount = newAmount,
                includeInBalance = includeInBalance,
                smsAutoListenEnabled = smsAutoListenEnabled,
                smsSenderPhone = smsSenderPhone.text,
                smsSubscriptionId = smsSubscriptionIdText.text.toIntOrNull(),
                smsReceiverPhone = smsReceiverPhoneText.text,
                smsParsingRegex = smsParsingRegex.text,
                useMultiRegex = useMultiRegex,
                smsIncomeRegex = smsIncomeRegex.text,
                smsExpenseRegex = smsExpenseRegex.text,
                smsAmountRegex = smsAmountRegex.text,
                smsDateTimeRegex = smsDateTimeRegex.text,
                smsDescriptionRegex = smsDescriptionRegex.text,
                smsBalanceRegex = smsBalanceRegex.text,
                smsCaptureLocation = smsCaptureLocation,

                onCreateAccount = onCreateAccount,
                onEditAccount = onEditAccount,
                dismiss = dismiss
            )
        }
    }

    CurrencyModal(
        title = stringResource(R.string.choose_currency),
        initialCurrency = IvyCurrency.fromCode(currencyCode),
        visible = currencyModalVisible,
        dismiss = { currencyModalVisible = false }
    ) {
        currencyCode = it

//        if (IvyCurrency.fromCode(it)?.isCrypto == true) {
//            if (getCustomIconId(context = context, iconName = it, size = "m") != null) {
//                icon = it
//            }
//        }
    }

    ChooseIconModal(
        visible = chooseIconModalVisible,
        initialIcon = icon ?: "account",
        color = color,
        dismiss = { chooseIconModalVisible = false }
    ) {
        icon = it
    }

    // Leaves for a new, unsaved transaction filled in from the tested SMS.
    fun addSmsTransaction(savedAccount: Account, transaction: ParsedSmsTransaction) {
        if (hasUnsavedChanges) {
            // Opening another screen closes this form, which would silently drop the edits.
            android.widget.Toast.makeText(
                context,
                "Save the account first: opening a new transaction closes this form " +
                    "and your unsaved changes would be lost.",
                android.widget.Toast.LENGTH_LONG
            ).show()
            return
        }
        smsTesterVisible = false
        dismiss()
        nav.navigateTo(
            EditTransactionScreen(
                initialTransactionId = null,
                type = transaction.type,
                accountId = savedAccount.id,
                amount = transaction.amount,
                title = transaction.title,
                description = transaction.description,
                dateTime = transaction.dateTimeMillis,
                // Same tag the SMS receiver gives the transactions it saves.
                tagIds = listOfNotNull(savedAccount.autoTagId),
            )
        )
    }

    SmsTesterModal(
        visible = smsTesterVisible,
        sessionKey = modal,
        rules = SmsParsingRules(
            masterRegex = smsParsingRegex.text,
            useMultiRegex = useMultiRegex,
            incomeRegex = smsIncomeRegex.text,
            expenseRegex = smsExpenseRegex.text,
            amountRegex = smsAmountRegex.text,
            dateTimeRegex = smsDateTimeRegex.text,
            descriptionRegex = smsDescriptionRegex.text,
            balanceRegex = smsBalanceRegex.text,
        ),
        senderFilter = smsSenderPhone.text,
        subscriptionId = smsSubscriptionIdText.text.toIntOrNull(),
        accountName = nameTextFieldValue.text.trim(),
        // Only a saved account can be given the transaction.
        onAddTransaction = account?.let { savedAccount -> { addSmsTransaction(savedAccount, it) } },
        dismiss = { smsTesterVisible = false }
    )

    SmsSamplePickerModal(
        visible = smsSamplePickerVisible,
        senderFilter = smsSenderPhone.text,
        dismiss = { smsSamplePickerVisible = false },
        onPick = { sender, subscriptionId ->
            smsSenderPhone = TextFieldValue(sender)
            if (subscriptionId != null) selectSim(subscriptionId)
            android.widget.Toast.makeText(
                context,
                if (subscriptionId != null) {
                    "Sender and SIM copied from the SMS"
                } else {
                    "Sender copied. The phone didn't record this SMS's SIM, so the SIM is unchanged."
                },
                android.widget.Toast.LENGTH_LONG
            ).show()
        }
    )
}

private fun Context.hasPermission(permission: String): Boolean =
    ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

private fun getContactNameFromUri(context: Context, contactUri: Uri): String? {
    return try {
        val projection = arrayOf(ContactsContract.Contacts.DISPLAY_NAME)
        context.contentResolver.query(contactUri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(0)
            } else null
        }
    } catch (_: Exception) {
        null
    }
}

// READ_PHONE_STATE lists the SIMs; READ_PHONE_NUMBERS lets Android tell us each SIM's number.
private val simPermissions = listOf(
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
    onSubIdChanged: (Int?) -> Unit,
    onManualEntryClick: () -> Unit
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

    Box(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
    ) {
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
                        onSubIdChanged(info.subscriptionId)
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
                    onSubIdChanged(null)
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

private fun save(
    account: Account?,
    nameTextFieldValue: TextFieldValue,
    currency: String,
    color: Color,
    icon: String?,
    amount: Double,
    includeInBalance: Boolean,
    smsAutoListenEnabled: Boolean,
    smsSenderPhone: String?,
    smsSubscriptionId: Int?,
    smsReceiverPhone: String?,
    smsParsingRegex: String?,
    useMultiRegex: Boolean,
    smsIncomeRegex: String?,
    smsExpenseRegex: String?,
    smsAmountRegex: String?,
    smsDateTimeRegex: String?,
    smsDescriptionRegex: String?,
    smsBalanceRegex: String?,
    smsCaptureLocation: Boolean,

    onCreateAccount: (CreateAccountData) -> Unit,
    onEditAccount: (Account, balance: Double) -> Unit,
    dismiss: () -> Unit
) {
    if (account != null) {
        onEditAccount(
            account.copy(
                name = nameTextFieldValue.text.trim(),
                currency = currency,
                includeInBalance = includeInBalance,
                icon = icon,
                color = color.toArgb(),
                smsAutoListenEnabled = smsAutoListenEnabled,
                smsSenderPhone = smsSenderPhone,
                smsSubscriptionId = smsSubscriptionId,
                smsReceiverPhone = smsReceiverPhone,
                smsParsingRegex = smsParsingRegex,
                useMultiRegex = useMultiRegex,
                smsIncomeRegex = smsIncomeRegex,
                smsExpenseRegex = smsExpenseRegex,
                smsAmountRegex = smsAmountRegex,
                smsDateTimeRegex = smsDateTimeRegex,
                smsDescriptionRegex = smsDescriptionRegex,
                smsBalanceRegex = smsBalanceRegex,
                smsCaptureLocation = smsCaptureLocation
            ),
            amount
        )
    } else {
        onCreateAccount(
            CreateAccountData(
                name = nameTextFieldValue.text.trim(),
                currency = currency,
                color = color,
                icon = icon,
                balance = amount,
                includeBalance = includeInBalance,
                smsAutoListenEnabled = smsAutoListenEnabled,
                smsSenderPhone = smsSenderPhone,
                smsSubscriptionId = smsSubscriptionId,
                smsReceiverPhone = smsReceiverPhone,
                smsParsingRegex = smsParsingRegex,
                useMultiRegex = useMultiRegex,
                smsIncomeRegex = smsIncomeRegex,
                smsExpenseRegex = smsExpenseRegex,
                smsAmountRegex = smsAmountRegex,
                smsDateTimeRegex = smsDateTimeRegex,
                smsDescriptionRegex = smsDescriptionRegex,
                smsBalanceRegex = smsBalanceRegex,
                smsCaptureLocation = smsCaptureLocation
            )
        )
    }

    dismiss()
}

@Composable
private fun AccountCurrency(
    currencyCode: String,

    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .background(UI.colors.medium, UI.shapes.r4)
            .clip(UI.shapes.r4)
            .clickable {
                onClick()
            }
            .padding(vertical = 24.dp)
            .testTag("account_modal_currency"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(32.dp))

        Text(
            text = currencyCode.toUpperCaseLocal(),
            style = UI.typo.b1.style(
                fontWeight = FontWeight.ExtraBold
            )
        )

        Spacer(Modifier.weight(1f))

        val currencyName = IvyCurrency.fromCode(currencyCode)?.name ?: ""
        Text(
            text = "-$currencyName".toLowerCaseLocal(),
            style = UI.typo.b2.style(
                fontWeight = FontWeight.SemiBold,
                color = Gray
            )
        )

        Spacer(Modifier.width(24.dp))
    }
}

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview {
        AccountModal(
            modal = AccountModalData(
                account = null,
                baseCurrency = "BGN",
                balance = 0.0
            ),
            onCreateAccount = { },
            onEditAccount = { _, _ -> }
        ) {
        }
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
    val balanceRegex: String? = null
)
