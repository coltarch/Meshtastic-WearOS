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

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.test.core.app.ApplicationProvider
import androidx.wear.compose.material3.MaterialTheme
import org.junit.runner.RunWith
import org.meshtastic.core.common.ContextServices
import org.meshtastic.core.repository.WearOperatingMode
import org.meshtastic.core.resources.Res
import org.meshtastic.core.resources.experimental_badge
import org.meshtastic.core.resources.getString
import org.meshtastic.core.resources.standalone_radio_experimental_description
import org.meshtastic.core.resources.standalone_radio_mode
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.BeforeTest
import kotlin.test.Test

@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SettingsScreenTest {

    @BeforeTest
    fun setUp() {
        ContextServices.app = ApplicationProvider.getApplicationContext()
    }

    @Test
    fun `standalone row shows static experimental labeling without a master switch`() = runComposeUiTest {
        setOperatingModeContent(standaloneSupported = true)

        onNodeWithText(getString(Res.string.standalone_radio_mode)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.experimental_badge)).assertIsDisplayed()
        onNodeWithText(getString(Res.string.standalone_radio_experimental_description)).assertIsDisplayed()
        onAllNodesWithText("Experimental features").assertCountEquals(0)
        onNodeWithTag(STANDALONE_RADIO_SWITCH_TEST_TAG).assertIsEnabled()
    }

    @Test
    fun `experimental label does not override standalone hardware availability`() = runComposeUiTest {
        setOperatingModeContent(standaloneSupported = false)

        onNodeWithText(getString(Res.string.experimental_badge)).assertIsDisplayed()
        onNodeWithTag(STANDALONE_RADIO_SWITCH_TEST_TAG).assertIsNotEnabled()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setOperatingModeContent(standaloneSupported: Boolean) {
        setContent {
            MaterialTheme {
                OperatingModeSection(
                    state = WearSettingsUiState(configuredMode = WearOperatingMode.COMPANION),
                    standaloneSupported = standaloneSupported,
                    onModeRequest = {},
                    onRestartApp = {},
                )
            }
        }
    }
}
