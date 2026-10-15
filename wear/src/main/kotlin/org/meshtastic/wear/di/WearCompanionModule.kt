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
import org.koin.core.annotation.KoinViewModel
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.wear.data.PlayServicesWearDataLayerClient
import org.meshtastic.wear.data.WearCompanionSyncStateHolder
import org.meshtastic.wear.data.WearDataLayerClient
import org.meshtastic.wear.presentation.ChannelsViewModel
import org.meshtastic.wear.presentation.ChatViewModel
import org.meshtastic.wear.presentation.CompanionConnectionViewModel
import org.meshtastic.wear.presentation.MessagesViewModel
import org.meshtastic.wear.presentation.NodeDetailsViewModel
import org.meshtastic.wear.presentation.NodesViewModel
import org.meshtastic.wear.presentation.model.WearableChannelRepository
import org.meshtastic.wear.presentation.model.WearableMessageRepository
import org.meshtastic.wear.presentation.model.WearableNodeRepository
import org.meshtastic.wear.presentation.model.WearableQuickReplyRepository

/** Phone-synchronized graph. No direct-radio, database, network, USB, or worker definitions are included. */
@Module
class WearCompanionModule {
    @Single fun provideCompanionGraphMarker(): CompanionGraphMarker = CompanionGraphMarker

    @Single
    fun provideDataLayerClient(
        application: Application,
        syncStateHolder: WearCompanionSyncStateHolder,
    ): WearDataLayerClient = PlayServicesWearDataLayerClient(application, syncStateHolder)

    @KoinViewModel
    fun provideChannelsViewModel(
        repository: WearableChannelRepository,
        dataLayerClient: WearDataLayerClient,
    ): ChannelsViewModel = ChannelsViewModel(repository, dataLayerClient)

    @KoinViewModel
    fun provideMessagesViewModel(
        repository: WearableMessageRepository,
        channelRepository: WearableChannelRepository,
        dataLayerClient: WearDataLayerClient,
    ): MessagesViewModel = MessagesViewModel(repository, channelRepository, dataLayerClient)

    @KoinViewModel
    fun provideChatViewModel(
        repository: WearableMessageRepository,
        channelRepository: WearableChannelRepository,
        quickReplyRepository: WearableQuickReplyRepository,
        dataLayerClient: WearDataLayerClient,
    ): ChatViewModel = ChatViewModel(repository, channelRepository, quickReplyRepository, dataLayerClient)

    @KoinViewModel
    fun provideNodesViewModel(
        repository: WearableNodeRepository,
        dataLayerClient: WearDataLayerClient,
    ): NodesViewModel = NodesViewModel(repository, dataLayerClient)

    @KoinViewModel
    fun provideNodeDetailsViewModel(dataLayerClient: WearDataLayerClient): NodeDetailsViewModel =
        NodeDetailsViewModel(dataLayerClient)

    @KoinViewModel
    fun provideCompanionConnectionViewModel(dataLayerClient: WearDataLayerClient): CompanionConnectionViewModel =
        CompanionConnectionViewModel(dataLayerClient)
}

data object CompanionGraphMarker
