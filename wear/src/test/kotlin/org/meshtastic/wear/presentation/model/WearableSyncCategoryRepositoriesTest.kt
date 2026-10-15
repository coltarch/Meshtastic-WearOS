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
package org.meshtastic.wear.presentation.model

import kotlinx.serialization.json.Json
import org.meshtastic.core.model.WearableRadioStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WearableSyncCategoryRepositoriesTest {
    @Test
    fun `quick reply clear removes only quick replies`() {
        val quickReplies = WearableQuickReplyRepository()
        val radioStatus = WearableRadioStatusRepository()
        val status = WearableRadioStatus(connected = true, batteryLevel = 75)
        quickReplies.updateReplies(listOf("On my way"))
        radioStatus.updateStatus(status)

        quickReplies.clear()

        assertEquals(emptyList(), quickReplies.replies.value)
        assertEquals(status, radioStatus.status.value)
    }

    @Test
    fun `radio status can be cleared independently`() {
        val repository = WearableRadioStatusRepository()
        repository.updateStatus(WearableRadioStatus(connected = true))

        repository.updateStatus(null)

        assertNull(repository.status.value)
    }

    @Test
    fun `serialized radio status carries no location`() {
        val encoded =
            Json.encodeToString(
                WearableRadioStatus.serializer(),
                WearableRadioStatus(connected = true, batteryLevel = 75),
            )

        assertFalse(encoded.contains("latitude"))
        assertFalse(encoded.contains("longitude"))
    }
}
