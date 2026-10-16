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
package org.meshtastic.core.common.wear

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Pins down which messages the watch notifies about, since the notification itself cannot be run anywhere here.
 *
 * There is no emulator, no watch and no CI task that runs a `:wear` test, so the detector is the only part of the
 * feature a test can reach. The cases below are therefore the mistakes that would otherwise surface on a wrist: a
 * snapshot that is entirely old, the user's own messages, and a snapshot where new and old messages interleave.
 */
class NewMessageDetectorTest {

    private fun ref(uuid: Long, isMe: Boolean = false): NewMessageDetector.MessageRef =
        NewMessageDetector.MessageRef(uuid = uuid, contactKey = "dm-1", isMe = isMe)

    @Test
    fun `an empty snapshot notifies nothing`() {
        assertEquals(emptyList(), NewMessageDetector.newIncoming(seen = emptySet(), current = emptyList()))
    }

    @Test
    fun `a snapshot that is entirely already seen notifies nothing`() {
        val current = listOf(ref(1), ref(2))
        assertEquals(emptyList(), NewMessageDetector.newIncoming(seen = setOf(1L, 2L), current = current))
    }

    @Test
    fun `the user writes no notifications to himself`() {
        val current = listOf(ref(1, isMe = true), ref(2, isMe = true))
        assertEquals(emptyList(), NewMessageDetector.newIncoming(seen = emptySet(), current = current))
    }

    @Test
    fun `the one message that is new is the one returned`() {
        val current = listOf(ref(1), ref(2))
        assertEquals(listOf(ref(2)), NewMessageDetector.newIncoming(seen = setOf(1L), current = current))
    }

    @Test
    fun `new and old interleaved keep only the new ones in snapshot order`() {
        val current = listOf(ref(1), ref(2), ref(3), ref(4), ref(5))
        val new = NewMessageDetector.newIncoming(seen = setOf(1L, 3L, 5L), current = current)
        assertEquals(listOf(ref(2), ref(4)), new)
    }

    @Test
    fun `an own message between two new ones is skipped without reordering them`() {
        val current = listOf(ref(1), ref(2, isMe = true), ref(3))
        assertEquals(listOf(ref(1), ref(3)), NewMessageDetector.newIncoming(seen = emptySet(), current = current))
    }
}
