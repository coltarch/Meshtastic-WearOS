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
@file:Suppress("ModifierMissing", "TooManyFunctions")

package org.meshtastic.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.SwitchButtonDefaults
import androidx.wear.compose.material3.Text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.watch_settings_title
import org.meshtastic.core.resources.wear_phone_connection
import org.meshtastic.core.resources.wear_phone_linked
import org.meshtastic.core.resources.wear_phone_linked_description
import org.meshtastic.core.resources.wear_sync_now
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.presentation.components.CompanionSyncStatus
import org.meshtastic.wear.presentation.components.SectionHeader

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = koinViewModel()) {
    val highContrastMode by viewModel.highContrastModeEnabled.collectAsStateWithLifecycle()

    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader(stringResource(Res.string.watch_settings_title)) }

            item { CompanionConnectionSection() }
            item { CompanionMaintenanceSection() }

            item {
                DisplaySection(
                    highContrastMode = highContrastMode,
                    onHighContrastModeChange = { viewModel.setHighContrastModeEnabled(it) },
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun CompanionConnectionSection(viewModel: CompanionConnectionViewModel = koinViewModel()) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    PhoneConnectionSection(state = connectionState, onRefreshClick = viewModel::refreshPhoneLink)
}

@Composable
private fun CompanionMaintenanceSection(viewModel: CompanionConnectionViewModel = koinViewModel()) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    MaintenanceSection(
        syncing =
        connectionState == WearCompanionSyncState.Loading || connectionState == WearCompanionSyncState.Retrying,
        onSyncClick = viewModel::requestSync,
    )
}

@Composable
private fun PhoneConnectionSection(state: WearCompanionSyncState, onRefreshClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        Text(
            text = stringResource(Res.string.wear_phone_connection),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        if (state == WearCompanionSyncState.Ready) {
            Text(
                text = stringResource(Res.string.wear_phone_linked),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Text(
                text = stringResource(Res.string.wear_phone_linked_description),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
            )
            Button(onClick = onRefreshClick, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.wear_sync_now))
            }
        } else {
            CompanionSyncStatus(state = state, onRetry = onRefreshClick)
        }
    }
}

@Composable
private fun DisplaySection(highContrastMode: Boolean, onHighContrastModeChange: (Boolean) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Display",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        SwitchButton(
            checked = highContrastMode,
            onCheckedChange = onHighContrastModeChange,
            modifier = Modifier.fillMaxWidth(),
            colors = SwitchButtonDefaults.switchButtonColors(uncheckedContainerColor = COLOR_SURFACE2),
            label = {
                Text(
                    text = "Alternate colors",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = COLOR_TEXT_PRIMARY,
                )
            },
            secondaryLabel = {
                Text(
                    text = if (highContrastMode) "Grey theme enabled" else "OLED mode enabled",
                    fontSize = 9.sp,
                    color = COLOR_TEXT_SECONDARY,
                )
            },
        )
    }
}

@Composable
private fun MaintenanceSection(syncing: Boolean, onSyncClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Maintenance",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = onSyncClick,
            enabled = !syncing,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                    Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_AMBER.copy(alpha = 0.8f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("SY", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = stringResource(Res.string.wear_sync_now),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COLOR_TEXT_PRIMARY,
                    )
                    Text("Refresh from phone", fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
                }
            }
        }
    }
}
