package com.lanremote.android.data.androidtv

/** Como o app se apresenta à TV Box no `remote_configure`. */
data class DeviceInfo(val model: String, val vendor: String, val appVersion: String)

/**
 * Mensagens do protocolo do controle remoto do Android TV v2 (o mesmo do app Google TV).
 *
 * Porta 6467, pareamento: toda mensagem do cliente leva `1: protocol_version = 2` e
 * `2: status = 200`; cada resposta traz `2: status` (200 ok, 402 código errado) e o campo esperado.
 *
 * Porta 6466, controle: cada mensagem é um `RemoteMessage`, em que o número do campo diz o tipo.
 */
object AndroidTvMessages {
    const val CLIENT_NAME = "LanRemote (Android)"

    const val STATUS_OK = 200
    const val STATUS_BAD_SECRET = 402

    // Pareamento (6467)
    const val PAIRING_REQUEST = 10
    const val PAIRING_REQUEST_ACK = 11
    const val PAIRING_OPTION = 20
    const val PAIRING_CONFIGURATION = 30
    const val PAIRING_CONFIGURATION_ACK = 31
    const val PAIRING_SECRET = 40
    const val PAIRING_SECRET_ACK = 41

    // Controle (6466)
    const val REMOTE_CONFIGURE = 1
    const val REMOTE_SET_ACTIVE = 2
    const val REMOTE_ERROR = 3
    const val REMOTE_PING_REQUEST = 8
    const val REMOTE_PING_RESPONSE = 9
    const val REMOTE_KEY_INJECT = 10
    const val REMOTE_START = 40

    private const val PROTOCOL_VERSION = 2
    private const val ENCODING_HEXADECIMAL = 3
    private const val CODE_LENGTH = 6
    private const val ROLE_INPUT = 1
    private const val DIRECTION_SHORT = 3

    /** Recursos anunciados: tecla, IME, energia, volume e link de app (o valor do app Google TV). */
    const val FEATURES = 622

    private fun pairing(body: ProtoWriter.() -> Unit): ByteArray = ProtoWriter.build {
        int(1, PROTOCOL_VERSION)
        int(2, STATUS_OK)
        body()
    }

    /** `service_name` e `client_name`; o nome aparece na tela da TV. */
    fun pairingRequest(clientName: String = CLIENT_NAME): ByteArray = pairing {
        message(PAIRING_REQUEST) {
            string(1, "atvremote")
            string(2, clientName)
        }
    }

    /** Código hexadecimal de 6 símbolos; o app é a entrada (`preferred_role: INPUT`). */
    fun pairingOption(): ByteArray = pairing {
        message(PAIRING_OPTION) {
            message(1) {
                int(1, ENCODING_HEXADECIMAL)
                int(2, CODE_LENGTH)
            }
            int(3, ROLE_INPUT)
        }
    }

    /** Depois do ack desta mensagem o código aparece na TV. */
    fun pairingConfiguration(): ByteArray = pairing {
        message(PAIRING_CONFIGURATION) {
            message(1) {
                int(1, ENCODING_HEXADECIMAL)
                int(2, CODE_LENGTH)
            }
            int(2, ROLE_INPUT)
        }
    }

    fun pairingSecret(secret: ByteArray): ByteArray = pairing {
        message(PAIRING_SECRET) { bytes(1, secret) }
    }

    fun configureReply(device: DeviceInfo): ByteArray = ProtoWriter.build {
        message(REMOTE_CONFIGURE) {
            int(1, FEATURES)
            message(2) {
                string(1, device.model)
                string(2, device.vendor)
                int(3, 1)
                string(4, "1")
                string(5, "lanremote")
                string(6, device.appVersion)
            }
        }
    }

    fun setActiveReply(): ByteArray = ProtoWriter.build {
        message(REMOTE_SET_ACTIVE) { int(1, FEATURES) }
    }

    fun pingReply(value: Long): ByteArray = ProtoWriter.build {
        message(REMOTE_PING_RESPONSE) { int(1, value) }
    }

    /** Toque curto numa tecla (keycode do Android, ex.: 26 = POWER). */
    fun keyInject(keyCode: Int): ByteArray = ProtoWriter.build {
        message(REMOTE_KEY_INJECT) {
            int(1, keyCode)
            int(2, DIRECTION_SHORT)
        }
    }
}
