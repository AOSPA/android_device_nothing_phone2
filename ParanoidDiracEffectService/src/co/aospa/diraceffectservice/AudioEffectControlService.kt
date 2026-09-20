/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.diraceffectservice

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class AudioEffectControlService : Service() {
    private lateinit var diracHelper: DiracHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "AudioEffectControlService onCreate")
        diracHelper = DiracHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AudioEffectControlService onStartCommand")
        diracHelper.updateRouting()
        return START_STICKY
    }

    override fun onDestroy() {
        diracHelper.release()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "AudioEffectControlService"
    }
}
