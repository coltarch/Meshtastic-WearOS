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
package org.meshtastic.wear.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Card
import androidx.wear.compose.material3.CardDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.common.util.MetricFormatter
import org.meshtastic.core.model.NodeAddress
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.externally_powered
import org.meshtastic.core.resources.favorite
import org.meshtastic.core.resources.last_seen
import org.meshtastic.core.resources.no
import org.meshtastic.core.resources.node_id
import org.meshtastic.core.resources.node_list_help_node_details
import org.meshtastic.core.resources.node_unavailable
import org.meshtastic.core.resources.offline
import org.meshtastic.core.resources.online
import org.meshtastic.core.resources.open_on_phone
import org.meshtastic.core.resources.short_name
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.unavailable
import org.meshtastic.core.resources.yes
import org.meshtastic.core.ui.icon.BatteryEmpty
import org.meshtastic.core.ui.icon.Favorite
import org.meshtastic.core.ui.icon.History
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.NodeId
import org.meshtastic.core.ui.icon.Person
import org.meshtastic.core.ui.icon.PersonQuestion
import org.meshtastic.core.ui.icon.PhoneAndroid
import org.meshtastic.core.ui.icon.PowerSettingsNew
import org.meshtastic.core.ui.icon.Snr
import org.meshtastic.wear.presentation.model.WearableNodeRepository

@Composable
fun NodeDetailsScreen(nodeNum: Int?, onOpenOnPhone: (Int) -> Unit, repository: WearableNodeRepository = koinInject()) {
    val nodes by repository.syncedNodes.collectAsStateWithLifecycle()
    val selectedNode = nodeNum?.let { selectedNum -> nodes.firstOrNull { it.num == selectedNum } }

    NodeDetailsContent(node = selectedNode, onOpenOnPhone = onOpenOnPhone)
}

@Composable
internal fun NodeDetailsContent(node: WearableNode?, onOpenOnPhone: (Int) -> Unit = {}) {
    val listState = rememberTransformingLazyColumnState()

    ScreenScaffold(scrollState = listState) { contentPadding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = contentPadding,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { ListHeader { Text(text = stringResource(Res.string.node_list_help_node_details)) } }

            if (node == null) {
                item { NodeUnavailableCard() }
            } else {
                nodeDetailsItems(node = node, onOpenOnPhone = onOpenOnPhone)
            }
        }
    }
}

private fun TransformingLazyColumnScope.nodeDetailsItems(node: WearableNode, onOpenOnPhone: (Int) -> Unit) {
    item { NodeDetailsHeader(node = node) }
    item { OpenOnPhoneButton(onClick = { onOpenOnPhone(node.num) }) }
    item {
        NodeInformationRow(
            icon = MeshtasticIcons.NodeId,
            label = stringResource(Res.string.node_id),
            value = NodeAddress.numToDefaultId(node.num),
        )
    }
    item {
        NodeInformationRow(
            icon = MeshtasticIcons.Person,
            label = stringResource(Res.string.short_name),
            value = node.shortName.ifBlank { stringResource(Res.string.unavailable) },
        )
    }
    item {
        NodeInformationRow(
            icon = MeshtasticIcons.Favorite,
            label = stringResource(Res.string.favorite),
            value = stringResource(if (node.favorite) Res.string.yes else Res.string.no),
        )
    }
    nodeBatteryItem(node.battery)
    node.snr?.let { signalToNoiseRatio ->
        item {
            NodeInformationRow(
                icon = MeshtasticIcons.Snr,
                label = stringResource(Res.string.snr),
                value = MetricFormatter.snr(signalToNoiseRatio),
            )
        }
    }
    if (node.lastSeen > 0) {
        item {
            NodeInformationRow(
                icon = MeshtasticIcons.History,
                label = stringResource(Res.string.last_seen),
                value = DateFormatter.formatRelativeTime(node.lastSeen),
            )
        }
    }
    item { Spacer(modifier = Modifier.height(12.dp)) }
}

private fun TransformingLazyColumnScope.nodeBatteryItem(batteryLevel: Int?) {
    batteryLevel ?: return
    item {
        if (batteryLevel >= EXTERNALLY_POWERED_BATTERY_LEVEL) {
            NodeInformationRow(
                icon = MeshtasticIcons.PowerSettingsNew,
                label = stringResource(Res.string.battery),
                value = stringResource(Res.string.externally_powered),
            )
        } else {
            NodeInformationRow(
                icon = MeshtasticIcons.BatteryEmpty,
                label = stringResource(Res.string.battery),
                value = MetricFormatter.percent(batteryLevel),
            )
        }
    }
}

@Composable
private fun OpenOnPhoneButton(onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Icon(imageVector = MeshtasticIcons.PhoneAndroid, contentDescription = null, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text = stringResource(Res.string.open_on_phone), style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
private fun NodeDetailsHeader(node: WearableNode) {
    val statusText = stringResource(if (node.online) Res.string.online else Res.string.offline)
    val statusColor = if (node.online) COLOR_TEAL else COLOR_TEXT_SECONDARY

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(48.dp).clip(CircleShape).background(COLOR_TEAL_DIM),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text =
                node.shortName.ifBlank { NodeAddress.numToDefaultId(node.num).takeLast(NODE_ID_AVATAR_CHAR_COUNT) },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = COLOR_BG_DEEP,
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = node.name.ifBlank { stringResource(Res.string.unavailable) },
            style = MaterialTheme.typography.titleMedium,
            color = COLOR_TEXT_PRIMARY,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(text = statusText, style = MaterialTheme.typography.labelMedium, color = statusColor)
    }
}

@Composable
private fun NodeInformationRow(icon: ImageVector, label: String, value: String) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = CardDefaults.cardColors(containerColor = COLOR_SURFACE1),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = COLOR_TEAL,
                modifier = Modifier.size(20.dp),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = COLOR_TEXT_SECONDARY,
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = COLOR_TEXT_PRIMARY,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun NodeUnavailableCard() {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
        colors = CardDefaults.cardColors(containerColor = COLOR_SURFACE1),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(
                imageVector = MeshtasticIcons.PersonQuestion,
                contentDescription = null,
                tint = COLOR_TEXT_SECONDARY,
                modifier = Modifier.size(28.dp),
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(Res.string.node_unavailable),
                style = MaterialTheme.typography.bodyMedium,
                color = COLOR_TEXT_PRIMARY,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private const val EXTERNALLY_POWERED_BATTERY_LEVEL = 101
private const val NODE_ID_AVATAR_CHAR_COUNT = 4
