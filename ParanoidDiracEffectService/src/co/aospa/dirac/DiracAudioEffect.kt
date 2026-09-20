/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.media.audiofx.AudioEffect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.UUID

class DiracAudioEffect : AudioEffect(EFFECT_TYPE_NULL, DIRAC_UUID, 0, 0) {

    fun setParam(id: Int, value: Float) {
        val payload = ByteBuffer.allocate(8)
            .order(ByteOrder.BIG_ENDIAN)
            .putInt(id)
            .putFloat(value)
            .array()
        checkStatus(setParameter(CMD_SET_PARAM, payload))
    }

    fun setConfig(config: String) {
        val payload = "$config\u0000".toByteArray(StandardCharsets.US_ASCII)
        checkStatus(setParameter(CMD_SET_CONFIG, payload))
    }

    companion object {
        private val DIRAC_UUID = UUID.fromString("ae737c63-f2c0-5457-909e-1e940c91b67b")
        private const val CMD_SET_CONFIG = 0x10012de0
        private const val CMD_SET_PARAM = 0x10012de1
    }
}
