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
package org.meshtastic.wear

import android.app.ActivityManager
import android.app.Application
import android.content.pm.PackageManager
import android.os.Build
import android.os.Process
import co.touchlab.kermit.Logger
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.loadKoinModules
import org.koin.plugin.module.dsl.startKoin
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.di.CoroutineDispatchers
import org.meshtastic.core.repository.WATCH_COMPANION_SYNC_CAPABILITY
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.wear.di.WearKoinApp
import org.meshtastic.wear.di.WearStartupState
import org.meshtastic.wear.di.selectWearStartupMode
import org.meshtastic.wear.di.wearModeModule
import kotlin.time.Duration.Companion.seconds

private const val RESTART_PROCESS_SUFFIX = ":restart"

class WearApplication : Application() {
    private val applicationJob = SupervisorJob()
    private lateinit var dispatchers: CoroutineDispatchers

    override fun onCreate() {
        super.onCreate()
        if (isRestartProcess()) return

        ContextServices.app = this
        startCommonGraph()
        dispatchers = GlobalContext.get().get()

        val standaloneSupported = packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)
        val startupState = GlobalContext.get().get<WearStartupState>()
        val applicationScope = CoroutineScope(applicationJob + dispatchers.default)
        applicationScope.launch {
            try {
                val storedMode = readStoredMode()
                val startupMode = selectWearStartupMode(storedMode, standaloneSupported)

                updateCompanionCapability(startupMode)
                loadModeGraph(startupMode, storedMode, standaloneSupported)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                Logger.e { "Wear startup initialization failed" }
                startupState.fail()
            }
        }
    }

    private fun isRestartProcess(): Boolean {
        val processName =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                Application.getProcessName()
            } else {
                getSystemService(ActivityManager::class.java)
                    .runningAppProcesses
                    ?.firstOrNull { process -> process.pid == Process.myPid() }
                    ?.processName
            }
        return processName?.endsWith(RESTART_PROCESS_SUFFIX) == true
    }

    private fun startCommonGraph() {
        startKoin<WearKoinApp> { androidContext(this@WearApplication) }
    }

    private suspend fun readStoredMode(): WearOperatingMode = withContext(dispatchers.io) {
        try {
            withTimeoutOrNull(STARTUP_OPERATION_TIMEOUT) {
                GlobalContext.get().get<WatchPrefs>().readOperatingMode()
            }
                ?: WearOperatingMode.COMPANION.also {
                    Logger.w { "Wear startup mode read timed out; using Companion mode" }
                }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Logger.w { "Unable to read Wear startup mode; using Companion mode" }
            WearOperatingMode.COMPANION
        }
    }

    private fun loadModeGraph(mode: WearOperatingMode, restartMode: WearOperatingMode, standaloneSupported: Boolean) {
        val koin = GlobalContext.get()
        loadKoinModules(wearModeModule(mode))
        koin.get<WearStartupState>().initialize(mode, standaloneSupported, restartMode)
    }

    private suspend fun updateCompanionCapability(mode: WearOperatingMode) {
        try {
            withContext(dispatchers.io) {
                val capabilityClient = Wearable.getCapabilityClient(this@WearApplication)
                val task =
                    if (mode == WearOperatingMode.COMPANION) {
                        capabilityClient.addLocalCapability(WATCH_COMPANION_SYNC_CAPABILITY)
                    } else {
                        capabilityClient.removeLocalCapability(WATCH_COMPANION_SYNC_CAPABILITY)
                    }
                val completed =
                    withTimeoutOrNull(STARTUP_OPERATION_TIMEOUT) {
                        task.await()
                        true
                    } ?: false
                if (!completed) {
                    Logger.w { "Companion synchronization availability update timed out" }
                }
            }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            Logger.w { "Unable to update Companion synchronization availability" }
        }
    }

    override fun onTerminate() {
        applicationJob.cancel()
        super.onTerminate()
    }

    private companion object {
        val STARTUP_OPERATION_TIMEOUT = 5.seconds
    }
}
