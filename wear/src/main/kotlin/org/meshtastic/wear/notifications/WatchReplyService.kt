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
package org.meshtastic.wear.notifications

import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.RemoteInput
import co.touchlab.kermit.Logger
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.meshtastic.core.common.di.ApplicationCoroutineScope
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.data.WearDataLayerPaths
import org.meshtastic.wear.presentation.WearableReplyFactory
import org.meshtastic.wear.presentation.model.WearableMessageRepository

/**
 * Sends a reply typed into a notification, which opens no UI — through the same path the chat screen uses.
 *
 * A Service rather than a receiver because the platform allowlists a notification action to start one, exactly as the
 * phone's own `ConversationActionService` does. The send runs on the application scope instead of one this class would
 * have to build and cancel, and the notification is cleared once the reply is handed to the data layer — the only
 * feedback the shade gets.
 */
class WatchReplyService :
    Service(),
    KoinComponent {

    private val repository: WearableMessageRepository by inject()
    private val dataLayerClient: WearDataLayerClient by inject()
    private val applicationScope: ApplicationCoroutineScope by inject()
    private val json = Json
    private val notifications by lazy { WatchNotificationManager(this) }
    private val replyFactory by lazy { WearableReplyFactory(repository) }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val contactKey = intent?.getStringExtra(EXTRA_CONTACT_KEY)
        val text = intent?.let { RemoteInput.getResultsFromIntent(it)?.getCharSequence(KEY_REPLY) }?.toString()

        if (contactKey == null || text.isNullOrBlank()) {
            stopSelf(startId)
            return START_NOT_STICKY
        }

        applicationScope.launch {
            try {
                val reply = replyFactory.create(contactKey = contactKey, text = text)
                val payload = json.encodeToString(reply).encodeToByteArray()
                if (!dataLayerClient.sendCommand(WearDataLayerPaths.SEND_MESSAGE, payload)) {
                    Logger.w { "Watch reply could not be sent through the phone" }
                }
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Logger.w { "Watch reply could not be sent through the phone" }
            } finally {
                notifications.cancel(contactKey)
                stopSelf(startId)
            }
        }
        return START_NOT_STICKY
    }
}
