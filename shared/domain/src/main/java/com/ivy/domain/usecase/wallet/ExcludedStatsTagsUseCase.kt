package com.ivy.domain.usecase.wallet

import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.ivy.data.datastore.IvyDataStore
import com.ivy.data.model.TagId
import com.ivy.data.repository.TagRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject

/**
 * Tags whose transactions are left out of the Home screen's income and expense
 * figures: the period totals and each day's cash flow. The transactions still show
 * in the history and still count toward balances.
 */
class ExcludedStatsTagsUseCase @Inject constructor(
    private val dataStore: IvyDataStore,
    private val tagRepository: TagRepository,
) {
    fun tagIdsFlow(): Flow<Set<TagId>> = dataStore.data.map { prefs ->
        prefs[EXCLUDED_TAG_IDS].orEmpty()
            .mapNotNull { runCatching { TagId(UUID.fromString(it)) }.getOrNull() }
            .toSet()
    }

    suspend fun setExcluded(tagId: TagId, excluded: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[EXCLUDED_TAG_IDS].orEmpty()
            val id = tagId.value.toString()
            prefs[EXCLUDED_TAG_IDS] = if (excluded) current + id else current - id
        }
    }

    /** Ids of the transactions that have at least one excluded tag. */
    suspend fun excludedTransactionIds(): Set<UUID> {
        val tagIds = tagIdsFlow().first()
        if (tagIds.isEmpty()) return emptySet()
        return tagRepository.findByAllAssociatedIdForTagId(tagIds.toList())
            .values
            .flatMap { associations -> associations.map { it.associatedId.value } }
            .toSet()
    }

    companion object {
        private val EXCLUDED_TAG_IDS = stringSetPreferencesKey("excluded_stats_tag_ids")
    }
}
