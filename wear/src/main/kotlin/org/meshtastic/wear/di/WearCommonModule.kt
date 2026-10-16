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

import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.repository.WatchPrefs
import org.meshtastic.wear.data.WearCompanionSyncStateHolder
import org.meshtastic.wear.presentation.SettingsViewModel
import org.meshtastic.wear.presentation.model.WearableChannelRepository
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import org.meshtastic.wear.presentation.model.WearableNodeRepository
import org.meshtastic.wear.presentation.model.WearableQuickReplyRepository
import org.meshtastic.wear.presentation.model.WearableRadioStatusRepository

/** Small graph shared by both modes: startup state, Watch settings, and process-local synchronized-data stores. */
@Module
class WearCommonModule {
    @Single fun provideStartupState(): WearStartupState = WearStartupState()

    @Single fun provideNodeRepository(): WearableNodeRepository = WearableNodeRepository()

    @Single fun provideMessageRepository(): WearableMessageRepository = WearableMessageRepository()

    @Single fun provideChannelRepository(): WearableChannelRepository = WearableChannelRepository()

    @Single fun provideQuickReplyRepository(): WearableQuickReplyRepository = WearableQuickReplyRepository()

    @Single fun provideRadioStatusRepository(): WearableRadioStatusRepository = WearableRadioStatusRepository()

    @Single fun provideCompanionSyncStateHolder(): WearCompanionSyncStateHolder = WearCompanionSyncStateHolder()

    @KoinViewModel
    fun provideSettingsViewModel(watchPrefs: WatchPrefs): SettingsViewModel = SettingsViewModel(watchPrefs)
}
