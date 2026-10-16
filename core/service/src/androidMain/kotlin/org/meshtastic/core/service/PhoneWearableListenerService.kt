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
package org.meshtastic.core.service

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.meshtastic.core.common.di.ServiceScope
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.usecase.SendMessageUseCase

/** Listener that receives validated commands from a watch through the Wearable Message API. */
class PhoneWearableListenerService : WearableListenerService() {
    private val syncService: WearableSyncService by inject()
    private val sendMessageUseCase: SendMessageUseCase by inject()
    private val watchPrefs: WatchPrefs by inject()
    private val serviceScope: ServiceScope by inject()
    private val failureLogger = WearableFailureLogger(Logger.withTag("PhoneWearableListenerService"))
    private val router = WearableIncomingActionRouter()
    private val handler by lazy {
        WearableIncomingActionHandler(
            requestSync = syncService::forceSync,
            sendMessage = { text, contactKey -> sendMessageUseCase(text, contactKey) },
            openConversation = ::openConversation,
            openNode = ::openNode,
        )
    }

    override fun onMessageReceived(messageEvent: MessageEvent) {
        if (!watchPrefs.watchConnectivityEnabled.value) {
            Logger.d { "Ignored wearable command while watch connectivity is off" }
        } else {
            handleEnabledMessage(messageEvent)
        }
    }

    private fun handleEnabledMessage(messageEvent: MessageEvent) {
        when (val action = router.route(messageEvent.path, messageEvent.data, messageEvent.sourceNodeId)) {
            WearableIncomingAction.Malformed -> Logger.w { "Rejected malformed wearable command" }

            WearableIncomingAction.Unknown -> Logger.d { "Ignored unknown wearable command" }

            else -> {
                Logger.d { "Handling wearable command" }
                syncService.recordSuccessfulWatchCommunication()
                serviceScope.launch {
                    try {
                        handler.handle(action)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: IllegalArgumentException) {
                        failureLogger.log(WearableFailureLog.InvalidCommand, e)
                    } catch (e: IllegalStateException) {
                        failureLogger.log(WearableFailureLog.UnavailableCommand, e)
                    } catch (@Suppress("TooGenericExceptionCaught") e: Exception) {
                        failureLogger.log(WearableFailureLog.FailedCommand, e)
                    }
                }
            }
        }
    }

    private fun openConversation(contactKey: String) {
        val uri =
            Uri.Builder()
                .scheme("https")
                .authority("meshtastic.org")
                .appendPath("messages")
                .appendPath(contactKey)
                .build()
        val intent =
            Intent(Intent.ACTION_VIEW, uri)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        try {
            startActivity(intent)
            Logger.d { "Opened wearable conversation on phone" }
        } catch (e: ActivityNotFoundException) {
            failureLogger.log(WearableFailureLog.OpenConversation, e)
        } catch (e: SecurityException) {
            failureLogger.log(WearableFailureLog.OpenConversation, e)
        }
    }

    private fun openNode(nodeNum: Int) {
        val uri =
            Uri.Builder().scheme("https").authority("meshtastic.org").appendPath("nodes").appendPath("$nodeNum").build()
        val intent =
            Intent(Intent.ACTION_VIEW, uri)
                .setPackage(packageName)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        try {
            startActivity(intent)
            Logger.d { "Opened wearable node on phone" }
        } catch (e: ActivityNotFoundException) {
            failureLogger.log(WearableFailureLog.OpenNode, e)
        } catch (e: SecurityException) {
            failureLogger.log(WearableFailureLog.OpenNode, e)
        }
    }
}
