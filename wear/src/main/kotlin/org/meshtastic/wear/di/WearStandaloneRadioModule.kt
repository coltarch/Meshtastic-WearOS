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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.work.WorkManager
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Module
import org.koin.core.annotation.Named
import org.koin.core.annotation.Single
import org.meshtastic.core.ble.BleConnectionFactory
import org.meshtastic.core.ble.BleScanner
import org.meshtastic.core.ble.BluetoothRepository
import org.meshtastic.core.ble.di.CoreBleAndroidModule
import org.meshtastic.core.ble.di.CoreBleModule
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.common.di.CoreCommonModule
import org.meshtastic.core.data.di.CoreDataAndroidModule
import org.meshtastic.core.data.di.CoreDataModule
import org.meshtastic.core.database.di.CoreDatabaseAndroidModule
import org.meshtastic.core.database.di.CoreDatabaseModule
import org.meshtastic.core.datastore.di.CoreDatastoreAndroidModule
import org.meshtastic.core.datastore.di.CoreDatastoreModule
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.network.di.CoreNetworkModule
import org.meshtastic.core.repository.AppWidgetUpdater
import org.meshtastic.core.repository.DataPair
import org.meshtastic.core.repository.PlatformAnalytics
import org.meshtastic.core.repository.RadioController
import org.meshtastic.core.repository.RadioPrefs
import org.meshtastic.core.repository.RadioTransportFactory
import org.meshtastic.core.service.di.CoreServiceAndroidModule
import org.meshtastic.core.service.di.CoreServiceModule
import org.meshtastic.core.takserver.di.CoreTakServerModule
import org.meshtastic.core.ui.di.CoreUiModule
import org.meshtastic.wear.BuildConfig
import org.meshtastic.wear.standalone.StandaloneRadioViewModel
import org.meshtastic.wear.standalone.WearBleRadioTransportFactory

/**
 * Direct-radio graph loaded only when Standalone mode was selected before process startup. It is intentionally not
 * included by [WearCompanionModule], so none of these definitions are available during Companion startup.
 */
@Module(
    includes =
    [
        CoreCommonModule::class,
        CoreBleModule::class,
        CoreBleAndroidModule::class,
        CoreDataModule::class,
        CoreDataAndroidModule::class,
        org.meshtastic.core.domain.di.CoreDomainModule::class,
        CoreDatabaseModule::class,
        CoreDatabaseAndroidModule::class,
        org.meshtastic.core.repository.di.CoreRepositoryModule::class,
        CoreDatastoreModule::class,
        CoreDatastoreAndroidModule::class,
        CoreServiceModule::class,
        CoreServiceAndroidModule::class,
        CoreNetworkModule::class,
        CoreTakServerModule::class,
        CoreUiModule::class,
    ],
)
class WearStandaloneRadioModule {
    @Single internal fun provideStandaloneGraphMarker(): StandaloneGraphMarker = StandaloneGraphMarker

    @Single
    fun provideRadioTransportFactory(
        bleScanner: BleScanner,
        bluetoothRepository: BluetoothRepository,
        connectionFactory: BleConnectionFactory,
        dispatchers: CoroutineDispatchers,
    ): RadioTransportFactory =
        WearBleRadioTransportFactory(bleScanner, bluetoothRepository, connectionFactory, dispatchers)

    @KoinViewModel
    fun provideStandaloneRadioViewModel(
        bleScanner: Lazy<BleScanner>,
        bluetoothRepository: Lazy<BluetoothRepository>,
        radioController: Lazy<RadioController>,
        radioPrefs: Lazy<RadioPrefs>,
        transportFactory: Lazy<RadioTransportFactory>,
        startupState: WearStartupState,
    ): StandaloneRadioViewModel = StandaloneRadioViewModel(
        bleScanner,
        bluetoothRepository,
        radioController,
        radioPrefs,
        transportFactory,
        startupState,
    )

    @Single
    @Named("ProcessLifecycle")
    fun provideProcessLifecycle(): Lifecycle = ProcessLifecycleOwner.get().lifecycle

    @Single
    fun provideBuildConfigProvider(): BuildConfigProvider = object : BuildConfigProvider {
        override val isDebug: Boolean = BuildConfig.DEBUG
        override val applicationId: String = BuildConfig.APPLICATION_ID
        override val versionCode: Int = BuildConfig.VERSION_CODE
        override val versionName: String = BuildConfig.VERSION_NAME
        override val absoluteMinFwVersion: String = BuildConfig.ABS_MIN_FW_VERSION
        override val minFwVersion: String = BuildConfig.MIN_FW_VERSION
    }

    @Single fun provideWorkManager(context: Application): WorkManager = WorkManager.getInstance(context)

    @Single
    fun providePlatformAnalytics(): PlatformAnalytics = object : PlatformAnalytics {
        override fun track(event: String, vararg properties: DataPair) = Unit

        override fun setDeviceAttributes(firmwareVersion: String, model: String) = Unit

        override val isPlatformServicesAvailable: Boolean = false
    }

    @Single
    fun provideAppWidgetUpdater(): AppWidgetUpdater = object : AppWidgetUpdater {
        override suspend fun updateAll() = Unit
    }
}

internal data object StandaloneGraphMarker
