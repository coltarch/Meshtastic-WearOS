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
@file:Suppress("ModifierMissing")

package org.meshtastic.wear.presentation

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Process
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.wear.compose.foundation.pager.HorizontalPager
import androidx.wear.compose.foundation.pager.rememberPagerState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.HorizontalPageIndicator
import androidx.wear.compose.material3.HorizontalPagerScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.error
import org.meshtastic.core.resources.retry
import org.meshtastic.wear.di.WearStartupState
import org.meshtastic.wear.di.WearStartupStatus
import org.meshtastic.wear.notifications.EXTRA_CONTACT_KEY
import org.meshtastic.wear.presentation.theme.MeshWatchTheme

private const val MAIN_PAGER_ROUTE = "main_pager"
private const val NODE_DETAILS_ROUTE = "node_detail/{nodeNum}"
private const val COMPANION_PAGE_COUNT = 3
internal const val NODE_DETAILS_UNAVAILABLE_ROUTE = "node_detail/unavailable"

internal fun nodeDetailsRoute(nodeNum: Int): String = "node_detail/$nodeNum"

// ─────────────────────────────────────────────────────────────
//  Activity
// ─────────────────────────────────────────────────────────────
class MainActivity : ComponentActivity() {

    /**
     * A tapped notification's conversation, held until it has been navigated to: the nav host is not up in `onCreate`.
     */
    private val pendingContactKey = mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingContactKey.value = intent?.getStringExtra(EXTRA_CONTACT_KEY)
        setContent {
            val navController = rememberSwipeDismissableNavController()
            val startupState: WearStartupState = koinInject()
            val startupStatus by startupState.status.collectAsStateWithLifecycle()
            val contactKey by pendingContactKey
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {}

            // Asked once and never blocking on it: without it the watch simply posts no notifications.
            LaunchedEffect(Unit) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }

            // The conversation a notification opened, after the host exists rather than from `onCreate`.
            LaunchedEffect(contactKey, startupStatus) {
                if (contactKey != null && startupStatus != WearStartupStatus.Loading) {
                    navController.navigate("chat_view/$contactKey")
                    pendingContactKey.value = null
                }
            }

            MeshWatchTheme {
                AppScaffold {
                    when (startupStatus) {
                        WearStartupStatus.Loading -> WearStartupLoading()

                        WearStartupStatus.Failed -> WearStartupFailure(onRetry = ::restartApp)

                        is WearStartupStatus.Ready ->
                            WearNavigationHost(
                                navController = navController,
                                mainPagerContent = {
                                    MainPagerScreen(
                                        onNavigateToChats = { navController.navigate("chats_menu") },
                                        onNavigateToDMs = { navController.navigate("dm_list") },
                                        onThreadSelect = { contactKey ->
                                            navController.navigate("chat_view/$contactKey")
                                        },
                                        onNodeSelect = { nodeNum -> navController.navigate(nodeDetailsRoute(nodeNum)) },
                                    )
                                },
                                nodeDetailsContent = { nodeNum ->
                                    val nodeDetailsViewModel: NodeDetailsViewModel = koinViewModel()
                                    NodeDetailsScreen(
                                        nodeNum = nodeNum,
                                        onOpenOnPhone = nodeDetailsViewModel::openOnPhone,
                                    )
                                },
                            )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingContactKey.value = intent.getStringExtra(EXTRA_CONTACT_KEY)
    }

    private fun restartApp() {
        startActivity(
            Intent(this, RestartActivity::class.java).putExtra(RestartActivity.EXTRA_PROCESS_ID, Process.myPid()),
        )
    }
}

@Composable
private fun WearStartupLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

@Composable
private fun WearStartupFailure(onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        androidx.compose.foundation.layout.Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stringResource(Res.string.error), color = MaterialTheme.colorScheme.error)
            Button(onClick = onRetry) { Text(stringResource(Res.string.retry)) }
        }
    }
}

@Composable
internal fun WearNavigationHost(
    navController: NavHostController,
    mainPagerContent: @Composable () -> Unit,
    nodeDetailsContent: @Composable (Int?) -> Unit,
) {
    SwipeDismissableNavHost(navController = navController, startDestination = MAIN_PAGER_ROUTE) {
        composable(MAIN_PAGER_ROUTE) { mainPagerContent() }
        composable("chats_menu") {
            ChatsMenuScreen(onChatSelect = { contactKey -> navController.navigate("chat_view/$contactKey") })
        }
        composable("dm_list") {
            DMListScreen(onDMSelect = { contactKey -> navController.navigate("chat_view/$contactKey") })
        }
        composable("chat_view/{contactKey}") { backStackEntry ->
            val contactKey = backStackEntry.arguments?.getString("contactKey").orEmpty()
            ChatScreen(contactKey)
        }
        composable(NODE_DETAILS_ROUTE) { backStackEntry ->
            nodeDetailsContent(backStackEntry.arguments?.getString("nodeNum")?.toIntOrNull())
        }
    }
}

// ─────────────────────────────────────────────────────────────
//  Root pager — Nodes → Messages → Settings
// ─────────────────────────────────────────────────────────────
@Composable
fun MainPagerScreen(
    onNavigateToChats: () -> Unit,
    onNavigateToDMs: () -> Unit,
    onThreadSelect: (String) -> Unit,
    onNodeSelect: (Int) -> Unit,
) {
    val pagerState = rememberPagerState(pageCount = { COMPANION_PAGE_COUNT })
    HorizontalPagerScaffold(
        pagerState = pagerState,
        pageIndicator = {
            HorizontalPageIndicator(pagerState = pagerState, modifier = Modifier.padding(bottom = 4.dp))
        },
    ) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> NodesScreen(onNodeSelect = onNodeSelect)
                1 -> MessagesRootScreen(onNavigateToChats, onNavigateToDMs, onThreadSelect)
                2 -> SettingsScreen()
            }
        }
    }
}
