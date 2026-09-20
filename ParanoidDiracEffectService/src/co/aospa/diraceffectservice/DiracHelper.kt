/*
 * Copyright (C) 2026 Paranoid Android
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package co.aospa.diraceffectservice

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.SystemProperties
import android.util.Log

class DiracHelper(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val diracEffect = DiracAudioEffect(::onDeadObject)
    private val config = SystemProperties.get(CONFIG_PROPERTY, DEFAULT_CONFIG)

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
                Log.w(TAG, "Audio server is down; releasing the Dirac effect")
                diracEffect.release()
            }

            override fun onAudioServerUp() {
                Log.i(TAG, "Audio server reconnected; restoring the Dirac effect")
                createAndConfigureEffect()
            }
        }

    init {
        appContext.registerReceiver(
            volumeReceiver,
            IntentFilter(AudioManager.VOLUME_CHANGED_ACTION),
            Context.RECEIVER_NOT_EXPORTED,
        )
        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        audioManager.setAudioServerStateCallback(appContext.mainExecutor, audioServerStateCallback)
        createAndConfigureEffect()
    }

    fun updateRouting() {
        val shouldEnable = isBuiltInSpeakerActive()
        Log.d(TAG, "Set Dirac enabled=$shouldEnable")
        diracEffect.setEnabled(shouldEnable)
        diracEffect.setParam(PARAM_ID_ENABLE, if (shouldEnable) 1.0f else 0.0f)

        if (shouldEnable) {
            updateVolume(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC))
        }
    }

    fun release() {
        appContext.unregisterReceiver(volumeReceiver)
        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        audioManager.clearAudioServerStateCallback()
        diracEffect.release()
    }

    private fun createAndConfigureEffect() {
        if (!diracEffect.create()) return

        diracEffect.setConfig(config)
        updateRouting()
    }

    private fun isBuiltInSpeakerActive(): Boolean {
        var hasBuiltInSpeaker = false
        for (device in audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> hasBuiltInSpeaker = true
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER,
                AudioDeviceInfo.TYPE_USB_ACCESSORY,
                AudioDeviceInfo.TYPE_USB_DEVICE,
                AudioDeviceInfo.TYPE_USB_HEADSET -> return false
            }
        }
        return hasBuiltInSpeaker
    }

    private fun updateVolume(volume: Int) {
        if (volume == INVALID_VOLUME || !isBuiltInSpeakerActive()) return

        val streamMax = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        val maxVolume = if (streamMax > VOLUME_PIVOT) streamMax else FALLBACK_MAX_VOLUME
        val normalizedVolume =
            if (volume > VOLUME_PIVOT) {
                    (volume - VOLUME_PIVOT).toFloat() / (maxVolume - VOLUME_PIVOT)
                } else {
                    (volume - VOLUME_PIVOT).toFloat() / VOLUME_PIVOT
                }
                .coerceIn(-1.0f, 1.0f)

        Log.d(TAG, "Set Dirac volume=$normalizedVolume for stream volume $volume/$maxVolume")
        diracEffect.setParam(PARAM_ID_VOLUME_CONTROL, normalizedVolume)
    }

    private fun onDeadObject() {
        Log.w(TAG, "Dirac effect reported a dead audio server object")
    }

    companion object {
        private const val TAG = "DiracHelper"
        private const val CONFIG_PROPERTY = "persist.vendor.newdirac.cur.config"
        private const val DEFAULT_CONFIG = "MUSIC"
        private const val PARAM_ID_VOLUME_CONTROL = 5
        private const val PARAM_ID_ENABLE = 6
        private const val VOLUME_PIVOT = 18
        private const val FALLBACK_MAX_VOLUME = 30
        private const val INVALID_VOLUME = -1
    }
}
