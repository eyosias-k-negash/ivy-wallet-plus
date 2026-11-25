package com.ivy.settings

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ivy.base.legacy.Theme
import com.ivy.data.attachment.AttachmentType
import com.ivy.data.attachment.AttachmentUsage
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.l1_buildingBlocks.IconScale
import com.ivy.design.l1_buildingBlocks.IvyIconScaled
import com.ivy.legacy.IvyWalletPreview
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.GradientRed
import com.ivy.wallet.ui.theme.Gray
import com.ivy.wallet.ui.theme.components.IvyButton
import com.ivy.wallet.ui.theme.modal.IvyModal
import com.ivy.wallet.ui.theme.modal.ModalTitle
import java.util.UUID

@Composable
internal fun BoxWithConstraintsScope.DiskUsageModal(
    visible: Boolean,
    usage: Map<AttachmentType, AttachmentUsage>,
    dismiss: () -> Unit,
    onPurge: (AttachmentType) -> Unit,
    id: UUID = UUID.randomUUID(),
) {
    IvyModal(
        id = id,
        visible = visible,
        dismiss = dismiss,
        PrimaryAction = { }
    ) {
        Spacer(Modifier.height(32.dp))

        ModalTitle(text = stringResource(R.string.disk_usage))

        Spacer(Modifier.height(12.dp))

        Text(
            modifier = Modifier.padding(horizontal = 32.dp),
            text = stringResource(R.string.disk_usage_modal_description),
            style = UI.typo.b2.style(
                color = Gray,
                fontWeight = FontWeight.Medium
            )
        )

        Spacer(Modifier.height(24.dp))

        AttachmentType.entries.forEach { type ->
            AttachmentUsageRow(
                icon = type.icon(),
                title = type.title(),
                usage = usage[type] ?: AttachmentUsage(files = 0, bytes = 0),
                onPurge = { onPurge(type) }
            )

            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AttachmentUsageRow(
    @DrawableRes icon: Int,
    title: String,
    usage: AttachmentUsage,
    onPurge: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .background(UI.colors.medium, UI.shapes.r4)
            .padding(horizontal = 12.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IvyIconScaled(
            icon = icon,
            tint = UI.colors.pureInverse,
            iconScale = IconScale.M,
            padding = 6.dp
        )

        Spacer(Modifier.width(8.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = UI.typo.b2.style(
                    color = UI.colors.pureInverse,
                    fontWeight = FontWeight.Bold
                )
            )
            Text(
                text = pluralStringResource(
                    R.plurals.attachment_files,
                    usage.files,
                    usage.files,
                    formatFileSize(usage.bytes)
                ),
                style = UI.typo.nB2.style(
                    color = Gray,
                    fontWeight = FontWeight.Normal
                ).copy(fontSize = 14.sp)
            )
        }

        Spacer(Modifier.width(8.dp))

        IvyButton(
            text = stringResource(R.string.purge),
            backgroundGradient = GradientRed,
            iconStart = R.drawable.ic_delete,
            enabled = usage.files > 0,
            hasGlow = false,
            padding = 8.dp,
            iconEdgePadding = 8.dp,
            onClick = onPurge
        )
    }
}

@Composable
private fun AttachmentType.title(): String = when (this) {
    AttachmentType.Image -> stringResource(R.string.images)
    AttachmentType.Audio -> stringResource(R.string.audio_notes)
}

@DrawableRes
private fun AttachmentType.icon(): Int = when (this) {
    AttachmentType.Image -> R.drawable.ic_vue_media_image
    AttachmentType.Audio -> R.drawable.ic_vue_media_microphone
}

/** Formats a size the way Android's storage settings do, such as "12 MB". */
@Composable
internal fun formatFileSize(bytes: Long): String =
    Formatter.formatShortFileSize(LocalContext.current, bytes)

@Preview
@Composable
private fun Preview() {
    IvyWalletPreview(Theme.LIGHT) {
        DiskUsageModal(
            visible = true,
            usage = mapOf(
                AttachmentType.Image to AttachmentUsage(files = 8, bytes = 12_300_000),
                AttachmentType.Audio to AttachmentUsage(files = 0, bytes = 0),
            ),
            dismiss = {},
            onPurge = {}
        )
    }
}
