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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
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
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.cancel
import org.meshtastic.core.resources.companion_mode
import org.meshtastic.core.resources.companion_mode_description
import org.meshtastic.core.resources.experimental_badge
import org.meshtastic.core.resources.operating_mode
import org.meshtastic.core.resources.standalone_radio_battery_warning
import org.meshtastic.core.resources.standalone_radio_experimental_description
import org.meshtastic.core.resources.standalone_radio_mode
import org.meshtastic.core.resources.standalone_radio_phone_sync_notice
import org.meshtastic.core.resources.standalone_radio_unsupported
import org.meshtastic.core.resources.watch_settings_title
import org.meshtastic.core.resources.wear_mode_change_confirm
import org.meshtastic.core.resources.wear_mode_change_restart
import org.meshtastic.core.resources.wear_mode_restart_action
import org.meshtastic.core.resources.wear_mode_restart_required
import org.meshtastic.core.resources.wear_phone_connection
import org.meshtastic.core.resources.wear_phone_linked
import org.meshtastic.core.resources.wear_phone_linked_description
import org.meshtastic.core.resources.wear_sync_now
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.presentation.components.CompanionSyncStatus
import org.meshtastic.wear.presentation.components.SectionHeader

internal const val STANDALONE_RADIO_SWITCH_TEST_TAG = "standalone_radio_switch"

@Composable
fun SettingsScreen(onRestartApp: () -> Unit, viewModel: SettingsViewModel = koinViewModel()) {
    val highContrastMode by viewModel.highContrastModeEnabled.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val confirmationMode by viewModel.confirmationMode.collectAsStateWithLifecycle()

    if (confirmationMode != null) {
        OperatingModeConfirmation(
            mode = checkNotNull(confirmationMode),
            onConfirm = viewModel::confirmOperatingModeChange,
            onCancel = viewModel::cancelOperatingModeChange,
        )
        return
    }

    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { SectionHeader(stringResource(Res.string.watch_settings_title)) }

            item {
                OperatingModeSection(
                    state = settingsState,
                    standaloneSupported = viewModel.standaloneSupported,
                    onModeRequest = viewModel::requestOperatingMode,
                    onRestartApp = onRestartApp,
                )
            }

            if (settingsState.activeMode == WearOperatingMode.COMPANION) {
                item { CompanionConnectionSection() }
                item { PhoneMenuSection() }
                item { DeviceSection() }
                item { CompanionMaintenanceSection() }
            }

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
private fun ExperimentalBadge() {
    Text(
        text = stringResource(Res.string.experimental_badge),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
        Modifier.clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    )
}

@Composable
internal fun OperatingModeSection(
    state: WearSettingsUiState,
    standaloneSupported: Boolean,
    onModeRequest: (WearOperatingMode) -> Unit,
    onRestartApp: () -> Unit,
) {
    val standaloneSummary = stringResource(Res.string.standalone_radio_experimental_description)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = stringResource(Res.string.operating_mode),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Text(
            text = stringResource(Res.string.companion_mode),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 4.dp),
        )
        Text(
            text = stringResource(Res.string.companion_mode_description),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        SwitchButton(
            checked = state.configuredMode == WearOperatingMode.STANDALONE_RADIO,
            onCheckedChange = { checked ->
                onModeRequest(if (checked) WearOperatingMode.STANDALONE_RADIO else WearOperatingMode.COMPANION)
            },
            enabled = standaloneSupported || state.configuredMode == WearOperatingMode.STANDALONE_RADIO,
            modifier =
            Modifier.fillMaxWidth().testTag(STANDALONE_RADIO_SWITCH_TEST_TAG).semantics {
                stateDescription = standaloneSummary
            },
            colors = SwitchButtonDefaults.switchButtonColors(),
            label = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(text = stringResource(Res.string.standalone_radio_mode), modifier = Modifier.weight(1f))
                    ExperimentalBadge()
                }
            },
            secondaryLabel = {
                Column {
                    Text(text = standaloneSummary)
                    if (!standaloneSupported) Text(text = stringResource(Res.string.standalone_radio_unsupported))
                }
            },
        )
        if (state.restartRequired) {
            Text(
                text = stringResource(Res.string.wear_mode_restart_required),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
            Button(onClick = onRestartApp, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(Res.string.wear_mode_restart_action))
            }
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
private fun OperatingModeConfirmation(mode: WearOperatingMode, onConfirm: () -> Unit, onCancel: () -> Unit) {
    val listState = rememberTransformingLazyColumnState()
    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(state = listState, contentPadding = padding, modifier = Modifier.fillMaxSize()) {
            item { SectionHeader(stringResource(Res.string.wear_mode_change_confirm)) }
            item {
                Text(
                    text =
                    stringResource(
                        if (mode == WearOperatingMode.STANDALONE_RADIO) {
                            Res.string.standalone_radio_mode
                        } else {
                            Res.string.companion_mode
                        },
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                )
            }
            item {
                Text(
                    text = stringResource(Res.string.wear_mode_change_restart),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }
            if (mode == WearOperatingMode.STANDALONE_RADIO) {
                item {
                    Text(
                        text = stringResource(Res.string.standalone_radio_battery_warning),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
                item {
                    Text(
                        text = stringResource(Res.string.standalone_radio_phone_sync_notice),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            item {
                Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(stringResource(Res.string.wear_mode_change_confirm))
                }
            }
            item {
                Button(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Text(stringResource(Res.string.cancel))
                }
            }
        }
    }
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
private fun PhoneMenuSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Phone Menu",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = { /* TODO: Notification settings */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_TEAL_DIM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("NT", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Notifications",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COLOR_TEXT_PRIMARY,
                    )
                    Text("Alerts from phone", fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
                }
            }
        }
    }
}

@Composable
private fun DeviceSection() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(
            text = "Device",
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, bottom = 4.dp),
        )

        Button(
            onClick = { /* TODO: Radio settings */ },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_SURFACE2),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(COLOR_TEAL_DIM),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("RD", fontSize = 8.sp, fontWeight = FontWeight.Bold, color = COLOR_BG_DEEP)
                }
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Radio Settings",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = COLOR_TEXT_PRIMARY,
                    )
                    Text("Configure radio", fontSize = 9.sp, color = COLOR_TEXT_SECONDARY)
                }
            }
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
