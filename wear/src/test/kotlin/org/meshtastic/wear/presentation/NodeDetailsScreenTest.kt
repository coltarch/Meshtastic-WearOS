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
package org.meshtastic.wear.presentation

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.navigation.NavHostController
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import org.junit.runner.RunWith
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.model.WearableNode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.battery
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.node_unavailable
import org.meshtastic.core.resources.open_on_phone
import org.meshtastic.core.resources.snr
import org.meshtastic.core.resources.wear_node_card_content_description
import org.meshtastic.wear.presentation.model.WearableNodeRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class NodeDetailsScreenTest {

    @BeforeTest
    fun setUp() {
        ContextServices.app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `tapping a node card reports its numeric ID`() = runComposeUiTest {
        val node = testNode(num = 101, name = "Alpha")
        var selectedNodeNum: Int? = null
        setContent { MaterialTheme { NodeCard(node = node, onNodeSelect = { selectedNodeNum = it }) } }

        onNodeWithText(node.name).performClick()

        runOnIdle { assertEquals(node.num, selectedNodeNum) }
    }

    @Test
    fun `different node cards report their respective numeric IDs`() = runComposeUiTest {
        val first = testNode(num = 101, name = "Alpha")
        val second = testNode(num = 202, name = "Bravo")
        var selectedNodeNum: Int? = null
        setContent {
            MaterialTheme {
                Column {
                    NodeCard(node = first, onNodeSelect = { selectedNodeNum = it })
                    NodeCard(node = second, onNodeSelect = { selectedNodeNum = it })
                }
            }
        }

        onNodeWithText(first.name).performClick()
        runOnIdle { assertEquals(first.num, selectedNodeNum) }

        onNodeWithText(second.name).performClick()
        runOnIdle { assertEquals(second.num, selectedNodeNum) }
    }

    @Test
    fun `a valid route ID displays the matching node`() = runComposeUiTest {
        val repository = WearableNodeRepository()
        val selected = testNode(num = 202, name = "Selected Bravo")
        repository.updateNodes(listOf(testNode(num = 101, name = "Other Alpha"), selected))

        setContent {
            val navController = rememberSwipeDismissableNavController()
            AppScaffold {
                WearNavigationHost(
                    navController = navController,
                    mainPagerContent = {
                        NodeCard(node = selected, onNodeSelect = { navController.navigate(nodeDetailsRoute(it)) })
                    },
                    nodeDetailsContent = { nodeNum ->
                        NodeDetailsScreen(nodeNum = nodeNum, onOpenOnPhone = {}, repository = repository)
                    },
                )
            }
        }

        onNodeWithText(selected.name).performClick()
        onNodeWithText(selected.name).assertIsDisplayed()
        onNodeWithText("!000000ca").assertIsDisplayed()
    }

    @Test
    fun `an invalid route argument displays unavailable without crashing`() = runComposeUiTest {
        val repository = WearableNodeRepository()

        setContent {
            val navController = rememberSwipeDismissableNavController()
            AppScaffold {
                WearNavigationHost(
                    navController = navController,
                    mainPagerContent = {
                        Text(
                            text = "Open invalid route",
                            modifier = Modifier.clickable { navController.navigate("node_detail/not-a-number") },
                        )
                    },
                    nodeDetailsContent = { nodeNum ->
                        NodeDetailsScreen(nodeNum = nodeNum, onOpenOnPhone = {}, repository = repository)
                    },
                )
            }
        }

        onNodeWithText("Open invalid route").performClick()
        onNodeWithText(getString(Res.string.node_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `an absent route argument displays unavailable without crashing`() = runComposeUiTest {
        val repository = WearableNodeRepository()

        setContent {
            val navController = rememberSwipeDismissableNavController()
            AppScaffold {
                WearNavigationHost(
                    navController = navController,
                    mainPagerContent = {
                        Text(
                            text = "Open route without node",
                            modifier = Modifier.clickable { navController.navigate(NODE_DETAILS_UNAVAILABLE_ROUTE) },
                        )
                    },
                    nodeDetailsContent = { nodeNum ->
                        NodeDetailsScreen(nodeNum = nodeNum, onOpenOnPhone = {}, repository = repository)
                    },
                )
            }
        }

        onNodeWithText("Open route without node").performClick()
        onNodeWithText(getString(Res.string.node_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `a valid but missing node displays unavailable`() = runComposeUiTest {
        val repository = WearableNodeRepository()

        setContent {
            val navController = rememberSwipeDismissableNavController()
            AppScaffold {
                WearNavigationHost(
                    navController = navController,
                    mainPagerContent = {
                        Text(
                            text = "Open missing node",
                            modifier = Modifier.clickable { navController.navigate(nodeDetailsRoute(404)) },
                        )
                    },
                    nodeDetailsContent = { nodeNum ->
                        NodeDetailsScreen(nodeNum = nodeNum, onOpenOnPhone = {}, repository = repository)
                    },
                )
            }
        }

        onNodeWithText("Open missing node").performClick()
        onNodeWithText(getString(Res.string.node_unavailable)).assertIsDisplayed()
    }

    @Test
    fun `repository updates refresh the open details screen`() = runComposeUiTest {
        val repository = WearableNodeRepository()
        val original = testNode(num = 101, name = "Original name")
        repository.updateNodes(listOf(original))

        setContent {
            MaterialTheme { NodeDetailsScreen(nodeNum = original.num, onOpenOnPhone = {}, repository = repository) }
        }
        onNodeWithText(original.name).assertIsDisplayed()

        repository.updateNodes(listOf(original.copy(name = "Updated name", shortName = "UP")))

        onNodeWithText("Updated name").assertIsDisplayed()
        onAllNodesWithText(original.name).assertCountEquals(0)
    }

    @Test
    fun `nullable battery and SNR rows are omitted safely`() = runComposeUiTest {
        val node = testNode(num = 101, name = "No telemetry", battery = null, snr = null)

        setContent { MaterialTheme { NodeDetailsContent(node = node) } }

        onAllNodesWithText(getString(Res.string.battery)).assertCountEquals(0)
        onAllNodesWithText(getString(Res.string.snr)).assertCountEquals(0)
    }

    @Test
    fun `zero SNR remains a displayed measurement`() = runComposeUiTest {
        val node = testNode(num = 101, name = "Zero SNR", snr = 0f)

        setContent { MaterialTheme { NodeDetailsContent(node = node) } }

        onNode(hasScrollAction()).performTouchInput { swipeUp() }
        onAllNodesWithText("0.0 dB").assertCountEquals(1)
    }

    @Test
    fun `open on phone sends the displayed node ID`() = runComposeUiTest {
        val node = testNode(num = 202, name = "Phone node")
        var openedNodeNum: Int? = null

        setContent { MaterialTheme { NodeDetailsContent(node = node, onOpenOnPhone = { openedNodeNum = it }) } }

        onNodeWithText(getString(Res.string.open_on_phone)).performClick()

        runOnIdle { assertEquals(node.num, openedNodeNum) }
    }

    @Test
    fun `swipe back from details returns to the main pager`() = runComposeUiTest {
        val repository = WearableNodeRepository()
        val node = testNode(num = 101, name = "Swipe node")
        repository.updateNodes(listOf(node))
        lateinit var navController: NavHostController

        setContent {
            navController = rememberSwipeDismissableNavController()
            AppScaffold {
                WearNavigationHost(
                    navController = navController,
                    mainPagerContent = {
                        NodeCard(node = node, onNodeSelect = { navController.navigate(nodeDetailsRoute(it)) })
                    },
                    nodeDetailsContent = { nodeNum ->
                        NodeDetailsScreen(nodeNum = nodeNum, onOpenOnPhone = {}, repository = repository)
                    },
                )
            }
        }

        onNodeWithText(node.name).performClick()
        onNodeWithText(node.name).assertIsDisplayed()

        onRoot().performTouchInput { swipeRight() }

        onNodeWithText(node.name).assertIsDisplayed()
    }

    @Test
    fun `a node card does not hide its status behind a content description`() = runComposeUiTest {
        val node = testNode(num = 101, name = "Alpha", battery = 82)
        setContent { MaterialTheme { NodeCard(node = node, onNodeSelect = {}) } }

        onNodeWithText(node.name).assertIsDisplayed()
        onNodeWithText("82%").assertIsDisplayed()
        onNodeWithContentDescription(getString(Res.string.wear_node_card_content_description, node.name))
            .assertDoesNotExist()
    }
}

private fun testNode(
    num: Int,
    name: String,
    shortName: String = name.take(2).uppercase(),
    online: Boolean = true,
    battery: Int? = 75,
    snr: Float? = 4.5f,
    lastSeen: Long = 0,
    favorite: Boolean = false,
): WearableNode = WearableNode(
    num = num,
    name = name,
    shortName = shortName,
    online = online,
    battery = battery,
    snr = snr,
    lastSeen = lastSeen,
    favorite = favorite,
)
