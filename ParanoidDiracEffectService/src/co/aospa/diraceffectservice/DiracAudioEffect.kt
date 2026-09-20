/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.diraceffectservice

import android.media.AudioDeviceAttributes
import android.media.AudioDeviceInfo
import android.media.audiofx.AudioEffect
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class DiracAudioEffect(private val onDeadObject: () -> Unit) {
    private var effect: AudioEffect? = null

    @Synchronized
    fun create(): Boolean {
        if (effect != null) return true
        return try {
            // Attach exclusively to the built-in speaker device
            val speakerDevice =
                AudioDeviceAttributes(
                    AudioDeviceAttributes.ROLE_OUTPUT,
                    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
                    "",
                )
            effect = AudioEffect(DIRAC_UUID, speakerDevice)
            Log.d(TAG, "Created Dirac AudioEffect for the built-in speaker")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Dirac AudioEffect", e)
            effect = null
            false
        }
    }

    fun setEnabled(enabled: Boolean): Int {
        val fx = effect ?: return ERROR_NO_INIT
        return try {
            fx.setEnabled(enabled).also { status -> handleStatus(status, "setEnabled($enabled)") }
        } catch (e: Exception) {
            Log.e(TAG, "setEnabled($enabled) failed", e)
            ERROR_NO_INIT
        }
    }

    fun setParam(id: Int, value: Float): Int {
        val fx = effect ?: return -1
        return try {
            val paramBytes =
                ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(id).array()
            val valueBytes =
                ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putFloat(value).array()
            fx.setParameter(paramBytes, valueBytes).also { status ->
                handleStatus(status, "setParameter($id)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setParameter($id) failed", e)
            ERROR_NO_INIT
        }
    }

    fun setConfig(config: String): Int {
        val fx = effect ?: return -1
        return try {
            val paramBytes =
                ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(CMD_SET_CONFIG).array()
            val valueBytes = "$config\u0000".toByteArray(StandardCharsets.US_ASCII)
            fx.setParameter(paramBytes, valueBytes).also { status ->
                handleStatus(status, "setConfig")
            }
        } catch (e: Exception) {
            Log.e(TAG, "setConfig failed", e)
            ERROR_NO_INIT
        }
    }

    @Synchronized
    fun release() {
        try {
            effect?.release()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to release AudioEffect", e)
        } finally {
            effect = null
        }
    }

    private fun handleStatus(status: Int, operation: String) {
        if (status == AudioEffect.SUCCESS) return

        Log.w(TAG, "$operation returned $status")
        if (status == ERROR_DEAD_OBJECT) {
            release()
            onDeadObject()
        }
    }

    companion object {
        private const val TAG = "DiracAudioEffect"
        private val DIRAC_UUID = UUID.fromString("ae737c63-f2c0-5457-909e-1e940c91b67b")
        private const val CMD_SET_CONFIG = 0x10001
        private const val ERROR_DEAD_OBJECT = -7
        private const val ERROR_NO_INIT = -3
    }
}
