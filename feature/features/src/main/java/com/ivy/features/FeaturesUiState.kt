package com.ivy.features

import com.ivy.data.model.TagId
import kotlinx.collections.immutable.ImmutableList

data class FeaturesUiState(
    val featureItemViewStates: ImmutableList<FeatureItemViewState>,
    val statsTags: ImmutableList<StatsTagViewState>,
)

sealed interface FeatureItemViewState {
    data class FeatureToggleViewState(
        val key: String,
        val name: String,
        val enabled: Boolean,
        val description: String?,
    ) : FeatureItemViewState

    data class FeatureHeaderViewState(val name: String) : FeatureItemViewState
}

@Suppress("DataClassTypedIDs")
data class StatsTagViewState(
    val id: TagId,
    val name: String,
    val excluded: Boolean,
)
