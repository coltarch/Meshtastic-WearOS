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

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import org.meshtastic.core.resources.R.drawable
import org.meshtastic.wear.presentation.MainActivity

/** The extra a notification's tap carries; the conversation the notification belongs to. */
const val EXTRA_CONTACT_KEY = "extra_contact_key"

/**
 * The reply action's `RemoteInput` key — deliberately the one the in-app "Reply (Mic / Keys)" button already uses, so a
 * reply from the shade and a reply from that button are the same thing to the phone.
 */
const val KEY_REPLY = "extra_voice_reply"

/**
 * The watch's own message notification: one per conversation, answering without opening the app.
 *
 * It mirrors the phone's manager instead of inventing a convention — a single high-importance channel, a
 * `MessagingStyle` conversation, the app's own notification icon, and a reply action offering the phrases the user
 * already synced. The user-visible labels are literals like the rest of this module; they move to resources with the
 * other hardcoded watch strings.
 */
class WatchNotificationManager(private val context: Context) {

    /** Posts the conversation's notification, replacing its previous one: the user's own phrases are the choices. */
    fun post(contactKey: String, senderName: String, text: String, timestamp: Long, quickReplies: List<String>) {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        ensureChannel()
        val id = contactKey.hashCode()
        val input = RemoteInput.Builder(KEY_REPLY).setLabel(REPLY_LABEL).setChoices(quickReplies.toTypedArray())
        val action =
            NotificationCompat.Action.Builder(REPLY_ICON, REPLY_LABEL, replyIntent(contactKey))
                .addRemoteInput(input.build())
                .setAllowGeneratedReplies(false)
                .build()
        val style = NotificationCompat.MessagingStyle(person(SELF_NAME)).addMessage(text, timestamp, person(senderName))
        val notification =
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(drawable.meshtastic_ic_notification)
                .setStyle(style)
                .setContentTitle(senderName)
                .setContentText(text)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setAutoCancel(true)
                .setContentIntent(openIntent(contactKey))
                .addAction(action)
                .build()
        NotificationManagerCompat.from(context).notify(id, notification)
    }

    /** Clears the conversation's notification, whether the reply succeeded or the user dismissed it. */
    fun cancel(contactKey: String) {
        NotificationManagerCompat.from(context).cancel(contactKey.hashCode())
    }

    private fun ensureChannel() {
        val manager = NotificationManagerCompat.from(context)
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH)
        channel.description = CHANNEL_DESCRIPTION
        manager.createNotificationChannel(channel)
    }

    private fun person(name: String): Person = Person.Builder().setName(name).build()

    private fun openIntent(contactKey: String): PendingIntent =
        PendingIntent.getActivity(
            context,
            contactKey.hashCode(),
            Intent(context, MainActivity::class.java).putExtra(EXTRA_CONTACT_KEY, contactKey),
            PENDING_INTENT_FLAGS,
        )

    private fun replyIntent(contactKey: String): PendingIntent =
        PendingIntent.getService(
            context,
            contactKey.hashCode(),
            Intent(context, WatchReplyService::class.java).putExtra(EXTRA_CONTACT_KEY, contactKey),
            PENDING_INTENT_FLAGS,
        )

    private companion object {
        const val CHANNEL_ID = "watch_messages"
        const val CHANNEL_NAME = "Messages"
        const val CHANNEL_DESCRIPTION = "Direct messages from the mesh"
        const val REPLY_LABEL = "Reply"
        const val SELF_NAME = "You"
        val REPLY_ICON = android.R.drawable.ic_menu_send
        val PENDING_INTENT_FLAGS = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    }
}
