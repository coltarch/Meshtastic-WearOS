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

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Logger
import co.touchlab.kermit.Severity
import co.touchlab.kermit.loggerConfigInit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull

class WearableFailureLoggerTest {
    private data class Entry(val severity: Severity, val message: String, val throwable: Throwable?)

    private class CapturingWriter : LogWriter() {
        val entries = mutableListOf<Entry>()

        override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
            entries += Entry(severity, message, throwable)
        }
    }

    private class SensitiveFailure(message: String, cause: Throwable) : Exception(message, cause)

    @Test
    fun `failure logs never expose exception details or attach throwables`() {
        val secret = "https://meshtastic.org/messages/8!private-node message-body"
        val causeSecret = "device-address and channel-key"
        val writer = CapturingWriter()
        val logger = WearableFailureLogger(Logger(loggerConfigInit(writer), tag = "Test"))
        val failure = SensitiveFailure(secret, IllegalStateException(causeSecret))

        WearableFailureLog.entries.forEach { logger.log(it, failure) }

        assertEquals(WearableFailureLog.entries.size, writer.entries.size)
        writer.entries.forEach { entry ->
            assertNull(entry.throwable)
            assertFalse(entry.message.contains(secret))
            assertFalse(entry.message.contains(causeSecret))
            assertFalse(entry.message.contains("private-node"))
            assertFalse(entry.message.contains("message-body"))
        }
    }

    @Test
    fun `failure log severity and output contain only fixed event text and exception type`() {
        val writer = CapturingWriter()
        val logger = WearableFailureLogger(Logger(loggerConfigInit(writer), tag = "Test"))
        val failure = SensitiveFailure("secret-message", IllegalStateException("secret-cause"))

        logger.log(WearableFailureLog.UnexpectedCommand, failure)
        logger.log(WearableFailureLog.OpenConversation, failure)

        assertEquals(Severity.Error, writer.entries[0].severity)
        assertEquals("Unexpected wearable command handling failure (SensitiveFailure)", writer.entries[0].message)
        assertEquals(Severity.Warn, writer.entries[1].severity)
        assertEquals("Unable to open wearable conversation (SensitiveFailure)", writer.entries[1].message)
    }
}
