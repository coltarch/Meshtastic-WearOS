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
package org.meshtastic.wear.di

import android.app.Application
import android.content.Context
import android.hardware.usb.UsbManager
import androidx.test.core.app.ApplicationProvider
import androidx.work.WorkManager
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import org.junit.runner.RunWith
import org.koin.android.ext.koin.androidContext
import org.koin.core.KoinApplication
import org.koin.core.annotation.KoinExperimentalAPI
import org.koin.dsl.koinApplication
import org.koin.dsl.module
import org.koin.test.verify.verify
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.common.di.CoreCommonModule
import org.meshtastic.core.database.MeshtasticDatabase
import org.meshtastic.core.di.di.CoreDiModule
import org.meshtastic.core.prefs.di.CorePrefsAndroidModule
import org.meshtastic.core.prefs.di.CorePrefsModule
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.service.MeshServiceOrchestrator
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.presentation.model.WearableChannelRepository
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import org.meshtastic.wear.presentation.model.WearableNodeRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.meshtastic.core.common.di.module as coreCommonModule
import org.meshtastic.core.di.di.module as coreDiModule
import org.meshtastic.core.prefs.di.module as corePrefsModule

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class KoinVerificationTest {

    private var application: KoinApplication? = null

    @AfterTest
    fun tearDown() {
        application?.close()
    }

    @OptIn(KoinExperimentalAPI::class)
    @Test
    fun `companion startup graph resolves every Wear screen dependency`() {
        module { includes(*companionModules().toTypedArray()) }
            .verify(extraTypes = listOf(Application::class, Context::class, CoroutineDispatcher::class))
    }

    @Test
    fun `companion startup keeps Data Layer sync commands and preferences available`() {
        startCompanionKoin()

        val koin = checkNotNull(application).koin
        assertNotNull(koin.getOrNull<WearDataLayerClient>())
        assertNotNull(koin.getOrNull<WearableNodeRepository>())
        assertNotNull(koin.getOrNull<WearableMessageRepository>())
        assertNotNull(koin.getOrNull<WearableChannelRepository>())
        assertNotNull(koin.getOrNull<WatchPrefs>())
        assertNotNull(koin.getOrNull<CompanionGraphMarker>())
        assertNull(koin.getOrNull<StandaloneGraphMarker>())
    }

    @Test
    fun `standalone startup loads only the direct radio mode module`() {
        startStandaloneKoin()

        val koin = checkNotNull(application).koin
        assertNotNull(koin.getOrNull<StandaloneGraphMarker>())
        assertNull(koin.getOrNull<CompanionGraphMarker>())
        assertNull(koin.getOrNull<WearDataLayerClient>())
    }

    @Test
    fun `companion startup excludes standalone radio and heavy application services`() {
        startCompanionKoin()

        val koin = checkNotNull(application).koin
        assertNull(koin.getOrNull<RadioController>())
        assertNull(koin.getOrNull<MeshtasticDatabase>())
        assertNull(koin.getOrNull<HttpClient>())
        assertNull(koin.getOrNull<UsbManager>())
        assertNull(koin.getOrNull<BleScanner>())
        assertNull(koin.getOrNull<WorkManager>())
        assertNull(koin.getOrNull<MeshServiceOrchestrator>())
    }

    private fun startCompanionKoin() {
        application?.close()
        application = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(companionModules())
        }
    }

    private fun startStandaloneKoin() {
        application?.close()
        application = koinApplication {
            androidContext(ApplicationProvider.getApplicationContext())
            modules(commonModules() + WearStandaloneRadioModule().module())
        }
    }

    private fun companionModules() = commonModules() + WearCompanionModule().module()

    private fun commonModules() = listOf(
        WearCommonModule().module(),
        CoreCommonModule().coreCommonModule(),
        CoreDiModule().coreDiModule(),
        CorePrefsModule().corePrefsModule(),
        CorePrefsAndroidModule().corePrefsModule(),
    )
}
