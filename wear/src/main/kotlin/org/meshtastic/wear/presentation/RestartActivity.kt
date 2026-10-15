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

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.os.Process
import android.view.View

private const val RELAUNCH_DELAY_MILLIS = 200L

/** Keeps a visible, user-initiated activity alive while the main Wear process is replaced. */
class RestartActivity : Activity() {
    private var restartStarted = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this).apply { setBackgroundColor(Color.BLACK) })
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus || restartStarted) return

        val mainProcessId = intent.getIntExtra(EXTRA_PROCESS_ID, INVALID_PROCESS_ID)
        val launchIntent =
            packageManager.getLaunchIntentForPackage(packageName)?.apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
                ?: run {
                    finish()
                    return
                }

        restartStarted = true
        if (mainProcessId > 0 && mainProcessId != Process.myPid()) Process.killProcess(mainProcessId)
        window.decorView.postDelayed(
            {
                startActivity(launchIntent)
                finish()
            },
            RELAUNCH_DELAY_MILLIS,
        )
    }

    companion object {
        internal const val EXTRA_PROCESS_ID = "org.meshtastic.wear.extra.PROCESS_ID"
        private const val INVALID_PROCESS_ID = -1
    }
}
