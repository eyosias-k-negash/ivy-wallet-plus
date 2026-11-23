package com.ivy.wallet.ui.theme.modal.edit

import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID

/**
 * Lets the user pick an SMS from the inbox, so the account's sender and SIM can be
 * copied from it instead of being chosen one by one.
 */
@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BoxScope.SmsSamplePickerModal(
    visible: Boolean,
    senderFilter: String,
    dismiss: () -> Unit,
    /** [subscriptionId] is null when the phone didn't record which SIM the SMS came in on. */
    onPick: (sender: String, subscriptionId: Int?) -> Unit,
) {
    val context = LocalContext.current
    val modalId = remember { UUID.randomUUID() }

    var inbox by remember { mutableStateOf<List<InboxSms>?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var onlyFromSender by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    LaunchedEffect(visible) {
        if (!visible) return@LaunchedEffect
        query = TextFieldValue("")
        onlyFromSender = senderFilter.isNotBlank()
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
        PrimaryAction = {},
        scrollState = null,
        includeActionsRowPadding = false,
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(text = "Pick a sample SMS")

        Spacer(Modifier.height(8.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = "Pick an SMS from your bank. Its sender and the SIM it came in on are copied " +
                "into the account.",
            style = UI.typo.c.style(color = UI.colors.gray, fontWeight = FontWeight.Medium)
        )

        Spacer(Modifier.height(16.dp))

        SmsPicker(
            inbox = inbox,
            loadError = loadError,
            query = query,
            onQueryChange = { query = it },
            senderFilter = senderFilter,
            onlyFromSender = onlyFromSender,
            onOnlyFromSenderChange = { onlyFromSender = it },
            listState = listState,
            onSelect = { sms ->
                onPick(sms.address, sms.knownSubscriptionId)
                dismiss()
            }
        )
    }
}
