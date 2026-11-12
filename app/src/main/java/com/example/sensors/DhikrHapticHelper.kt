package com.example.sensors

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.HapticFeedbackConstants
import android.view.View

class DhikrHapticHelper(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    fun performCountFeedback(
        view: View?,
        newCount: Long,
        dailyTarget: Long,
        enabled: Boolean
    ) {
        if (!enabled) return
        when {
            newCount > 0L && newCount == dailyTarget -> performTargetGoalPulse(view, enabled)
            newCount > 0L && newCount % 33L == 0L -> performSubMilestonePulse(view, enabled)
            else -> performBeadTap(view, enabled)
        }
    }

    fun performBeadTap(view: View?, enabled: Boolean) {
        if (!enabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        vibrateSafely(
            api29Effect = { VibrationEffect.createPredefined(VibrationEffect.EFFECT_TICK) },
            api26Effect = { VibrationEffect.createOneShot(14L, 95) },
            legacyMillis = 14L
        )
    }

    fun performSubMilestonePulse(view: View?, enabled: Boolean) {
        if (!enabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        vibrateSafely(
            api29Effect = { VibrationEffect.createWaveform(longArrayOf(0L, 18L, 55L, 28L), -1) },
            api26Effect = { VibrationEffect.createWaveform(longArrayOf(0L, 18L, 55L, 28L), -1) },
            legacyPattern = longArrayOf(0L, 18L, 55L, 28L)
        )
    }

    fun performTargetGoalPulse(view: View?, enabled: Boolean) {
        if (!enabled) return
        val feedbackConst = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            HapticFeedbackConstants.CONFIRM
        } else {
            HapticFeedbackConstants.LONG_PRESS
        }
        view?.performHapticFeedback(feedbackConst)
        vibrateSafely(
            api29Effect = { VibrationEffect.createWaveform(longArrayOf(0L, 20L, 45L, 25L, 45L, 42L), -1) },
            api26Effect = { VibrationEffect.createWaveform(longArrayOf(0L, 20L, 45L, 25L, 45L, 42L), -1) },
            legacyPattern = longArrayOf(0L, 20L, 45L, 25L, 45L, 42L)
        )
    }

    fun performSoftUndo(view: View?, enabled: Boolean) {
        if (!enabled) return
        view?.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        vibrateSafely(
            api29Effect = { VibrationEffect.createOneShot(10L, 55) },
            api26Effect = { VibrationEffect.createOneShot(10L, 55) },
            legacyMillis = 10L
        )
    }

    private fun vibrateSafely(
        api29Effect: () -> VibrationEffect,
        api26Effect: () -> VibrationEffect,
        legacyMillis: Long? = null,
        legacyPattern: LongArray? = null
    ) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return
        runCatching {
            when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q -> vib.vibrate(api29Effect())
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> vib.vibrate(api26Effect())
                else -> {
                    @Suppress("DEPRECATION")
                    if (legacyPattern != null) {
                        vib.vibrate(legacyPattern, -1)
                    } else if (legacyMillis != null) {
                        vib.vibrate(legacyMillis)
                    }
                }
            }
        }
    }
}
