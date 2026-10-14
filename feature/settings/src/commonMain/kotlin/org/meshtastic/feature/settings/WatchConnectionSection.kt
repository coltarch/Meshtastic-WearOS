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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.nowMillis
import org.meshtastic.core.repository.WatchConnectionError
import org.meshtastic.core.repository.WatchConnectionState
import org.meshtastic.core.repository.WatchSyncStage
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.watch_connected_watch
import org.meshtastic.core.resources.watch_connection_section
import org.meshtastic.core.resources.watch_last_communication
import org.meshtastic.core.resources.watch_multiple_reachable
import org.meshtastic.core.resources.watch_no_communication_yet
import org.meshtastic.core.resources.watch_not_reachable
import org.meshtastic.core.resources.watch_reachable
import org.meshtastic.core.resources.watch_searching_for_watch
import org.meshtastic.core.resources.watch_status
import org.meshtastic.core.resources.watch_status_accessibility
import org.meshtastic.core.resources.watch_status_checking
import org.meshtastic.core.resources.watch_status_connected
import org.meshtastic.core.resources.watch_status_disconnected
import org.meshtastic.core.resources.watch_status_sync_failed
import org.meshtastic.core.resources.watch_status_syncing
import org.meshtastic.core.resources.watch_sync_interrupted
import org.meshtastic.core.resources.watch_sync_not_connected
import org.meshtastic.core.resources.watch_sync_nothing_selected
import org.meshtastic.core.resources.watch_sync_now_description
import org.meshtastic.core.resources.watch_sync_now_label
import org.meshtastic.core.resources.watch_sync_problem
import org.meshtastic.core.resources.watch_sync_problem_transfer
import org.meshtastic.core.resources.watch_sync_progress
import org.meshtastic.core.resources.watch_sync_progress_channels
import org.meshtastic.core.resources.watch_sync_progress_messages
import org.meshtastic.core.resources.watch_sync_progress_nodes
import org.meshtastic.core.resources.watch_sync_progress_quick_replies
import org.meshtastic.core.resources.watch_sync_progress_radio_status
import org.meshtastic.core.resources.watch_sync_retry
import org.meshtastic.core.resources.watch_sync_services_unavailable
import org.meshtastic.core.resources.watch_time_just_now
import org.meshtastic.core.ui.component.ListItem
import org.meshtastic.core.ui.icon.CheckCircle
import org.meshtastic.core.ui.icon.Device
import org.meshtastic.core.ui.icon.Error
import org.meshtastic.core.ui.icon.LinkOff
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.Refresh
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Composable
internal fun ConnectionSection(state: WatchConnectionUiState, connectivityEnabled: Boolean, onSyncNow: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(Res.string.watch_connection_section), style = MaterialTheme.typography.titleMedium)
        WatchConnectionCard(state)
        AnimatedVisibility(visible = connectivityEnabled) {
            ManualSyncCard(state = state, onSyncNow = onSyncNow)
        }
    }
}

@Composable
private fun WatchConnectionCard(state: WatchConnectionUiState) {
    val presentation = watchStatusPresentation(state.connectionState)
    val statusText = stringResource(presentation.label)
    val statusDescription = stringResource(Res.string.watch_status_accessibility, statusText)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        ListItem(
            text = stringResource(Res.string.watch_status),
            modifier = Modifier.semantics { contentDescription = statusDescription },
            supportingText = statusText,
            leadingIcon = presentation.icon,
            leadingIconTint = presentation.tint,
            trailingIcon = null,
        )
        if (state.watchName != null || state.isReachable) {
            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
            ListItem(
                text = state.watchName ?: stringResource(Res.string.watch_connected_watch),
                supportingText =
                if (state.isReachable) {
                    stringResource(Res.string.watch_reachable)
                } else {
                    stringResource(Res.string.watch_not_reachable)
                },
                leadingIcon = MeshtasticIcons.Device,
                trailingIcon = null,
            )
            if (state.reachableWatchCount > 1) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                ListItem(
                    text =
                    pluralStringResource(
                        Res.plurals.watch_multiple_reachable,
                        state.reachableWatchCount,
                        state.reachableWatchCount,
                    ),
                    trailingIcon = null,
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
        ListItem(
            text = stringResource(Res.string.watch_last_communication),
            supportingText =
            state.lastCommunicationMillis?.let { formatWatchTimestamp(it) }
                ?: stringResource(Res.string.watch_no_communication_yet),
            leadingIcon = MeshtasticIcons.CheckCircle,
            trailingIcon = null,
        )
    }
}

@Composable
private fun ManualSyncCard(state: WatchConnectionUiState, onSyncNow: () -> Unit) {
    val hasError = state.error != null
    val containerColor by
        animateColorAsState(
            targetValue =
            if (hasError) {
                MaterialTheme.colorScheme.errorContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
            animationSpec = tween(durationMillis = CARD_COLOR_ANIMATION_MILLIS),
            label = "Sync card color",
        )
    val contentColor =
        if (hasError) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurfaceVariant

    Card(colors = CardDefaults.cardColors(containerColor = containerColor)) {
        Column(
            modifier = Modifier.padding(16.dp).animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (state.error == null) {
                Text(
                    text =
                    stringResource(
                        if (state.nothingSelected) {
                            Res.string.watch_sync_nothing_selected
                        } else {
                            Res.string.watch_sync_now_description
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor,
                )
            } else {
                ListItem(
                    text = stringResource(Res.string.watch_sync_problem),
                    supportingText = syncErrorExplanation(state.error),
                    textColor = contentColor,
                    supportingTextColor = contentColor,
                    leadingIcon = MeshtasticIcons.Error,
                    leadingIconTint = MaterialTheme.colorScheme.error,
                    trailingIcon = null,
                )
            }

            Crossfade(targetState = state.connectionState, label = "Sync status") { connectionState ->
                when (connectionState) {
                    WatchConnectionState.CheckingConnection -> SearchingForWatchIndicator()
                    WatchConnectionState.Syncing -> SyncProgressIndicator(state)
                    else -> Unit
                }
            }

            Button(onClick = onSyncNow, modifier = Modifier.fillMaxWidth(), enabled = state.canSync) {
                Icon(imageVector = MeshtasticIcons.Refresh, contentDescription = null)
                Text(
                    text = stringResource(if (hasError) Res.string.watch_sync_retry else Res.string.watch_sync_now_label),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SearchingForWatchIndicator() {
    val description = stringResource(Res.string.watch_searching_for_watch)
    val transition = rememberInfiniteTransition(label = "Watch search")
    val rotation by
        transition.animateFloat(
            initialValue = 0f,
            targetValue = FULL_ROTATION_DEGREES,
            animationSpec =
            infiniteRepeatable(tween(SEARCH_ROTATION_MILLIS, easing = LinearEasing), RepeatMode.Restart),
            label = "Watch search rotation",
        )

    Column(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = MeshtasticIcons.Refresh,
                contentDescription = null,
                modifier = Modifier.size(24.dp).rotate(rotation),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = description, style = MaterialTheme.typography.bodyMedium)
        }
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun SyncProgressIndicator(state: WatchConnectionUiState) {
    val progress = state.syncProgress
    if (progress == null) {
        val progressDescription = stringResource(Res.string.watch_status_syncing)
        LinearProgressIndicator(
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription },
        )
        return
    }

    val stageText =
        when (progress.stage) {
            WatchSyncStage.Nodes -> stringResource(Res.string.watch_sync_progress_nodes)
            WatchSyncStage.Channels -> stringResource(Res.string.watch_sync_progress_channels)
            WatchSyncStage.Messages -> stringResource(Res.string.watch_sync_progress_messages)
            WatchSyncStage.QuickReplies -> stringResource(Res.string.watch_sync_progress_quick_replies)
            WatchSyncStage.RadioStatus -> stringResource(Res.string.watch_sync_progress_radio_status)
        }
    val progressDescription =
        stringResource(Res.string.watch_sync_progress, stageText, progress.completedStages, progress.totalStages)
    val fraction = progress.completedStages.toFloat() / progress.totalStages.toFloat()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(text = stageText, style = MaterialTheme.typography.bodyMedium)
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = progressDescription },
        )
    }
}

@Composable
private fun syncErrorExplanation(error: WatchConnectionError): String = when (error) {
    WatchConnectionError.NotConnected -> stringResource(Res.string.watch_sync_not_connected)
    WatchConnectionError.WearServicesUnavailable -> stringResource(Res.string.watch_sync_services_unavailable)
    WatchConnectionError.SynchronizationFailed -> stringResource(Res.string.watch_sync_problem_transfer)
    WatchConnectionError.SynchronizationInterrupted -> stringResource(Res.string.watch_sync_interrupted)
}

private const val CARD_COLOR_ANIMATION_MILLIS = 350
private const val SEARCH_ROTATION_MILLIS = 900
private const val FULL_ROTATION_DEGREES = 360f

@Composable
private fun formatWatchTimestamp(timestampMillis: Long): String =
    if ((nowMillis - timestampMillis).coerceAtLeast(0).milliseconds < 1.minutes) {
        stringResource(Res.string.watch_time_just_now)
    } else {
        DateFormatter.formatRelativeTime(timestampMillis)
    }

@Composable
private fun watchStatusPresentation(state: WatchConnectionState): WatchStatusPresentation = when (state) {
    WatchConnectionState.Connected ->
        WatchStatusPresentation(
            label = Res.string.watch_status_connected,
            icon = MeshtasticIcons.CheckCircle,
            tint = MaterialTheme.colorScheme.primary,
        )

    WatchConnectionState.Disconnected ->
        WatchStatusPresentation(
            label = Res.string.watch_status_disconnected,
            icon = MeshtasticIcons.LinkOff,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )

    WatchConnectionState.CheckingConnection ->
        WatchStatusPresentation(
            label = Res.string.watch_status_checking,
            icon = MeshtasticIcons.Refresh,
            tint = MaterialTheme.colorScheme.primary,
        )

    WatchConnectionState.Syncing ->
        WatchStatusPresentation(
            label = Res.string.watch_status_syncing,
            icon = MeshtasticIcons.Refresh,
            tint = MaterialTheme.colorScheme.primary,
        )

    WatchConnectionState.SyncFailed ->
        WatchStatusPresentation(
            label = Res.string.watch_status_sync_failed,
            icon = MeshtasticIcons.Error,
            tint = MaterialTheme.colorScheme.error,
        )
}

private data class WatchStatusPresentation(val label: StringResource, val icon: ImageVector, val tint: Color)
