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
                applyReportRate(this@TouchReportRateService)
            }
        }

    private val screenStateReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (DEBUG) Log.d(TAG, "onReceive: ${intent.action}")
                if (intent.action == Intent.ACTION_SCREEN_ON) {
                    if (DEBUG) Log.d(TAG, "Screen on, restoring touch report rate")
                    applyReportRate(this@TouchReportRateService)
                    // The touch driver resets the rate on resume after this
                    // broadcast is delivered; re-assert once resume settles
                    // so the reset cannot win. Covers unlock and first
                    // unlock after reboot alike.
                    handler.postDelayed(
                        { applyReportRate(this@TouchReportRateService) },
                        RESUME_SETTLE_DELAY_MS,
                    )
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
        registerReceiver(screenStateReceiver, IntentFilter(Intent.ACTION_SCREEN_ON))
        applyReportRate(this)
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
        private const val DEFAULT_VALUE = 1
        private const val PROC_PATH = "/proc/xm_htc_report_rate"
        private const val VALUE_NORMAL = "120"
        private const val VALUE_HIGH = "240"
        private const val RESUME_SETTLE_DELAY_MS = 2000L

        fun isReportRateWritable(): Boolean = FileUtils.isFileWritable(PROC_PATH)

        fun applyReportRate(context: Context) {
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
    }
}
