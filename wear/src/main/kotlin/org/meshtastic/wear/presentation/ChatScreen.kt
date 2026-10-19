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

package org.meshtastic.wear.presentation

import android.app.RemoteInput
import android.content.Intent
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import androidx.wear.input.RemoteInputIntentHelper
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.common.util.DateFormatter
import org.meshtastic.core.model.MessageStatus
import org.meshtastic.core.model.WearableMessage
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.message_delivery_status
import org.meshtastic.core.resources.open_on_phone
import org.meshtastic.core.resources.wear_no_messages
import org.meshtastic.core.resources.wear_quick_reply
import org.meshtastic.core.resources.wear_reply_mic_keys
import org.meshtastic.core.ui.icon.Acknowledged
import org.meshtastic.core.ui.icon.AddLink
import org.meshtastic.core.ui.icon.CloudUpload
import org.meshtastic.core.ui.icon.LinkIcon
import org.meshtastic.core.ui.icon.MeshtasticIcons
import org.meshtastic.core.ui.icon.MessageEnroute
import org.meshtastic.core.ui.icon.MessageError
import org.meshtastic.core.ui.icon.MqttDelivered
import org.meshtastic.core.ui.icon.Warning
import org.meshtastic.wear.data.WearCompanionSyncState
import org.meshtastic.wear.presentation.components.CompanionSyncStatus
import org.meshtastic.wear.presentation.components.PulsingDot

@Composable
fun ChatScreen(contactKey: String, viewModel: ChatViewModel = koinViewModel()) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val channels by viewModel.channels.collectAsStateWithLifecycle()
    val syncedQuickReplies by viewModel.quickReplies.collectAsStateWithLifecycle()
    val syncState by viewModel.syncState.collectAsStateWithLifecycle()
    val listState = rememberTransformingLazyColumnState()

    val chatMessages =
        remember(messages, contactKey) {
            messages.filter { it.contactKey == contactKey }.sortedByDescending { it.timestamp }
        }
    val displayedMessages = remember(chatMessages) { chatMessages.reversed() }
    val chatName = chatMessages.firstOrNull { !it.isMe }?.senderName ?: chatMessages.firstOrNull()?.senderName ?: "Chat"
    val validConversation = chatMessages.isNotEmpty() || channels.any { it.contactKey == contactKey }
    val canSend = validConversation && syncState == WearCompanionSyncState.Ready

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val data: Intent? = result.data
            if (data != null) {
                val results = RemoteInput.getResultsFromIntent(data)
                val text = results?.getCharSequence("extra_voice_reply")
                if (text != null) {
                    viewModel.sendMessage(contactKey, text.toString())
                }
            }
        }

    ScreenScaffold(scrollState = listState) { padding ->
        TransformingLazyColumn(
            state = listState,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize().background(COLOR_BG_DEEP),
        ) {
            item { ChatHeader(chatName = chatName, syncState = syncState) }

            if (syncState != WearCompanionSyncState.Ready) {
                item { CompanionSyncStatus(state = syncState, onRetry = viewModel::retrySync) }
            }

            items(displayedMessages) { msg -> MessageBubble(msg) }

            if (chatMessages.isEmpty() && syncState == WearCompanionSyncState.Ready) {
                item {
                    Text(
                        text = stringResource(Res.string.wear_no_messages),
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    )
                }
            }

            if (canSend) {
                item { ChatDivider() }

                item {
                    val quickReplies = syncedQuickReplies.orEmpty()
                    if (quickReplies.isNotEmpty()) {
                        QuickReplySection(replies = quickReplies, onReply = { viewModel.sendMessage(contactKey, it) })
                    }
                }

                item {
                    ReplyActions(
                        chatName = chatName,
                        launcher = launcher,
                        onOpenOnPhone = { viewModel.openOnPhone(contactKey) },
                    )
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ChatHeader(chatName: String, syncState: WearCompanionSyncState) {
    val statusColor =
        when (syncState) {
            WearCompanionSyncState.Ready -> COLOR_NEON_GREEN

            WearCompanionSyncState.Loading,
            WearCompanionSyncState.Retrying,
            -> COLOR_AMBER

            else -> COLOR_OFFLINE_GRAY
        }
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PulsingDot(color = statusColor, sizeDp = 6)
        Spacer(Modifier.width(5.dp))
        Text(
            text = chatName,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = COLOR_TEAL,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ChatDivider() {
    Box(
        modifier =
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).height(1.dp).background(COLOR_SURFACE2),
    )
}

@Composable
private fun QuickReplySection(replies: List<String>, onReply: (String) -> Unit) {
    Column {
        Text(
            text = stringResource(Res.string.wear_quick_reply),
            fontSize = 9.sp,
            letterSpacing = 1.5.sp,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 12.dp, top = 2.dp, end = 0.dp, bottom = 4.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            replies.forEach { qr ->
                Box(
                    modifier =
                    Modifier.weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(COLOR_SURFACE2)
                        .clickable(role = Role.Button) { onReply(qr) }
                        .padding(vertical = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(qr, fontSize = 11.sp, color = COLOR_TEAL, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun ReplyActions(
    chatName: String,
    launcher: ManagedActivityResultLauncher<Intent, ActivityResult>,
    onOpenOnPhone: () -> Unit,
) {
    Column {
        Button(
            onClick = {
                val remoteInput = RemoteInput.Builder("extra_voice_reply").setLabel("Reply to $chatName").build()
                val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
                RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(remoteInput))
                launcher.launch(intent)
            },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            colors = ButtonDefaults.buttonColors(containerColor = COLOR_TEAL_DIM),
        ) {
            Text(
                text = stringResource(Res.string.wear_reply_mic_keys),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )
        }

        FilledTonalButton(onClick = onOpenOnPhone, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            Text(stringResource(Res.string.open_on_phone), fontSize = 11.sp)
        }
    }
}

@Composable
fun MessageBubble(msg: WearableMessage) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 3.dp),
        horizontalAlignment = if (msg.isMe) Alignment.End else Alignment.Start,
    ) {
        if (!msg.isMe) {
            Text(
                msg.senderName,
                fontSize = 9.sp,
                color = COLOR_TEAL,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 4.dp, bottom = 1.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (msg.isMe) Arrangement.End else Arrangement.Start,
        ) {
            if (msg.isMe) {
                MessageStatusIcon(
                    status = msg.status,
                    modifier = Modifier.padding(end = 4.dp, bottom = 1.dp).size(11.dp),
                )
            }
            Box(
                modifier =
                Modifier.clip(
                    RoundedCornerShape(
                        topStart = 12.dp,
                        topEnd = 12.dp,
                        bottomStart = if (msg.isMe) 12.dp else 3.dp,
                        bottomEnd = if (msg.isMe) 3.dp else 12.dp,
                    ),
                )
                    .background(
                        if (msg.isMe) {
                            Brush.linearGradient(listOf(COLOR_TEAL_DIM, COLOR_TEAL_DEEP))
                        } else {
                            Brush.linearGradient(listOf(COLOR_SURFACE2, COLOR_SURFACE1))
                        },
                    )
                    .padding(horizontal = 9.dp, vertical = 6.dp),
            ) {
                Text(msg.text, fontSize = 12.sp, color = COLOR_TEXT_PRIMARY)
            }
        }
        Text(
            text = DateFormatter.formatTime(msg.timestamp),
            fontSize = 8.sp,
            color = COLOR_TEXT_SECONDARY,
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 1.dp),
        )
    }
}

@Composable
fun MessageStatusIcon(status: MessageStatus, modifier: Modifier = Modifier) {
    val icon =
        when (status) {
            MessageStatus.RECEIVED -> MeshtasticIcons.Acknowledged
            MessageStatus.QUEUED -> MeshtasticIcons.CloudUpload
            MessageStatus.DELIVERED -> MeshtasticIcons.MqttDelivered
            MessageStatus.SFPP_ROUTING -> MeshtasticIcons.AddLink
            MessageStatus.SFPP_CONFIRMED -> MeshtasticIcons.LinkIcon
            MessageStatus.ENROUTE -> MeshtasticIcons.MessageEnroute
            MessageStatus.ERROR -> MeshtasticIcons.MessageError
            else -> MeshtasticIcons.Warning
        }
    val tint = if (status == MessageStatus.ERROR) COLOR_ERROR_RED else COLOR_TEAL
    Icon(
        imageVector = icon,
        contentDescription = stringResource(Res.string.message_delivery_status),
        tint = tint,
        modifier = modifier,
    )
}
