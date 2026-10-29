package com.ivy.domain.legacy.ui.theme.components

import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.utils.thenIf
import com.ivy.wallet.ui.theme.components.ItemIconSDefaultIcon
import com.ivy.wallet.ui.theme.findContrastTextColor

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun ListItem(
    icon: String?,
    @DrawableRes defaultIcon: Int,
    text: String,
    selectedColor: Color?,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: (selected: Boolean) -> Unit
) {
    val textColor =
        if (selectedColor != null) findContrastTextColor(selectedColor) else UI.colors.pureInverse

    val medium = UI.colors.medium
    val rFull = UI.shapes.rFull

    val startEndPadding = if (compact) 8.dp else 12.dp
    val textPaddingVertical = if (compact) 5.dp else 10.dp
    val trailingPadding = if (compact) 10.dp else 24.dp

    Row(
        modifier = modifier
            .clip(UI.shapes.rFull)
            .thenIf(selectedColor == null) {
                border(if (compact) 1.5.dp else 2.dp, medium, rFull)
            }
            .thenIf(selectedColor != null) {
                background(selectedColor!!, rFull)
            }
            .clickable(
                onClick = {
                    onClick(selectedColor != null)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(startEndPadding))

        ItemIconSDefaultIcon(
            iconName = icon,
            defaultIcon = defaultIcon,
            tint = textColor
        )

        Spacer(Modifier.width(4.dp))

        Text(
            modifier = Modifier.padding(vertical = textPaddingVertical),
            text = text,
            style = (if (compact) UI.typo.c else UI.typo.b2).style(
                color = textColor,
                fontWeight = FontWeight.Bold
            )
        )

        Spacer(Modifier.width(trailingPadding))
    }

    if (!compact) {
        Spacer(Modifier.width(12.dp))
    }
}
