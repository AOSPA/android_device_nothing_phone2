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
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.AudioSystem
import android.os.SystemProperties
import android.util.Log

class DiracHelper(context: Context) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(AudioManager::class.java)
    private val diracEffect = DiracAudioEffect(::onDeadObject)
    private val config = SystemProperties.get(CONFIG_PROPERTY, DEFAULT_CONFIG)
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
                Log.w(TAG, "Audio server is down; releasing the Dirac effect")
                diracEffect.release()
            }

            override fun onAudioServerUp() {
                if (released) return
                Log.i(TAG, "Audio server reconnected; restoring the Dirac effect")
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
        Log.d(TAG, "Set Dirac enabled=$shouldEnable")
        diracEffect.setEnabled(shouldEnable)
        diracEffect.setParam(PARAM_ID_ENABLE, if (shouldEnable) 1.0f else 0.0f)

        if (shouldEnable) {
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
        if (audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any(::isExternalOutput)) {
            return false
        }

        val mediaDevices = audioManager.getAudioDevicesForAttributes(MEDIA_AUDIO_ATTRIBUTES)
        return mediaDevices.isNotEmpty() &&
            mediaDevices.all { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER }
    }

    private fun isExternalOutput(device: AudioDeviceInfo): Boolean {
        return when (device.type) {
            AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
            AudioDeviceInfo.TYPE_TELEPHONY -> false
            else -> true
        }
    }

    private fun updateVolume(volume: Int) {
        if (released || volume == INVALID_VOLUME || !isBuiltInSpeakerActive()) return

        val minVolume = audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        val maxVolume =
            audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).let {
                if (it > 0) it else FALLBACK_MAX_VOLUME
            }
        val defaultVolume = AudioSystem.getDefaultStreamVolume(AudioSystem.STREAM_MUSIC)

        val normalizedVolume =
            when {
                volume >= maxVolume -> 1.0f
                volume <= minVolume -> -1.0f
                volume > defaultVolume -> {
                    val range = maxVolume - defaultVolume
                    if (range > 0) (volume - defaultVolume).toFloat() / range.toFloat() else 0.0f
                }
                else -> {
                    val range = defaultVolume - minVolume
                    if (range > 0) (volume - defaultVolume).toFloat() / range.toFloat() else 0.0f
                }
            }.coerceIn(-1.0f, 1.0f)

        Log.d(
            TAG,
            "Set Dirac volume=$normalizedVolume for stream volume $volume/$maxVolume (default=$defaultVolume)",
        )
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
        private const val FALLBACK_MAX_VOLUME = 30
        private const val INVALID_VOLUME = -1
        private val MEDIA_AUDIO_ATTRIBUTES =
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).build()
    }
}
