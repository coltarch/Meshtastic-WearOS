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
import android.net.ConnectivityManager
import android.net.nsd.NsdManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.DefaultRequest
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.request.header
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single
import org.meshtastic.core.common.BuildConfigProvider
import org.meshtastic.core.network.HttpClientDefaults
import org.meshtastic.core.network.KermitHttpLogger
import org.meshtastic.core.network.configureDefaultRetry
import org.meshtastic.core.network.service.ApiService
import org.meshtastic.core.network.service.ApiServiceImpl

@Module
class WearNetworkModule {
    @Single fun bindApiService(apiServiceImpl: ApiServiceImpl): ApiService = apiServiceImpl

    @Single
    fun provideConnectivityManager(application: Application): ConnectivityManager =
        application.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    @Single
    fun provideNsdManager(application: Application): NsdManager =
        application.getSystemService(Context.NSD_SERVICE) as NsdManager

    @Single
    fun provideHttpClient(json: Json, buildConfigProvider: BuildConfigProvider): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(json) }
        install(DefaultRequest) {
            url(HttpClientDefaults.API_BASE_URL)
            header(
                HttpHeaders.UserAgent,
                "Meshtastic-Wear/${buildConfigProvider.versionName} (${buildConfigProvider.applicationId})",
            )
        }
        install(HttpTimeout) {
            requestTimeoutMillis = HttpClientDefaults.REQUEST_TIMEOUT_MS
            connectTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
            socketTimeoutMillis = HttpClientDefaults.TIMEOUT_MS
        }
        install(HttpRequestRetry) { configureDefaultRetry() }
        if (buildConfigProvider.isDebug) {
            install(Logging) {
                logger = KermitHttpLogger
                level = LogLevel.INFO
            }
        }
    }
}
