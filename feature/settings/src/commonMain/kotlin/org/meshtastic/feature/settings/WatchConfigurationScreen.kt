/*
 * Copyright (c) 2026 Meshtastic LLC
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.meshtastic.feature.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.watch_settings_title
import org.meshtastic.core.resources.watch_sync_enabled_description
import org.meshtastic.core.resources.watch_sync_enabled_label
import org.meshtastic.core.resources.watch_sync_messages_description
import org.meshtastic.core.resources.watch_sync_messages_label
import org.meshtastic.core.resources.watch_sync_nodes_description
import org.meshtastic.core.resources.watch_sync_nodes_label
import org.meshtastic.core.resources.watch_sync_now_description
import org.meshtastic.core.resources.watch_sync_now_label
import org.meshtastic.core.resources.watch_sync_privacy_description
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh

@Composable
fun WatchConfigurationScreen(viewModel: WatchViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WatchConfigurationScreen(
        state = state,
        onPushToWatchChange = viewModel::setPushToWatchEnabled,
        onSyncNodesChange = viewModel::setSyncNodesEnabled,
        onSyncMessagesChange = viewModel::setSyncMessagesEnabled,
        onSyncNow = viewModel::requestSync,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun WatchConfigurationScreen(
    state: WatchSettingsUiState,
    onPushToWatchChange: (Boolean) -> Unit,
    onSyncNodesChange: (Boolean) -> Unit,
    onSyncMessagesChange: (Boolean) -> Unit,
    onSyncNow: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        topBar = {
            MainAppBar(
                title = stringResource(Res.string.watch_settings_title),
                ourNode = null,
                showNodeChip = false,
                canNavigateUp = true,
                onNavigateUp = onBack,
                actions = {},
                onClickChip = {},
            )
        },
    ) { paddingValues ->
        AdaptiveTwoPane(
            modifier =
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(paddingValues).padding(16.dp),
            first = {
                WatchSyncPreferences(
                    state = state,
                    onPushToWatchChange = onPushToWatchChange,
                    onSyncNodesChange = onSyncNodesChange,
                    onSyncMessagesChange = onSyncMessagesChange,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            },
            second = {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ManualSyncCard(enabled = state.pushToWatchEnabled, onSyncNow = onSyncNow)
                    PrivacyCard()
                }
            },
        )
    }
}

@Composable
private fun WatchSyncPreferences(
    state: WatchSettingsUiState,
    onPushToWatchChange: (Boolean) -> Unit,
    onSyncNodesChange: (Boolean) -> Unit,
    onSyncMessagesChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        SwitchPreference(
            title = stringResource(Res.string.watch_sync_enabled_label),
            summary = stringResource(Res.string.watch_sync_enabled_description),
            checked = state.pushToWatchEnabled,
            enabled = true,
            onCheckedChange = onPushToWatchChange,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SwitchPreference(
            title = stringResource(Res.string.watch_sync_nodes_label),
            summary = stringResource(Res.string.watch_sync_nodes_description),
            checked = state.syncNodesEnabled,
            enabled = state.pushToWatchEnabled,
            onCheckedChange = onSyncNodesChange,
        )
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        SwitchPreference(
            title = stringResource(Res.string.watch_sync_messages_label),
            summary = stringResource(Res.string.watch_sync_messages_description),
            checked = state.syncMessagesEnabled,
            enabled = state.pushToWatchEnabled,
            onCheckedChange = onSyncMessagesChange,
        )
    }
}

@Composable
private fun ManualSyncCard(enabled: Boolean, onSyncNow: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(Res.string.watch_sync_now_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Button(onClick = onSyncNow, enabled = enabled, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                Text(text = stringResource(Res.string.watch_sync_now_label), modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}

@Composable
private fun PrivacyCard() {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(
            text = stringResource(Res.string.watch_sync_privacy_description),
            leadingIcon = MeshtasticIcons.Info,
            trailingIcon = null,
        )
    }
}

@Composable
internal fun WatchSettingsEntry(onNavigate: (Route) -> Unit) {
    ListItem(
        text = stringResource(Res.string.watch_settings_title),
        supportingText = stringResource(Res.string.watch_sync_enabled_description),
        leadingIcon = MeshtasticIcons.AppSettingsAlt,
    ) {
        onNavigate(SettingsRoute.Watch)
    }
}
