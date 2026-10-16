package com.lanremote.android.system

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Gerenciador de feedback háptico de baixa latência para resposta tátil instantânea nos botões.
 */
class HapticFeedbackHelper(private val context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Feedback tátil sutil de clique (< 10ms).
     */
    fun performClick() {
        vibrate(durationMillis = 12, amplitude = VibrationEffect.DEFAULT_AMPLITUDE, predefinedEffect = VibrationEffect.EFFECT_CLICK)
    }

    /**
     * Feedback tátil mais firme para botões de ação destrutiva ou primária (Power, Home).
     */
    fun performHeavyClick() {
        vibrate(durationMillis = 25, amplitude = 200, predefinedEffect = VibrationEffect.EFFECT_HEAVY_CLICK)
    }

    /**
     * Feedback ultraleve para passos contínuos (volume, setas).
     */
    fun performTick() {
        vibrate(durationMillis = 8, amplitude = 70, predefinedEffect = VibrationEffect.EFFECT_TICK)
    }

    /**
     * Dois toques firmes: a ação não aconteceu (ex.: tecla com a TV desligada) ou pede confirmação.
     */
    fun performWarning() {
        pattern(longArrayOf(0, 22, 80, 22), intArrayOf(0, 200, 0, 200))
    }

    /**
     * Toque leve seguido de um firme: deu certo (TV ligou, pareamento concluído).
     */
    fun performSuccess() {
        pattern(longArrayOf(0, 14, 70, 26), intArrayOf(0, 110, 0, 255))
    }

    /**
     * Três toques firmes: falhou.
     */
    fun performError() {
        pattern(longArrayOf(0, 24, 60, 24, 60, 24), intArrayOf(0, 255, 0, 255, 0, 255))
    }

    private fun pattern(timings: LongArray, amplitudes: IntArray) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            vib.vibrate(VibrationEffect.createWaveform(timings, amplitudes, -1))
        } catch (ignored: Exception) {}
    }

    private fun vibrate(durationMillis: Long, amplitude: Int, predefinedEffect: Int) {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                vib.vibrate(VibrationEffect.createPredefined(predefinedEffect))
            } else {
                vib.vibrate(VibrationEffect.createOneShot(durationMillis, amplitude))
            }
        } catch (e: Exception) {
            try {
                @Suppress("DEPRECATION")
                vib.vibrate(durationMillis)
            } catch (ignored: Exception) {}
        }
    }
}
