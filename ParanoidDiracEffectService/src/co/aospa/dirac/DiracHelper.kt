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
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemProperties
import android.util.Log

class DiracHelper(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager by lazy { appContext.getSystemService(AudioManager::class.java) }
    private val diracEffect by lazy { DiracAudioEffect() }
    private val config by lazy { SystemProperties.get(CONFIG_PROPERTY, DEFAULT_CONFIG) }
    @Volatile private var released = false

    private val volumeReceiver =
        object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                if (intent.action != AudioManager.VOLUME_CHANGED_ACTION) return
                if (
                    intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_TYPE, -1) !=
                        AudioManager.STREAM_MUSIC
                ) {
                    return
                }

                updateVolume(
                    intent.getIntExtra(AudioManager.EXTRA_VOLUME_STREAM_VALUE, INVALID_VOLUME)
                )
            }
        }

    private val audioDeviceCallback =
        object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
                updateRouting()
            }

            override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) {
                updateRouting()
            }
        }

    private val audioServerStateCallback =
        object : AudioManager.AudioServerStateCallback() {
            override fun onAudioServerDown() {
                if (released) return
                Log.w(TAG, "Audio server down; releasing effect")
                diracEffect.release()
            }

            override fun onAudioServerUp() {
                if (released) return
                Log.i(TAG, "Audio server reconnected; restoring effect")
                createAndConfigureEffect()
            }
        }

    private val mediaRoutingListener =
        AudioManager.OnDevicesForAttributesChangedListener { _, _ -> updateRouting() }

    init {
        appContext.registerReceiver(
            volumeReceiver,
            IntentFilter(AudioManager.VOLUME_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        audioManager.addOnDevicesForAttributesChangedListener(
            MEDIA_AUDIO_ATTRIBUTES,
            appContext.mainExecutor,
            mediaRoutingListener,
        )
        audioManager.setAudioServerStateCallback(appContext.mainExecutor, audioServerStateCallback)
        createAndConfigureEffect()
    }

    fun updateRouting() {
        if (released) return
        val shouldEnable = isBuiltInSpeakerActive()
        Log.d(TAG, "updateRouting: shouldEnable=$shouldEnable")
        diracEffect.setEnabled(shouldEnable)
        diracEffect.setParam(PARAM_ID_ENABLE, if (shouldEnable) 1.0f else 0.0f)

        if (shouldEnable) {
            diracEffect.setConfig(config)
            updateVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
    }

    fun release() {
        if (released) return
        released = true
        appContext.unregisterReceiver(volumeReceiver)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.removeOnDevicesForAttributesChangedListener(mediaRoutingListener)
        audioManager.clearAudioServerStateCallback()
        diracEffect.release()
    }

    private fun createAndConfigureEffect() {
        if (released) return
        if (!diracEffect.create()) return

        diracEffect.setConfig(config)
        updateRouting()
    }

    private fun isBuiltInSpeakerActive(): Boolean {
        val mediaDevices = audioManager.getAudioDevicesForAttributes(MEDIA_AUDIO_ATTRIBUTES)
        return mediaDevices.isNotEmpty() &&
            mediaDevices.all { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    private fun updateVolume(volume: Int) {
        if (released || volume == INVALID_VOLUME || !isBuiltInSpeakerActive()) return

        val maxVolume =
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).let {
                if (it > 0) it.toFloat() else FALLBACK_MAX_VOLUME
            }
        val pivot = maxVolume * VOLUME_PIVOT_RATIO

        val normalizedVolume = when {
            volume >= maxVolume -> 1.0f
            volume <= 0 -> -1.0f
            volume > pivot -> (volume.toFloat() - pivot) / (maxVolume - pivot)
            else -> (volume.toFloat() - pivot) / pivot
        }.coerceIn(-1.0f, 1.0f)

        Log.d(TAG, "updateVolume: norm=$normalizedVolume (vol=$volume/$maxVolume, pivot=$pivot)")
        diracEffect.setParam(PARAM_ID_VOLUME_CONTROL, normalizedVolume)
    }

    companion object {
        private const val TAG = "DiracHelper"
        private const val CONFIG_PROPERTY = "persist.vendor.newdirac.cur.config"
        private const val DEFAULT_CONFIG = "MUSIC"
        private const val PARAM_ID_VOLUME_CONTROL = 5
        private const val PARAM_ID_ENABLE = 6
        private const val VOLUME_PIVOT_RATIO = 0.6f
        private const val FALLBACK_MAX_VOLUME = 30.0f
        private const val INVALID_VOLUME = -1
        private val MEDIA_AUDIO_ATTRIBUTES =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }
}
