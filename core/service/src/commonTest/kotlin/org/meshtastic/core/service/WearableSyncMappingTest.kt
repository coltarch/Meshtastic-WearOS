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

import org.meshtastic.core.model.ConnectionState
import org.meshtastic.core.model.Node
import org.meshtastic.core.model.WatchSyncCategory
import org.meshtastic.core.repository.WatchSyncPreferences
import org.meshtastic.core.repository.toSyncPlan
import org.meshtastic.proto.DeviceMetrics
import org.meshtastic.proto.EnvironmentMetrics
import org.meshtastic.proto.User
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WearableSyncMappingTest {
    @Test
    fun `radio status reports the connection state`() {
        assertTrue(createWearableRadioStatus(null, ConnectionState.Connected).connected)
        assertFalse(createWearableRadioStatus(null, ConnectionState.Disconnected).connected)
    }

    @Test
    fun `radio status without a node reports no telemetry`() {
        val status = createWearableRadioStatus(null, ConnectionState.Connected)

        assertNull(status.batteryLevel)
        assertNull(status.voltage)
        assertNull(status.snr)
        assertNull(status.lastSeen)
        assertNull(status.temperature)
        assertNull(status.relativeHumidity)
    }

    @Test
    fun `radio status maps telemetry and converts last heard to millis`() {
        val node =
            testNode(
                num = 1,
                lastHeard = 1_700_000_000,
                snr = 12.5f,
                batteryLevel = 87,
                voltage = 4.1f,
                temperature = 21.5f,
                humidity = 45f,
            )
        val status = createWearableRadioStatus(node, ConnectionState.Connected)

        assertEquals(87, status.batteryLevel)
        assertEquals(4.1f, status.voltage)
        assertEquals(12.5f, status.snr)
        assertEquals(1_700_000_000_000L, status.lastSeen)
        assertEquals(21.5f, status.temperature)
        assertEquals(45f, status.relativeHumidity)
    }

    @Test
    fun `wearable node without radio status strips telemetry and forces offline`() {
        val node = testNode(num = 7, isFavorite = true)
        val wearable = node.toWearableNode(includeRadioStatus = false)

        assertEquals(7, wearable.num)
        assertEquals("Long", wearable.name)
        assertEquals("L", wearable.shortName)
        assertFalse(wearable.online)
        assertNull(wearable.battery)
        assertNull(wearable.snr)
        assertEquals(0L, wearable.lastSeen)
        assertTrue(wearable.favorite)
    }

    @Test
    fun `wearable node with radio status maps telemetry and liveness`() {
        val node =
            testNode(
                num = 7,
                lastHeard = Int.MAX_VALUE,
                snr = 5.0f,
                batteryLevel = 62,
                isFavorite = true,
            )
        val wearable = node.toWearableNode(includeRadioStatus = true)

        assertTrue(wearable.online)
        assertEquals(62, wearable.battery)
        assertEquals(5.0f, wearable.snr)
        assertEquals(Int.MAX_VALUE.toLong() * 1_000L, wearable.lastSeen)
        assertTrue(wearable.favorite)
    }

    @Test
    fun `a preference set with nothing enabled has no enabled category`() {
        val nothingEnabled =
            WatchSyncPreferences(
                syncNodes = false,
                syncChannels = false,
                syncRecentMessages = false,
                syncQuickReplies = false,
                syncRadioStatus = false,
            )

        assertFalse(nothingEnabled.hasEnabledCategory)
        assertTrue(nothingEnabled.toSyncPlan().enabledCategories.isEmpty())
        assertEquals(WatchSyncCategory.entries, nothingEnabled.toSyncPlan().disabledCategories)
        assertTrue(WatchSyncPreferences().hasEnabledCategory)
    }
}

private fun testNode(
    num: Int,
    lastHeard: Int = 0,
    snr: Float = 0f,
    batteryLevel: Int? = null,
    voltage: Float? = null,
    temperature: Float? = null,
    humidity: Float? = null,
    isFavorite: Boolean = false,
): Node {
    val user = User.Builder()
    user.long_name = "Long"
    user.short_name = "L"
    val deviceMetrics = DeviceMetrics.Builder()
    deviceMetrics.battery_level = batteryLevel
    deviceMetrics.voltage = voltage
    val environmentMetrics = EnvironmentMetrics.Builder()
    environmentMetrics.temperature = temperature
    environmentMetrics.relative_humidity = humidity
    return Node(
        num = num,
        user = user.build(),
        lastHeard = lastHeard,
        snr = snr,
        deviceMetrics = deviceMetrics.build(),
        environmentMetrics = environmentMetrics.build(),
        isFavorite = isFavorite,
    )
}
