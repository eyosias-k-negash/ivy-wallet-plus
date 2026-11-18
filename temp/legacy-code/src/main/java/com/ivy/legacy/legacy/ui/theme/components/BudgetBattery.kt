package com.ivy.legacy.legacy.ui.theme.components

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.ivy.design.l0_system.UI
import com.ivy.design.l0_system.style
import com.ivy.design.utils.thenIf
import com.ivy.legacy.IvyWalletComponentPreview
import com.ivy.legacy.utils.format
import com.ivy.ui.R
import com.ivy.wallet.ui.theme.Green
import com.ivy.wallet.ui.theme.Ivy
import com.ivy.wallet.ui.theme.Orange
import com.ivy.wallet.ui.theme.Red
import com.ivy.wallet.ui.theme.White
import com.ivy.wallet.ui.theme.components.IvyIcon
import com.ivy.wallet.ui.theme.wallet.AmountCurrencyB2Row
import kotlin.math.abs

@Deprecated("Old design system. Use `:ivy-design` and Material3")
@Composable
fun BudgetBattery(
    modifier: Modifier = Modifier,
    currency: String,
    expenses: Double,
    budget: Double,
    planned: Double = 0.0,
    backgroundNotFilled: Color = UI.colors.pure,
    onClick: (() -> Unit)? = null,
) {
    if (budget == 0.0) return
    val amounts = budgetBatteryAmounts(expenses = expenses, planned = planned, budget = budget)
    val percentSpent = amounts.percentSpent
    val percentCommitted = amounts.percentCommitted

    val textColor = when {
        percentSpent <= 0.30 -> {
            UI.colors.pureInverse
        }

        percentSpent <= 0.50 -> {
            White
        }

        percentSpent <= 0.75 -> {
            White
        }

        else -> White
    }

    val captionTextColor = when {
        percentSpent <= 0.30 -> {
            UI.colors.mediumInverse
        }

        percentSpent <= 0.50 -> {
            White
        }

        percentSpent <= 0.75 -> {
            White
        }

        else -> White
    }

    // Color by spent + planned so the warning shows before the planned payments land
    val fillColor = percentCommitted.toBudgetFillColor()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(UI.shapes.r4)
            .background(backgroundNotFilled)
            .drawBehind {
                // Planned payments sit as a lighter band right after the spent fill
                if (planned > 0) {
                    drawRect(
                        color = fillColor.copy(alpha = PlannedFillAlpha),
                        size = size.copy(
                            width = (size.width * percentCommitted).toFloat()
                        )
                    )
                }
                drawRect(
                    color = fillColor,
                    size = size.copy(
                        width = (size.width * percentSpent).toFloat()
                    )
                )
            }
            .thenIf(onClick != null) {
                clickable {
                    onClick?.invoke()
                }
            }
            .padding(vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.width(16.dp))

        IvyIcon(
            icon = if (percentCommitted > 1.0) R.drawable.ic_buffer_exceeded else R.drawable.ic_buffer_ok,
            tint = textColor
        )

        Spacer(Modifier.width(16.dp))

        Column {
            Text(
                text = stringResource(amounts.headline.textRes()),
                style = UI.typo.c.style(
                    color = textColor,
                    fontWeight = FontWeight.ExtraBold
                )
            )

            Spacer(Modifier.height(4.dp))

            AmountCurrencyB2Row(
                amount = amounts.amount,
                currency = currency,
                textColor = textColor
            )

            Spacer(Modifier.height(2.dp))

            Text(
                text = if (planned > 0) {
                    stringResource(
                        R.string.budget_spent_and_planned,
                        expenses.format(currency),
                        planned.format(currency),
                        budget.format(currency),
                        currency
                    )
                } else {
                    "${expenses.format(currency)}/${budget.format(currency)} $currency"
                },
                style = UI.typo.nC.style(
                    fontWeight = FontWeight.ExtraBold,
                    color = captionTextColor
                )
            )
        }
    }
}

private const val PlannedFillAlpha = 0.35f
private const val GreenFillMaxPercent = 0.25
private const val IvyFillMaxPercent = 0.50
private const val OrangeFillMaxPercent = 0.75

private fun Double.toBudgetFillColor(): Color = when {
    this <= GreenFillMaxPercent -> Green
    this <= IvyFillMaxPercent -> Ivy
    this <= OrangeFillMaxPercent -> Orange
    else -> Red
}

internal enum class BudgetBatteryHeadline {
    LEFT_TO_SPEND,
    FREE_TO_SPEND,
    PLANNED_EXCEEDS_BUDGET,
    BUDGET_EXCEEDED
}

@StringRes
private fun BudgetBatteryHeadline.textRes(): Int = when (this) {
    BudgetBatteryHeadline.LEFT_TO_SPEND -> R.string.left_to_spend
    BudgetBatteryHeadline.FREE_TO_SPEND -> R.string.free_to_spend
    BudgetBatteryHeadline.PLANNED_EXCEEDS_BUDGET -> R.string.planned_exceeds_budget_by
    BudgetBatteryHeadline.BUDGET_EXCEEDED -> R.string.budget_exceeded_by
}

internal data class BudgetBatteryAmounts(
    val headline: BudgetBatteryHeadline,
    val amount: Double,
    val percentSpent: Double,
    val percentCommitted: Double
)

internal fun budgetBatteryAmounts(
    expenses: Double,
    planned: Double,
    budget: Double
): BudgetBatteryAmounts {
    val committed = expenses + planned
    val headline = when {
        expenses > budget -> BudgetBatteryHeadline.BUDGET_EXCEEDED
        committed > budget -> BudgetBatteryHeadline.PLANNED_EXCEEDS_BUDGET
        planned > 0 -> BudgetBatteryHeadline.FREE_TO_SPEND
        else -> BudgetBatteryHeadline.LEFT_TO_SPEND
    }
    return BudgetBatteryAmounts(
        headline = headline,
        // Once actual spending is over, the overspend is the number that matters
        amount = if (headline == BudgetBatteryHeadline.BUDGET_EXCEEDED) {
            abs(budget - expenses)
        } else {
            abs(budget - committed)
        },
        percentSpent = expenses / budget,
        percentCommitted = committed / budget
    )
}

@Preview
@Composable
private fun Preview_budget_0() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                budget = 0.0,
                expenses = 100.45,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_expenses_0() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                budget = 1000.0,
                expenses = 0.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_spent_very_low() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 100000.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_buffer_25() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 20000.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_buffer_50() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 10000.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_buffer_75() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 7500.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_buffer_90() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 5500.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_buffer_100() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 5000.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_buffer_125() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 5000.0,
                budget = 2500.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_planned() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 2000.0,
                planned = 3000.0,
                budget = 10000.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_planned_exceeds_budget() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = 4500.0,
                planned = 6000.0,
                budget = 10000.0,
                currency = "BGN"
            )
        }
    }
}

@Preview
@Composable
private fun Preview_expenses_negative() {
    IvyWalletComponentPreview {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(UI.colors.medium),
            contentAlignment = Alignment.Center
        ) {
            BudgetBattery(
                modifier = Modifier.padding(horizontal = 32.dp),
                expenses = -348.54,
                budget = 1000.0,
                currency = "BGN"
            )
        }
    }
}
