/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.UserHandle

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.startServiceAsUser(
            Intent(context, DiracService::class.java),
            UserHandle.CURRENT,
        )
    }
}
