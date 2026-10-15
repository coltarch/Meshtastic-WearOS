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
@file:Suppress("ModifierMissing")

package org.meshtastic.wear.presentation.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import org.jetbrains.compose.resources.stringResource
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.retry
import org.meshtastic.core.resources.wear_phone_not_linked
import org.meshtastic.core.resources.wear_phone_not_linked_description
import org.meshtastic.core.resources.wear_sync_failed
import org.meshtastic.core.resources.wear_sync_failed_description
import org.meshtastic.core.resources.wear_sync_in_progress
import org.meshtastic.core.resources.wear_sync_now
import org.meshtastic.core.resources.wear_sync_retrying
import org.meshtastic.core.resources.wear_waiting_first_sync
import org.meshtastic.core.resources.wear_waiting_first_sync_description
import org.meshtastic.wear.data.WearCompanionSyncState

@Composable
internal fun CompanionSyncStatus(state: WearCompanionSyncState, onRetry: () -> Unit) {
    val title =
        when (state) {
            WearCompanionSyncState.Loading -> stringResource(Res.string.wear_sync_in_progress)
            WearCompanionSyncState.Retrying -> stringResource(Res.string.wear_sync_retrying)
            WearCompanionSyncState.PhoneUnavailable -> stringResource(Res.string.wear_phone_not_linked)
            WearCompanionSyncState.WaitingForFirstSync -> stringResource(Res.string.wear_waiting_first_sync)
            WearCompanionSyncState.SyncFailed -> stringResource(Res.string.wear_sync_failed)
            WearCompanionSyncState.Ready -> return
        }
    val description =
        when (state) {
            WearCompanionSyncState.PhoneUnavailable -> stringResource(Res.string.wear_phone_not_linked_description)
            WearCompanionSyncState.WaitingForFirstSync -> stringResource(Res.string.wear_waiting_first_sync_description)
            WearCompanionSyncState.SyncFailed -> stringResource(Res.string.wear_sync_failed_description)
            else -> null
        }
    val action =
        when (state) {
            WearCompanionSyncState.PhoneUnavailable,
            WearCompanionSyncState.SyncFailed,
            -> stringResource(Res.string.retry)

            WearCompanionSyncState.WaitingForFirstSync -> stringResource(Res.string.wear_sync_now)

            else -> null
        }

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (state == WearCompanionSyncState.Loading || state == WearCompanionSyncState.Retrying) {
            CircularProgressIndicator()
            Spacer(Modifier.height(6.dp))
        }
        Text(text = title, style = MaterialTheme.typography.titleSmall)
        description?.let {
            Spacer(Modifier.height(4.dp))
            Text(text = it, style = MaterialTheme.typography.bodySmall)
        }
        action?.let {
            Spacer(Modifier.height(8.dp))
            Button(onClick = onRetry, modifier = Modifier.fillMaxWidth()) { Text(text = it) }
        }
    }
}
