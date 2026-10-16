package com.lanremote.android.data.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

// Protobuf mínimo para o protocolo do controle remoto do Android TV v2: só os tipos que as duas
// portas da TV Box usam (varint e length-delimited). Não há .proto no projeto: os campos são
// escritos à mão, com os números documentados em AndroidTvMessages.kt.

class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(value: Long): ProtoWriter {
        var v = value
        while (v and 0x7FL.inv() != 0L) {
            out.write(((v and 0x7F) or 0x80).toInt())
            v = v ushr 7
        }
        out.write(v.toInt())
        return this
    }

    fun int(field: Int, value: Long): ProtoWriter = varint((field shl 3).toLong()).varint(value)

    fun int(field: Int, value: Int): ProtoWriter = int(field, value.toLong())

    fun bytes(field: Int, value: ByteArray): ProtoWriter {
        varint((field shl 3 or 2).toLong())
        varint(value.size.toLong())
        out.write(value)
        return this
    }

    fun string(field: Int, value: String): ProtoWriter = bytes(field, value.encodeToByteArray())

    fun message(field: Int, content: ProtoWriter.() -> Unit): ProtoWriter = bytes(field, build(content))

    fun toByteArray(): ByteArray = out.toByteArray()

    companion object {
        fun build(block: ProtoWriter.() -> Unit): ByteArray = ProtoWriter().apply(block).toByteArray()

        /** A mensagem com o próprio tamanho na frente (varint), como as duas portas esperam. */
        fun framed(payload: ByteArray): ByteArray = ProtoWriter().varint(payload.size.toLong()).toByteArray() + payload
    }
}

/**
 * Campos inteiros (wire type 0) e length-delimited (wire type 2) de uma mensagem; vale o último.
 * Os tipos 1 e 5 (64 e 32 bits) são pulados.
 */
class ProtoMessage(raw: ByteArray) {
    private val ints = HashMap<Int, Long>()
    private val blobs = HashMap<Int, ByteArray>()

    init {
        var i = 0
        fun varint(): Long {
            var result = 0L
            var shift = 0
            while (i < raw.size && shift < 64) {
                val byte = raw[i++].toInt() and 0xFF
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
            throw AndroidTvError.Rejected("mensagem malformada")
        }

        fun skip(count: Int) {
            if (i + count > raw.size) throw AndroidTvError.Rejected("mensagem truncada")
            i += count
        }

        while (i < raw.size) {
            val key = varint()
            val field = (key ushr 3).toInt()
            when ((key and 7).toInt()) {
                0 -> ints[field] = varint()
                1 -> skip(8)
                2 -> {
                    val length = varint()
                    if (length < 0 || length > raw.size - i) throw AndroidTvError.Rejected("mensagem truncada")
                    blobs[field] = raw.copyOfRange(i, i + length.toInt())
                    i += length.toInt()
                }
                5 -> skip(4)
                else -> throw AndroidTvError.Rejected("tipo de campo desconhecido")
            }
        }
    }

    fun has(field: Int): Boolean = field in ints || field in blobs

    fun long(field: Int): Long = ints[field] ?: 0L

    fun int(field: Int): Int = long(field).toInt()

    fun bytes(field: Int): ByteArray? = blobs[field]

    fun string(field: Int): String? = blobs[field]?.decodeToString()

    fun message(field: Int): ProtoMessage? = blobs[field]?.let { runCatching { ProtoMessage(it) }.getOrNull() }
}

/**
 * Lê a próxima mensagem enquadrada (tamanho em varint + conteúdo) de [input], bloqueando até ela
 * chegar inteira. Fim do fluxo antes ou no meio dela: [EOFException].
 */
fun readFrame(input: InputStream): ByteArray {
    var length = 0L
    var shift = 0
    while (true) {
        val byte = input.read()
        if (byte < 0) throw EOFException("a TV Box encerrou a conexão")
        length = length or ((byte and 0x7F).toLong() shl shift)
        if (byte and 0x80 == 0) break
        shift += 7
        if (shift >= 35) throw AndroidTvError.Rejected("tamanho de mensagem inválido")
    }
    if (length > MAX_FRAME_BYTES) throw AndroidTvError.Rejected("mensagem grande demais")
    val payload = ByteArray(length.toInt())
    var read = 0
    while (read < payload.size) {
        val count = input.read(payload, read, payload.size - read)
        if (count < 0) throw EOFException("a TV Box encerrou a conexão")
        read += count
    }
    return payload
}

/** Nenhuma mensagem dos dois protocolos chega perto disso; mais que isso é lixo na linha. */
private const val MAX_FRAME_BYTES = 1L shl 20
