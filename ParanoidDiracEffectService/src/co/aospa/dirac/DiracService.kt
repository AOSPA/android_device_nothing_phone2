/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.app.Service
import android.content.Intent
import android.os.IBinder

class DiracService : Service() {
    private lateinit var diracHelper: DiracHelper

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        diracHelper = DiracHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        diracHelper.updateRouting()
        return START_STICKY
    }

    override fun onDestroy() {
        diracHelper.release()
        super.onDestroy()
    }
}
