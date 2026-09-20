/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.dirac

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log

class DiracHelper(private val context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private var diracEffect: DiracAudioEffect? = null
    private var isEffectEnabled: Boolean? = null
    private var released = false

    private val volumeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == AudioManager.VOLUME_CHANGED_ACTION &&
                intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) == AudioManager.STREAM_MUSIC
            ) {
                updateRouting()
            }
        }
    }

    private val mediaRoutingListener =
        AudioManager.OnDevicesForAttributesChangedListener { _, _ -> updateRouting() }

    init {
        context.registerReceiver(
            volumeReceiver,
            IntentFilter(AudioManager.VOLUME_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        audioManager.addOnDevicesForAttributesChangedListener(
            MEDIA_AUDIO_ATTRIBUTES,
            context.mainExecutor,
            mediaRoutingListener,
        )
        updateRouting()
    }

    fun updateRouting() {
        if (released) return
        try {
            val enabled = shouldEnableDirac()
            val effect = diracEffect ?: if (enabled) DiracAudioEffect().also {
                diracEffect = it
            } else return
            if (isEffectEnabled != enabled) {
                effect.checkStatus(effect.setEnabled(enabled))
                effect.setParam(PARAM_ID_ENABLE, if (enabled) 1.0f else 0.0f)
                if (enabled) {
                    effect.setConfig(CONFIG_MUSIC)
                }
                isEffectEnabled = enabled
            }

            if (enabled) {
                updateVolume(effect)
            }
        } catch (e: RuntimeException) {
            Log.e(TAG, "Unable to update Dirac effect", e)
            diracEffect?.release()
            diracEffect = null
            isEffectEnabled = null
        }
    }

    fun release() {
        released = true
        context.unregisterReceiver(volumeReceiver)
        audioManager.removeOnDevicesForAttributesChangedListener(mediaRoutingListener)
        diracEffect?.release()
        diracEffect = null
        isEffectEnabled = null
    }

    private fun shouldEnableDirac(): Boolean {
        val devices = audioManager.getAudioDevicesForAttributes(MEDIA_AUDIO_ATTRIBUTES)
        return devices.isNotEmpty() && devices.all {
            it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE ||
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER ||
                it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE
        }
    }

    private fun updateVolume(effect: DiracAudioEffect) {
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        if (volume < 0) return

        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).toFloat()
        val pivot = maxVolume * VOLUME_PIVOT_RATIO

        val normalizedVolume = when {
            volume >= maxVolume -> 1.0f
            volume <= 0 -> -1.0f
            volume > pivot -> (volume - pivot) / (maxVolume - pivot)
            else -> (volume - pivot) / pivot
        }.coerceIn(-1.0f, 1.0f)

        effect.setParam(PARAM_ID_VOLUME_CONTROL, normalizedVolume)
    }

    companion object {
        private const val TAG = "DiracHelper"
        private const val CONFIG_MUSIC = "MUSIC"
        private const val PARAM_ID_VOLUME_CONTROL = 5
        private const val PARAM_ID_ENABLE = 6
        private const val VOLUME_PIVOT_RATIO = 0.6f
        private val MEDIA_AUDIO_ATTRIBUTES =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }
}
