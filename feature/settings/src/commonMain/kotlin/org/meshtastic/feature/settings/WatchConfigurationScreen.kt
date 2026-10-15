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

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.navigation.Route
import org.meshtastic.core.navigation.SettingsRoute
import org.meshtastic.core.repository.RecentMessageLimit
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.watch_message_retention_description
import org.meshtastic.core.resources.watch_message_retention_label
import org.meshtastic.core.resources.watch_message_retention_option
import org.meshtastic.core.resources.watch_settings_title
import org.meshtastic.core.resources.watch_sync_channels_description
import org.meshtastic.core.resources.watch_sync_channels_label
import org.meshtastic.core.resources.watch_sync_enabled_description
import org.meshtastic.core.resources.watch_sync_enabled_label
import org.meshtastic.core.resources.watch_sync_messages_description
import org.meshtastic.core.resources.watch_sync_messages_label
import org.meshtastic.core.resources.watch_sync_nodes_description
import org.meshtastic.core.resources.watch_sync_nodes_label
import org.meshtastic.core.resources.watch_sync_privacy_description
import org.meshtastic.core.resources.watch_sync_quick_replies_description
import org.meshtastic.core.resources.watch_sync_quick_replies_label
import org.meshtastic.core.resources.watch_sync_radio_status_description
import org.meshtastic.core.resources.watch_sync_radio_status_label
import org.meshtastic.core.resources.watch_sync_state_description
import org.meshtastic.core.resources.watch_sync_state_disabled
import org.meshtastic.core.resources.watch_sync_state_enabled
import org.meshtastic.core.resources.watch_sync_what_to_sync
import org.meshtastic.core.ui.component.AdaptiveTwoPane
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.component.MainAppBar
import org.meshtastic.core.ui.component.SwitchPreference
import org.meshtastic.core.ui.icon.AppSettingsAlt
import org.meshtastic.core.ui.icon.Info
import org.meshtastic.core.ui.icon.MeshtasticIcons

@Composable
fun WatchConfigurationScreen(viewModel: WatchViewModel, onBack: () -> Unit, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    WatchConfigurationScreen(
        state = state,
        onWatchConnectivityChange = viewModel::setWatchConnectivityEnabled,
        onSyncNodesChange = viewModel::setSyncNodesEnabled,
        onSyncChannelsChange = viewModel::setSyncChannelsEnabled,
        onSyncRecentMessagesChange = viewModel::setSyncRecentMessagesEnabled,
        onRecentMessageLimitChange = viewModel::setRecentMessageLimit,
        onSyncQuickRepliesChange = viewModel::setSyncQuickRepliesEnabled,
        onSyncRadioStatusChange = viewModel::setSyncRadioStatusEnabled,
        onSyncNow = viewModel::requestSync,
        onBack = onBack,
        modifier = modifier,
    )
}

@Composable
internal fun WatchConfigurationScreen(
    state: WatchSettingsUiState,
    onWatchConnectivityChange: (Boolean) -> Unit,
    onSyncNodesChange: (Boolean) -> Unit,
    onSyncChannelsChange: (Boolean) -> Unit,
    onSyncRecentMessagesChange: (Boolean) -> Unit,
    onRecentMessageLimitChange: (RecentMessageLimit) -> Unit,
    onSyncQuickRepliesChange: (Boolean) -> Unit,
    onSyncRadioStatusChange: (Boolean) -> Unit,
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
                Column(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(bottom = 16.dp)) {
                    ConnectionSection(
                        state = state.connection,
                        connectivityEnabled = state.watchConnectivityEnabled,
                        onSyncNow = onSyncNow,
                    )
                    WatchSyncPreferences(
                        state = state,
                        onWatchConnectivityChange = onWatchConnectivityChange,
                        onSyncNodesChange = onSyncNodesChange,
                        onSyncChannelsChange = onSyncChannelsChange,
                        onSyncRecentMessagesChange = onSyncRecentMessagesChange,
                        onRecentMessageLimitChange = onRecentMessageLimitChange,
                        onSyncQuickRepliesChange = onSyncQuickRepliesChange,
                        onSyncRadioStatusChange = onSyncRadioStatusChange,
                    )
                }
            },
            second = { AnimatedVisibility(visible = state.watchConnectivityEnabled) { PrivacyCard() } },
        )
    }
}

@Composable
@Suppress("LongMethod")
private fun WatchSyncPreferences(
    state: WatchSettingsUiState,
    onWatchConnectivityChange: (Boolean) -> Unit,
    onSyncNodesChange: (Boolean) -> Unit,
    onSyncChannelsChange: (Boolean) -> Unit,
    onSyncRecentMessagesChange: (Boolean) -> Unit,
    onRecentMessageLimitChange: (RecentMessageLimit) -> Unit,
    onSyncQuickRepliesChange: (Boolean) -> Unit,
    onSyncRadioStatusChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        ) {
            SwitchPreference(
                title = stringResource(Res.string.watch_sync_enabled_label),
                summary = stringResource(Res.string.watch_sync_enabled_description),
                checked = state.watchConnectivityEnabled,
                enabled = true,
                onCheckedChange = onWatchConnectivityChange,
            )
        }
        AnimatedVisibility(visible = state.watchConnectivityEnabled) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.watch_sync_what_to_sync),
                    style = MaterialTheme.typography.titleMedium,
                )
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                ) {
                    SyncSwitch(
                        title = stringResource(Res.string.watch_sync_nodes_label),
                        summary = stringResource(Res.string.watch_sync_nodes_description),
                        checked = state.syncPreferences.syncNodes,
                        onCheckedChange = onSyncNodesChange,
                    )
                    PreferenceDivider()
                    SyncSwitch(
                        title = stringResource(Res.string.watch_sync_channels_label),
                        summary = stringResource(Res.string.watch_sync_channels_description),
                        checked = state.syncPreferences.syncChannels,
                        onCheckedChange = onSyncChannelsChange,
                    )
                    PreferenceDivider()
                    SyncSwitch(
                        title = stringResource(Res.string.watch_sync_messages_label),
                        summary = stringResource(Res.string.watch_sync_messages_description),
                        checked = state.syncPreferences.syncRecentMessages,
                        onCheckedChange = onSyncRecentMessagesChange,
                    )
                    AnimatedVisibility(visible = state.syncPreferences.syncRecentMessages) {
                        Column {
                            PreferenceDivider()
                            MessageRetentionPreference(
                                selected = state.syncPreferences.recentMessageLimit,
                                onSelect = onRecentMessageLimitChange,
                            )
                        }
                    }
                    PreferenceDivider()
                    SyncSwitch(
                        title = stringResource(Res.string.watch_sync_quick_replies_label),
                        summary = stringResource(Res.string.watch_sync_quick_replies_description),
                        checked = state.syncPreferences.syncQuickReplies,
                        onCheckedChange = onSyncQuickRepliesChange,
                    )
                    PreferenceDivider()
                    SyncSwitch(
                        title = stringResource(Res.string.watch_sync_radio_status_label),
                        summary = stringResource(Res.string.watch_sync_radio_status_description),
                        checked = state.syncPreferences.syncRadioStatus,
                        onCheckedChange = onSyncRadioStatusChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncSwitch(title: String, summary: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val state =
        stringResource(if (checked) Res.string.watch_sync_state_enabled else Res.string.watch_sync_state_disabled)
    val accessibilityState = stringResource(Res.string.watch_sync_state_description, title, state)
    SwitchPreference(
        title = title,
        summary = summary,
        checked = checked,
        enabled = true,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.semantics { stateDescription = accessibilityState },
    )
}

@Composable
private fun MessageRetentionPreference(selected: RecentMessageLimit, onSelect: (RecentMessageLimit) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.watch_message_retention_label))
            Text(
                text = stringResource(Res.string.watch_message_retention_description),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = { expanded = true }) {
            Text(stringResource(Res.string.watch_message_retention_option, selected.count))
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                RecentMessageLimit.entries.forEach { limit ->
                    DropdownMenuItem(
                        text = { Text(stringResource(Res.string.watch_message_retention_option, limit.count)) },
                        onClick = {
                            expanded = false
                            onSelect(limit)
                        },
                    )
                }
            }
        }
    }
}

@Composable private fun PreferenceDivider() = HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

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
