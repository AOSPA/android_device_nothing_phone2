/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserManager
import android.util.Log

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val userManager = context.getSystemService(UserManager::class.java)
        if (userManager?.isSystemUser == false) return

        when (intent.action) {
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_BOOT_COMPLETED -> {
                Log.d(TAG, "Starting DiracService on ${intent.action}")
                val serviceIntent = Intent(context, DiracService::class.java)
                context.startService(serviceIntent)
            }
        }
    }

    companion object {
        private const val TAG = "BootCompletedReceiver"
    }
}
