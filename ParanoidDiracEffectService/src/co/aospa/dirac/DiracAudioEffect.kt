/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.media.audiofx.AudioEffect
import android.util.Log
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class DiracAudioEffect {
    private var effect: AudioEffect? = null

    @Synchronized
    fun create(): Boolean {
        if (effect != null) return true
        return try {
            effect = AudioEffect(AudioEffect.EFFECT_TYPE_NULL, DIRAC_UUID, 0, 0)
            Log.d(TAG, "Created Dirac AudioEffect on session 0")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create Dirac AudioEffect", e)
            effect = null
            false
        }
    }

    fun setEnabled(enabled: Boolean) {
        val fx = effect ?: return
        try {
            val status = fx.setEnabled(enabled)
            handleStatus(status, "setEnabled($enabled)")
        } catch (e: Exception) {
            Log.e(TAG, "setEnabled($enabled) failed", e)
        }
    }

    fun setParam(id: Int, value: Float) {
        val fx = effect ?: return
        try {
            val paramBytes =
                ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(CMD_SET_PARAM).array()
            val valueBytes =
                ByteBuffer.allocate(8)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(id)
                    .putFloat(value)
                    .array()
            val status = fx.setParameter(paramBytes, valueBytes)
            handleStatus(status, "setParam($id)")
        } catch (e: Exception) {
            Log.e(TAG, "setParam($id) failed", e)
        }
    }

    fun setConfig(config: String) {
        val fx = effect ?: return
        try {
            val paramBytes =
                ByteBuffer.allocate(4).order(ByteOrder.nativeOrder()).putInt(CMD_SET_CONFIG).array()
            val valueBytes = "$config\u0000".toByteArray(StandardCharsets.US_ASCII)
            val status = fx.setParameter(paramBytes, valueBytes)
            handleStatus(status, "setConfig($config)")
        } catch (e: Exception) {
            Log.e(TAG, "setConfig($config) failed", e)
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
        if (status == AudioEffect.ERROR_DEAD_OBJECT) {
            release()
        }
    }

    companion object {
        private const val TAG = "DiracAudioEffect"
        private val DIRAC_UUID = UUID.fromString("ae737c63-f2c0-5457-909e-1e940c91b67b")
        private const val CMD_SET_CONFIG = 0x10012de0
        private const val CMD_SET_PARAM = 0x10012de1
    }
}
