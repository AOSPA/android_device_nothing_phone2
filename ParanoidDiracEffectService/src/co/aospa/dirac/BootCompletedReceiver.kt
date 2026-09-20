/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            Log.d(TAG, "Starting DiracService on boot")
            val serviceIntent = Intent(context, DiracService::class.java)
            context.startService(serviceIntent)
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
