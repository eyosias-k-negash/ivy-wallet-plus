package com.ivy.wallet.domain.action.transaction

import com.ivy.base.legacy.TransactionHistoryItem
import com.ivy.domain.usecase.wallet.ExcludedStatsTagsUseCase
import com.ivy.frp.action.FPAction
import com.ivy.frp.then
import com.ivy.wallet.domain.pure.data.ClosedTimeRange
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import javax.inject.Inject

class HistoryWithDateDivsAct @Inject constructor(
    private val historyTrnsAct: HistoryTrnsAct,
    private val trnsWithDateDivsAct: TrnsWithDateDivsAct,
    private val excludedStatsTagsUseCase: ExcludedStatsTagsUseCase
) : FPAction<HistoryWithDateDivsAct.Input, ImmutableList<TransactionHistoryItem>>() {

    override suspend fun Input.compose(): suspend () -> ImmutableList<TransactionHistoryItem> =
        suspend {
            range
        } then historyTrnsAct then { trns ->
            TrnsWithDateDivsAct.Input(
                baseCurrency = baseCurrency,
                transactions = trns,
                excludedFromStats = excludedStatsTagsUseCase.excludedTransactionIds()
            )
        } then trnsWithDateDivsAct then { it.toImmutableList() }

    data class Input(
        val range: ClosedTimeRange,
        val baseCurrency: String
    )
}
