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

import org.koin.core.annotation.KoinApplication
import org.koin.core.module.Module
import org.meshtastic.core.common.di.CoreCommonModule
import org.meshtastic.core.di.di.CoreDiModule
import org.meshtastic.core.prefs.di.CorePrefsAndroidModule
import org.meshtastic.core.prefs.di.CorePrefsModule
import org.meshtastic.core.repository.WearOperatingMode

@KoinApplication(
    modules =
    [
        WearCommonModule::class,
        CoreCommonModule::class,
        CoreDiModule::class,
        CorePrefsModule::class,
        CorePrefsAndroidModule::class,
    ],
)
object WearKoinApp

/** Returns the one mode-specific graph selected before the application begins resolving screen dependencies. */
internal fun wearModeModule(mode: WearOperatingMode): Module = when (mode) {
    WearOperatingMode.COMPANION -> WearCompanionModule().module()
    WearOperatingMode.STANDALONE_RADIO -> WearStandaloneRadioModule().module()
}
