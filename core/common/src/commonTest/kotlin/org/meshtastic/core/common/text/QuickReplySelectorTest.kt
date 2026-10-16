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
package org.meshtastic.core.common.text

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Locks down which of the user's own phrases the chips offer for a given message.
 *
 * Two properties matter besides the ordering: the selector never returns text the user did not write, and a message
 * that matches no intent leaves the synced order alone. The watch renders whatever list it is handed, so a mistake
 * here shows up on the wrist rather than in the build.
 */
class QuickReplySelectorTest {

    private val replies = listOf("Yes", "No", "Sure", "On my way", "Running late", "You're welcome", "Hey")

    private fun select(text: String, limit: Int = 3) = QuickReplySelector.select(replies, listOf(text), limit)

    @Test
    fun `a question offers the affirmative, the negative and a short assent`() {
        val selected = select("Are you at camp?")

        assertEquals(listOf("Yes", "No", "Sure"), selected)
    }

    @Test
    fun `a question needs no question mark to be read as one`() {
        val selected = select("can you hear me")

        assertEquals(listOf("Yes", "No", "Sure"), selected)
    }

    @Test
    fun `where are you surfaces the arrival phrases first`() {
        val selected = select("where are you")

        assertEquals(listOf("On my way", "Yes", "No"), selected)
    }

    @Test
    fun `thanks surfaces the acknowledgement`() {
        val selected = select("thanks!")

        assertEquals(listOf("You're welcome", "Yes", "No"), selected)
    }

    @Test
    fun `a greeting surfaces the greeting back`() {
        val selected = select("hey")

        assertEquals(listOf("Hey", "Yes", "No"), selected)
    }

    @Test
    fun `a message matching nothing keeps the synced order`() {
        val selected = select("beacon is at grid 12")

        assertEquals(listOf("Yes", "No", "Sure"), selected)
    }

    @Test
    fun `the limit applies to the synced order too`() {
        val selected = select("beacon is at grid 12", limit = 2)

        assertEquals(listOf("Yes", "No"), selected)
    }

    @Test
    fun `fewer phrases than the limit are all returned`() {
        val selected = QuickReplySelector.select(listOf("Yes"), listOf("are you there?"))

        assertEquals(listOf("Yes"), selected)
    }

    @Test
    fun `no phrases at all selects nothing`() {
        val selected = QuickReplySelector.select(emptyList(), listOf("are you there?"))

        assertEquals(emptyList(), selected)
    }

    @Test
    fun `a limit of zero selects nothing`() {
        val selected = select("are you there?", limit = 0)

        assertEquals(emptyList(), selected)
    }

    @Test
    fun `a blank phrase is never offered`() {
        val selected = QuickReplySelector.select(listOf("Yes", "   ", "No"), listOf("are you there?"))

        assertEquals(listOf("Yes", "No"), selected)
    }

    @Test
    fun `intents are matched whatever the case`() {
        val selected = QuickReplySelector.select(listOf("YES", "on my way"), listOf("WHERE ARE YOU"))

        assertEquals(listOf("on my way", "YES"), selected)
    }

    @Test
    fun `a short word only matches a whole word`() {
        val selected =
            QuickReplySelector.select(
                listOf("Nobody knows", "This is fine", "No"),
                listOf("are you free?"),
            )

        // "Nobody" must not be read as "no": the real "No" answers the question, and the phrases that merely contain
        // those letters keep their synced order behind it.
        assertEquals(listOf("No", "Nobody knows", "This is fine"), selected)
    }

    @Test
    fun `the same input always selects the same list`() {
        val first = select("where are you")
        val second = select("where are you")

        assertEquals(first, second)
    }

    @Test
    fun `every selected phrase is one the user wrote`() {
        val incoming = listOf("are you there?", "where are you", "thanks", "hey", "grid 12 is clear")

        incoming.forEach { text ->
            val selected = QuickReplySelector.select(replies, listOf(text))

            assertTrue(selected.all(replies::contains), "\"$text\" selected a phrase that is not in the synced list")
        }
    }
}
