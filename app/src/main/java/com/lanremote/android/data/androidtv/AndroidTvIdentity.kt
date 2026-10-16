package com.lanremote.android.data.androidtv

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Date
import javax.security.auth.x500.X500Principal

/** O certificado de cliente do app no protocolo do Android TV e a chave privada dele. */
interface ClientIdentity {
    val privateKey: PrivateKey
    val certificate: X509Certificate
}

interface IdentityStore {
    /** A identidade, criada no primeiro uso. */
    fun load(): ClientIdentity

    /** Esquece a chave: o próximo pareamento começa do zero. */
    fun reset()
}

private class StoredIdentity(override val privateKey: PrivateKey, override val certificate: X509Certificate) : ClientIdentity

/**
 * Identidade no AndroidKeyStore. A TV Box guarda o certificado no pareamento, então ele tem que
 * sobreviver a reinícios do app; o Keystore gera o par de chaves e o certificado autoassinado de
 * uma vez, e a chave privada nunca sai dele.
 */
class AndroidTvIdentity : IdentityStore {

    companion object {
        private const val ALIAS = "lanremote_androidtv"
        private const val PROVIDER = "AndroidKeyStore"
        private const val DAY_MILLIS = 86_400_000L
    }

    override fun load(): ClientIdentity {
        try {
            val keyStore = keyStore()
            if (!keyStore.containsAlias(ALIAS)) {
                generate()
            }
            val entry = keyStore.getEntry(ALIAS, null) as? KeyStore.PrivateKeyEntry
                ?: throw AndroidTvError.Certificate("chave não encontrada no Keystore")
            val certificate = entry.certificate as? X509Certificate
                ?: throw AndroidTvError.Certificate("certificado ilegível")
            return StoredIdentity(entry.privateKey, certificate)
        } catch (e: AndroidTvError) {
            throw e
        } catch (e: Exception) {
            throw AndroidTvError.Certificate(e.message ?: e.javaClass.simpleName, e)
        }
    }

    override fun reset() {
        runCatching { keyStore().deleteEntry(ALIAS) }
    }

    private fun keyStore(): KeyStore = KeyStore.getInstance(PROVIDER).apply { load(null) }

    private fun generate() {
        val now = System.currentTimeMillis()
        val spec = KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
            .setAlgorithmParameterSpec(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4))
            .setDigests(
                KeyProperties.DIGEST_NONE, KeyProperties.DIGEST_SHA256,
                KeyProperties.DIGEST_SHA384, KeyProperties.DIGEST_SHA512
            )
            // PKCS#1 com SHA-256 assina o próprio certificado e serve ao TLS 1.2.
            .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1, KeyProperties.SIGNATURE_PADDING_RSA_PSS)
            // No TLS 1.3 a assinatura do cliente (CertificateVerify) é RSA-PSS, mas o Conscrypt faz
            // o enchimento PSS no BoringSSL e pede ao Keystore só a operação RSA crua
            // ("RSA/ECB/NoPadding" com a chave privada = assinar sem enchimento e sem digest).
            // Sem o enchimento NONE autorizado o handshake falharia dentro do Keystore.
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setCertificateSubject(X500Principal("CN=LanRemote"))
            .setCertificateSerialNumber(BigInteger.valueOf(now))
            .setCertificateNotBefore(Date(now - DAY_MILLIS))
            .setCertificateNotAfter(Date(now + 3650L * DAY_MILLIS))
            .build()
        KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, PROVIDER).run {
            initialize(spec)
            generateKeyPair()
        }
    }
}
