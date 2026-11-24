package com.ivy.wallet.ui.theme.modal.edit

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.domain.legacy.ui.IvyColorPicker
import com.ivy.legacy.IvyWalletPreview
import com.ivy.legacy.datamodel.Account
import com.ivy.legacy.sms.ParsedSmsTransaction
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

    val sms = remember(modal) { SmsSettingsState(account) }

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
    ) + sms.values()
    val savedFormValues = remember(modal) { formValues }
    val hasUnsavedChanges = formValues != savedFormValues

    val context = LocalContext.current
    val nav = navigation()
    val forceNonZeroBalance = modal?.forceNonZeroBalance ?: false

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
                    sms = sms,

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

        SmsSettings(
            state = sms,
            onSampleSms = openSmsSamplePicker,
            onTestSms = openSmsTester,
        )

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
                sms = sms,

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
        rules = sms.rules(),
        senderFilter = sms.senderPhone.text,
        subscriptionId = sms.subscriptionId,
        accountName = nameTextFieldValue.text.trim(),
        // Only a saved account can be given the transaction.
        onAddTransaction = account?.let { savedAccount -> { addSmsTransaction(savedAccount, it) } },
        dismiss = { smsTesterVisible = false }
    )

    SmsSamplePickerModal(
        visible = smsSamplePickerVisible,
        senderFilter = sms.senderPhone.text,
        subscriptionId = sms.subscriptionId,
        dismiss = { smsSamplePickerVisible = false },
        onPick = { sender, subscriptionId ->
            sms.senderPhone = TextFieldValue(sender)
            if (subscriptionId != null) sms.selectSim(context, subscriptionId)
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

private fun save(
    account: Account?,
    nameTextFieldValue: TextFieldValue,
    currency: String,
    color: Color,
    icon: String?,
    amount: Double,
    includeInBalance: Boolean,
    sms: SmsSettingsState,

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
                smsAutoListenEnabled = sms.autoListenEnabled,
                smsSenderPhone = sms.senderPhone.text,
                smsSubscriptionId = sms.subscriptionId,
                smsReceiverPhone = sms.receiverPhoneText.text,
                smsParsingRegex = sms.masterRegex.text,
                useMultiRegex = sms.useMultiRegex,
                smsIncomeRegex = sms.incomeRegex.text,
                smsExpenseRegex = sms.expenseRegex.text,
                smsAmountRegex = sms.amountRegex.text,
                smsDateTimeRegex = sms.dateTimeRegex.text,
                smsDescriptionRegex = sms.descriptionRegex.text,
                smsBalanceRegex = sms.balanceRegex.text,
                smsFeeRegex = sms.feeRegex.text,
                smsCaptureLocation = sms.captureLocation,
                smsCaptureDate = sms.captureDate,
                smsCaptureFee = sms.captureFee,
                smsCaptureDescription = sms.captureDescription,
                smsCaptureBalance = sms.captureBalance
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
                smsAutoListenEnabled = sms.autoListenEnabled,
                smsSenderPhone = sms.senderPhone.text,
                smsSubscriptionId = sms.subscriptionId,
                smsReceiverPhone = sms.receiverPhoneText.text,
                smsParsingRegex = sms.masterRegex.text,
                useMultiRegex = sms.useMultiRegex,
                smsIncomeRegex = sms.incomeRegex.text,
                smsExpenseRegex = sms.expenseRegex.text,
                smsAmountRegex = sms.amountRegex.text,
                smsDateTimeRegex = sms.dateTimeRegex.text,
                smsDescriptionRegex = sms.descriptionRegex.text,
                smsBalanceRegex = sms.balanceRegex.text,
                smsFeeRegex = sms.feeRegex.text,
                smsCaptureLocation = sms.captureLocation,
                smsCaptureDate = sms.captureDate,
                smsCaptureFee = sms.captureFee,
                smsCaptureDescription = sms.captureDescription,
                smsCaptureBalance = sms.captureBalance
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
