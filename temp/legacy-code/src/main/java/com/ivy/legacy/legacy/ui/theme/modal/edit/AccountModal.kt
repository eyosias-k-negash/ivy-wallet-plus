package com.ivy.wallet.ui.theme.modal.edit

import android.annotation.SuppressLint
import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
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
import androidx.compose.foundation.layout.Column
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
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.legacy.ui.IvyColorPicker
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.utils.isNotNullOrBlank
import com.ivy.legacy.utils.onScreenStart
import com.ivy.legacy.utils.selectEndTextFieldValue
import com.ivy.legacy.utils.toLowerCaseLocal
import com.ivy.legacy.utils.toUpperCaseLocal
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
import java.util.UUID

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

    var amountModalVisible by remember { mutableStateOf(false) }
    var currencyModalVisible by remember { mutableStateOf(false) }
    var chooseIconModalVisible by remember(modal) {
        mutableStateOf(false)
    }

    val context = LocalContext.current
    val forceNonZeroBalance = modal?.forceNonZeroBalance ?: false

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

        IvyCheckboxWithText(
            modifier = Modifier
                .padding(start = 16.dp)
                .align(Alignment.Start),
            text = "Auto Listen to SMS",
            checked = smsAutoListenEnabled
        ) {
            smsAutoListenEnabled = it
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
                    onSubIdChanged = { subId ->
                        smsSubscriptionIdText = TextFieldValue(subId?.toString() ?: "")
                        if (subId != null) {
                            val phone = getSimPhoneNumber(context, subId)
                            if (phone.isNotNullOrBlank()) {
                                smsReceiverPhoneText = TextFieldValue(phone!!)
                            }
                        } else {
                            smsReceiverPhoneText = TextFieldValue("")
                        }
                    },
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

            IvyCheckboxWithText(
                modifier = Modifier
                    .padding(start = 16.dp)
                    .align(Alignment.Start),
                text = "Use Advanced Multi-Regex",
                checked = useMultiRegex
            ) {
                useMultiRegex = it
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
}

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

@SuppressLint("MissingPermission")
@Composable
private fun SimSubscriptionPicker(
    subId: Int?,
    onSubIdChanged: (Int?) -> Unit,
    onManualEntryClick: () -> Unit
) {
    val context = LocalContext.current
    val subscriptionManager = remember {
        context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
    }
    val activeSubscriptions = remember {
        try {
            subscriptionManager?.activeSubscriptionInfoList ?: emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    var expanded by remember { mutableStateOf(false) }

    val selectedSim = activeSubscriptions.find { it.subscriptionId == subId }
    val displayText = selectedSim?.let { "${it.displayName} (${it.subscriptionId})" }
        ?: if (subId != null) "ID: $subId" else "Select SIM Slot"

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
                .clickable { expanded = true }
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
                val phone = getSimPhoneNumber(LocalContext.current, info.subscriptionId)
                DropdownMenuItem(
                    text = {
                        val subText = if (phone.isNotNullOrBlank()) {
                            "${info.subscriptionId}: ${info.carrierName}|$phone"
                        } else {
                            "${info.subscriptionId}: ${info.carrierName}"
                        }
                        Column {
                            Text(text = info.displayName.toString(), fontWeight = FontWeight.Bold)
                            Text(
                                text = subText,
                                style = UI.typo.c.style(color = Gray)
                            )
                        }
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
                smsBalanceRegex = smsBalanceRegex
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
                smsBalanceRegex = smsBalanceRegex
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
