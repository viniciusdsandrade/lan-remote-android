package com.lanremote.android.data.androidtv

/**
 * Lê o código de pareamento na foto da tela da TV (consulta `screen_image` do ROAP), para ninguém
 * precisar digitá-lo. O OCR fica em [MlKitScreenCodeReader]; aqui, a parte sem Android: das linhas
 * lidas, as leituras possíveis de um código de 6 hexadecimais, com as confusões comuns do OCR como
 * alternativas. O dígito verificador do código ([BoxPairing.accepts]) escolhe a certa.
 */
interface ScreenCodeReader {
    /** Leituras candidatas do código numa foto JPEG da tela; vazio se não houver código legível. */
    suspend fun read(jpeg: ByteArray): List<String>
}

object PairingCodeReader {
    private val mixUps = mapOf(
        'O' to listOf('0', 'D'), '0' to listOf('D'), 'D' to listOf('0'),
        '8' to listOf('B'), 'B' to listOf('8'),
        'I' to listOf('1'), 'L' to listOf('1'), 'S' to listOf('5'), 'G' to listOf('6'), 'Z' to listOf('2')
    )

    /** Das linhas de texto lidas na tela, os códigos de 6 hexadecimais possíveis, sem repetição. */
    fun candidates(lines: List<String>): List<String> {
        val codes = mutableListOf<String>()
        for (line in lines) {
            val compact = line.uppercase().filterNot { it.isWhitespace() }
            if (compact.length != 6) continue
            var readings = listOf("")
            for (character in compact) {
                val options = listOf(character) + mixUps[character].orEmpty()
                readings = readings.flatMap { prefix -> options.map { prefix + it } }
            }
            readings.filter { reading -> reading.all { it in '0'..'9' || it in 'A'..'F' } && reading !in codes }
                .forEach { codes.add(it) }
        }
        return codes
    }
}
