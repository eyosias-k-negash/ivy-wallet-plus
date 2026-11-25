package com.ivy.features

import com.ivy.data.model.TagId

sealed interface FeaturesUiEvent {
    data class ToggleFeature(val key: String) : FeaturesUiEvent

    @Suppress("DataClassTypedIDs")
    data class SetTagExclusion(val tagId: TagId, val exclude: Boolean) : FeaturesUiEvent
}
