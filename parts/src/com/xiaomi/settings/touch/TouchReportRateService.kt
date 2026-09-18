/*
 * SPDX-FileCopyrightText: The LineageOS Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.xiaomi.settings.touch

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.UserHandle
import android.provider.Settings
import android.util.Log
import com.xiaomi.settings.utils.FileUtils

class TouchReportRateService : Service() {

    private val handler = Handler(Looper.getMainLooper())

    private val settingObserver =
        object : ContentObserver(handler) {
            override fun onChange(selfChange: Boolean) {
                if (DEBUG) Log.d(TAG, "SettingObserver: onChange")
                // Setting changes always apply immediately — user just asked.
                applyReportRate(this@TouchReportRateService, force = true)
            }
        }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (DEBUG) Log.d(TAG, "onReceive: ${intent.action}")
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    // The touch driver resets the rate on resume after this
                    // broadcast is delivered; re-asserting here (and again
                    // after RESUME_SETTLE_DELAY_MS so the reset cannot win)
                    // makes the forced value stick across unlock and first
                    // unlock after reboot alike. Only runs when persistent is
                    // on; users who turned persistence off opted out of this
                    // auto-restore on purpose.
                    if (isPersistent(this@TouchReportRateService)) {
                        if (DEBUG) Log.d(TAG, "Screen on, restoring touch report rate")
                        applyReportRate(this@TouchReportRateService)
                        handler.postDelayed(
                            { applyReportRate(this@TouchReportRateService) },
                            RESUME_SETTLE_DELAY_MS,
                        )
                    }
                }
            }
        }

    override fun onCreate() {
        super.onCreate()
        if (DEBUG) Log.d(TAG, "onCreate")
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(SETTING_KEY),
            false,
            settingObserver,
            UserHandle.USER_CURRENT,
        )
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(PERSISTENT_SETTING_KEY),
            false,
            settingObserver,
            UserHandle.USER_CURRENT,
        )
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        if (isPersistent(this)) {
            applyReportRate(this)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (DEBUG) Log.d(TAG, "onStartCommand")
        return START_STICKY
    }

    override fun onDestroy() {
        if (DEBUG) Log.d(TAG, "onDestroy")
        handler.removeCallbacksAndMessages(null)
        contentResolver.unregisterContentObserver(settingObserver)
        unregisterReceiver(screenStateReceiver)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "TouchReportRateService"
        private val DEBUG = Log.isLoggable(TAG, Log.DEBUG)

        const val SETTING_KEY = "touch_high_sampling_rate"
        const val PERSISTENT_SETTING_KEY = "touch_high_sampling_rate_persistent"
        private const val DEFAULT_VALUE = 1
        private const val DEFAULT_PERSISTENT_VALUE = 1
        private const val PROC_PATH = "/proc/xm_htc_report_rate"
        private const val VALUE_NORMAL = "120"
        private const val VALUE_HIGH = "240"
        private const val RESUME_SETTLE_DELAY_MS = 2000L

        fun isReportRateWritable(): Boolean = FileUtils.isFileWritable(PROC_PATH)

        /**
         * Apply the current setting to the proc node. Caller controls whether
         * this is an "always write" path (user-driven, [force] = true) or a
         * "respect persistence" path (boot/screen-on auto-restore, [force] =
         * false). Kept as a single helper so the two call sites stay in sync.
         */
        fun applyReportRate(context: Context, force: Boolean = false) {
            if (!force && !isPersistent(context)) {
                if (DEBUG) Log.d(TAG, "applyReportRate: skipped (persistent off)")
                return
            }
            val value =
                Settings.System.getIntForUser(
                    context.contentResolver,
                    SETTING_KEY,
                    DEFAULT_VALUE,
                    UserHandle.USER_CURRENT,
                )
            if (DEBUG) Log.d(TAG, "applyReportRate: $value")
            FileUtils.writeLine(PROC_PATH, if (value == 1) VALUE_HIGH else VALUE_NORMAL)
            HtsrSwitchProvider.notifyChanged(context)
        }

        fun startService(context: Context) {
            context.startServiceAsUser(
                Intent(context, TouchReportRateService::class.java),
                UserHandle.CURRENT,
            )
        }

        private fun isPersistent(context: Context): Boolean =
            Settings.System.getIntForUser(
                context.contentResolver,
                PERSISTENT_SETTING_KEY,
                DEFAULT_PERSISTENT_VALUE,
                UserHandle.USER_CURRENT,
            ) == 1
    }
}
