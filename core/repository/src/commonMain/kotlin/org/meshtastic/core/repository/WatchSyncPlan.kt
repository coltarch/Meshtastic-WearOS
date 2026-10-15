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
package org.meshtastic.core.repository

import org.meshtastic.core.model.WatchSyncCategory

data class WatchSyncPlan(val enabledCategories: List<WatchSyncCategory>) {
    val disabledCategories: List<WatchSyncCategory> = WatchSyncCategory.entries - enabledCategories.toSet()
    val totalStages: Int = enabledCategories.size
}

fun WatchSyncPreferences.toSyncPlan(): WatchSyncPlan = WatchSyncPlan(
    enabledCategories =
    buildList {
        if (syncNodes) add(WatchSyncCategory.Nodes)
        if (syncChannels) add(WatchSyncCategory.Channels)
        if (syncRecentMessages) add(WatchSyncCategory.RecentMessages)
        if (syncQuickReplies) add(WatchSyncCategory.QuickReplies)
        if (syncRadioStatus) add(WatchSyncCategory.RadioStatus)
    },
)
