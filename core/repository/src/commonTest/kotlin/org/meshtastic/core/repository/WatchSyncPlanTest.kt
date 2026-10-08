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
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WatchSyncPlanTest {
    @Test
    fun `defaults enable every category and count every progress stage`() {
        val plan = WatchSyncPreferences().toSyncPlan()

        assertEquals(WatchSyncCategory.entries, plan.enabledCategories)
        assertEquals(5, plan.totalStages)
        assertTrue(plan.disabledCategories.isEmpty())
    }

    @Test
    fun `each disabled category is omitted and scheduled for clearing`() {
        WatchSyncCategory.entries.forEach { disabled ->
            val preferences = WatchSyncPreferences().withCategory(disabled, enabled = false)
            val plan = preferences.toSyncPlan()

            assertEquals(WatchSyncCategory.entries - disabled, plan.enabledCategories)
            assertEquals(listOf(disabled), plan.disabledCategories)
            assertEquals(4, plan.totalStages)
        }
    }

    @Test
    fun `all categories disabled produces an empty progress plan`() {
        val plan =
            WatchSyncCategory.entries
                .fold(WatchSyncPreferences()) { preferences, category ->
                    preferences.withCategory(category, enabled = false)
                }
                .toSyncPlan()

        assertTrue(plan.enabledCategories.isEmpty())
        assertEquals(0, plan.totalStages)
        assertEquals(WatchSyncCategory.entries, plan.disabledCategories)
    }

    @Test
    fun `re-enabling a category includes it in the next plan`() {
        val disabled = WatchSyncPreferences(syncQuickReplies = false).toSyncPlan()
        val reEnabled = WatchSyncPreferences(syncQuickReplies = true).toSyncPlan()

        assertEquals(false, WatchSyncCategory.QuickReplies in disabled.enabledCategories)
        assertEquals(true, WatchSyncCategory.QuickReplies in reEnabled.enabledCategories)
    }
}

private fun WatchSyncPreferences.withCategory(category: WatchSyncCategory, enabled: Boolean): WatchSyncPreferences =
    when (category) {
        WatchSyncCategory.Nodes -> copy(syncNodes = enabled)
        WatchSyncCategory.Channels -> copy(syncChannels = enabled)
        WatchSyncCategory.RecentMessages -> copy(syncRecentMessages = enabled)
        WatchSyncCategory.QuickReplies -> copy(syncQuickReplies = enabled)
        WatchSyncCategory.RadioStatus -> copy(syncRadioStatus = enabled)
    }
