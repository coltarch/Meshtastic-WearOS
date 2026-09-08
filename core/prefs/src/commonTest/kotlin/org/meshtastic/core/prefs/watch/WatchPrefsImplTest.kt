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
package org.meshtastic.core.prefs.watch

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.prefs.di.asWatchDataStore
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class WatchPrefsImplTest {
    private lateinit var tmpDir: Path
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var prefs: WatchPrefsImpl
    private lateinit var testScope: TestScope

    @BeforeTest
    fun setup() {
        val testDispatcher = UnconfinedTestDispatcher()
        testScope = TestScope(testDispatcher)
        tmpDir = FileSystem.SYSTEM_TEMPORARY_DIRECTORY / "watchPrefsTest-${Uuid.random()}"
        FileSystem.SYSTEM.createDirectories(tmpDir)
        dataStore =
            PreferenceDataStoreFactory.createWithPath(
                scope = testScope,
                produceFile = { tmpDir / "test.preferences_pb" },
            )
        prefs =
            WatchPrefsImpl(
                dataStore.asWatchDataStore(),
                CoroutineDispatchers(testDispatcher, testDispatcher, testDispatcher),
            )
    }

    @AfterTest
    fun tearDown() {
        testScope.cancel()
        FileSystem.SYSTEM.deleteRecursively(tmpDir)
    }

    @Test
    fun `synchronization is enabled by default`() = testScope.runTest {
        assertTrue(prefs.pushToWatchEnabled.value)
        assertTrue(prefs.syncNodesEnabled.value)
        assertTrue(prefs.syncMessagesEnabled.value)
        assertEquals(0L, prefs.syncRequest.value)
    }

    @Test
    fun `synchronization preferences persist updates`() = testScope.runTest {
        prefs.setPushToWatchEnabled(false)
        prefs.setSyncNodesEnabled(false)
        prefs.setSyncMessagesEnabled(false)

        assertEquals(
            false,
            dataStore.data.first {
                it[WatchPrefsImpl.KEY_PUSH_TO_WATCH] == false
            }[WatchPrefsImpl.KEY_PUSH_TO_WATCH],
        )
        assertEquals(
            false,
            dataStore.data.first { it[WatchPrefsImpl.KEY_SYNC_NODES] == false }[WatchPrefsImpl.KEY_SYNC_NODES],
        )
        assertEquals(
            false,
            dataStore.data.first {
                it[WatchPrefsImpl.KEY_SYNC_MESSAGES] == false
            }[WatchPrefsImpl.KEY_SYNC_MESSAGES],
        )
    }

    @Test
    fun `each synchronization request advances the persisted generation`() = testScope.runTest {
        prefs.requestSync()
        prefs.requestSync()

        val stored = dataStore.data.first { it[WatchPrefsImpl.KEY_SYNC_REQUEST] == 2L }

        assertEquals(2L, stored[WatchPrefsImpl.KEY_SYNC_REQUEST])
        assertEquals(2L, prefs.syncRequest.first { it == 2L })
    }
}
