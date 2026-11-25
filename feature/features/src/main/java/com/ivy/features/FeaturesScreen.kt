package com.ivy.features

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ivy.data.model.TagId
import com.ivy.design.system.colors.IvyColors.Gray
import com.ivy.navigation.navigation
import com.ivy.navigation.screenScopedViewModel
import kotlinx.collections.immutable.ImmutableList

@Composable
fun FeaturesScreenImpl(
    viewModel: FeaturesViewModel = screenScopedViewModel()
) {
    FeaturesUi(
        uiState = viewModel.uiState(),
        onEvent = viewModel::onEvent
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeaturesUi(
    uiState: FeaturesUiState,
    onEvent: (FeaturesUiEvent) -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Title()
                },
                navigationIcon = {
                    BackButton()
                },
            )
        },
        content = { innerPadding ->
            Content(
                modifier = Modifier.padding(innerPadding),
                items = uiState.featureItemViewStates,
                statsTags = uiState.statsTags,
                onToggleFeature = {
                    onEvent(FeaturesUiEvent.ToggleFeature(it))
                },
                onSetTagExclusion = { tagId, exclude ->
                    onEvent(FeaturesUiEvent.SetTagExclusion(tagId, exclude))
                }
            )
        }
    )
}

@Composable
private fun BackButton(
    modifier: Modifier = Modifier,
) {
    val nav = navigation()
    IconButton(
        modifier = modifier,
        onClick = {
            nav.back()
        }
    ) {
        Icon(
            imageVector = Icons.Filled.ArrowBack,
            contentDescription = "Back"
        )
    }
}

@Composable
private fun Title(
    modifier: Modifier = Modifier,
) {
    Text(
        modifier = modifier,
        text = "Features",
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

@Composable
private fun Content(
    items: ImmutableList<FeatureItemViewState>,
    statsTags: ImmutableList<StatsTagViewState>,
    onToggleFeature: (String) -> Unit,
    onSetTagExclusion: (tagId: TagId, exclude: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var tagPickerVisible by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(
            horizontal = 16.dp,
            vertical = 12.dp
        ),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(items) { item ->
            when (item) {
                is FeatureItemViewState.FeatureHeaderViewState -> {
                    FeatureSectionDivider(text = item.name)
                }

                is FeatureItemViewState.FeatureToggleViewState -> {
                    FeatureRow(
                        feature = item,
                        onToggleClick = { onToggleFeature(item.key) }
                    )
                }
            }
        }

        item {
            FeatureSectionDivider(text = "Income and expense")
        }
        item {
            ExcludedTagsRow(
                tags = statsTags,
                onClick = { tagPickerVisible = true }
            )
        }
    }

    if (tagPickerVisible) {
        ExcludedTagsDialog(
            tags = statsTags,
            onSetTagExclusion = onSetTagExclusion,
            onDismiss = { tagPickerVisible = false }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExcludedTagsRow(
    tags: ImmutableList<StatsTagViewState>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        onClick = onClick,
    ) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = "Exclude tags from income and expense",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W700
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Transactions with these tags stay in your history and balances, " +
                    "but don't count toward the Home screen's income, expenses and daily totals",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(modifier = Modifier.height(12.dp))
            val excludedTags = tags.filter { it.excluded }
            if (excludedTags.isEmpty()) {
                Text(
                    text = "No tags excluded. Tap to choose.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline
                )
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    excludedTags.forEach { TagChip(tag = it) }
                }
            }
        }
    }
}

@Composable
private fun TagChip(
    tag: StatsTagViewState,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface,
    ) {
        Text(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            text = tag.name,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun ExcludedTagsDialog(
    tags: ImmutableList<StatsTagViewState>,
    onSetTagExclusion: (tagId: TagId, exclude: Boolean) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = "Done")
            }
        },
        title = {
            Text(text = "Exclude tags")
        },
        text = {
            if (tags.isEmpty()) {
                Text(
                    text = "You don't have any tags yet. Add tags to your transactions, " +
                        "then pick them here."
                )
            } else {
                LazyColumn(Modifier.heightIn(max = 400.dp)) {
                    items(tags, key = { it.id.value.toString() }) { tag ->
                        ExcludedTagOption(
                            tag = tag,
                            onCheckedChange = { onSetTagExclusion(tag.id, it) }
                        )
                    }
                }
            }
        }
    )
}

@Composable
private fun ExcludedTagOption(
    tag: StatsTagViewState,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable { onCheckedChange(!tag.excluded) },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = tag.excluded, onCheckedChange = onCheckedChange)
        Text(
            text = tag.name,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun FeatureRow(
    feature: FeatureItemViewState.FeatureToggleViewState,
    onToggleClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxSize(),
        onClick = onToggleClick,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = feature.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W700
                )
                if (feature.description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = feature.description,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = feature.enabled, onCheckedChange = { onToggleClick() })
        }
    }
}

@Composable
private fun FeatureSectionDivider(
    text: String,
    color: Color = Gray
) {
    Column {
        Spacer(Modifier.height(16.dp))

        Text(
            modifier = Modifier.padding(start = 16.dp),
            text = text,
            style = MaterialTheme.typography.titleMedium.copy(
                color = color,
                fontWeight = FontWeight.Bold
            )
        )
    }
}
