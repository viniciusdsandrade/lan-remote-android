package com.lanremote.android.data.androidtv

import android.graphics.BitmapFactory
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * OCR da foto da tela da TV com o ML Kit (modelo latino embutido no app: roda no aparelho, sem
 * rede). O reconhecedor é criado a cada leitura e fechado em seguida: são poucas fotos, só no
 * pareamento.
 */
class MlKitScreenCodeReader : ScreenCodeReader {

    override suspend fun read(jpeg: ByteArray): List<String> {
        val bitmap = BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size) ?: return emptyList()
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        try {
            val text = suspendCancellableCoroutine { continuation ->
                recognizer.process(InputImage.fromBitmap(bitmap, 0))
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resumeWithException(it) }
            }
            return PairingCodeReader.candidates(text.textBlocks.flatMap { block -> block.lines.map { it.text } })
        } catch (e: Exception) {
            return emptyList()   // sem leitura, o campo para digitar continua na tela
        } finally {
            recognizer.close()
        }
    }
}
