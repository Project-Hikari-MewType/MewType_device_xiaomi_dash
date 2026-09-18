/*
 * SPDX-FileCopyrightText: The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.app.Activity
import android.os.Bundle
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import com.xiaomi.settings.R

/**
 * Toggle-only entry: tapping the Settings row flips forced HTSR immediately
 * without opening a sub page. Same key as the QS tile; both stay in sync.
 */
class TouchSettingsActivity : Activity() {

    companion object {
        private const val TAG = "TouchToggle"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val forced =
            Settings.System.getIntForUser(
                contentResolver,
                TouchReportRateService.SETTING_KEY,
                1,
                UserHandle.USER_CURRENT,
            ) == 1
        val next = if (forced) 0 else 1
        if (DEBUG) Log.d(TAG, "toggle: $forced -> ${next == 1}")
        Settings.System.putIntForUser(
            contentResolver,
            TouchReportRateService.SETTING_KEY,
            next,
            UserHandle.USER_CURRENT,
        )
        TouchReportRateService.applyReportRate(this)
        Toast.makeText(
            this,
            if (next == 1) getString(R.string.touch_tile_forced)
            else getString(R.string.touch_tile_auto),
            Toast.LENGTH_SHORT,
        ).show()
        finish()
    }
}
