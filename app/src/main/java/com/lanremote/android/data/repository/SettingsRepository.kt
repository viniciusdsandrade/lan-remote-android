package com.lanremote.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lanremote.android.data.model.AndroidTvBox
import com.lanremote.android.data.model.TVConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lan_remote_settings")

class SettingsRepository(private val context: Context) {

    companion object {
        private val KEY_HOST = stringPreferencesKey("tv_host")
        private val KEY_PORT = intPreferencesKey("tv_port")
        private val KEY_PAIRING_KEY = stringPreferencesKey("tv_pairing_key")
        private val KEY_SESSION_ID = stringPreferencesKey("tv_session_id")
        private val KEY_MODEL_NAME = stringPreferencesKey("tv_model_name")

        // TV Box que liga a TV. A chave privada do app fica só no AndroidKeyStore, e o código de
        // pareamento não é gravado.
        private val KEY_BOX_NAME = stringPreferencesKey("box_name")
        private val KEY_BOX_HOST = stringPreferencesKey("box_host")
        private val KEY_BOX_PAIRED = booleanPreferencesKey("box_paired")
        private val KEY_BOX_MAC = stringPreferencesKey("box_mac")
        private val KEY_BOX_BT_MAC = stringPreferencesKey("box_bt_mac")

        const val DEFAULT_HOST = "192.0.2.100"
        const val DEFAULT_PORT = 8080
    }

    val tvConfigFlow: Flow<TVConfig> = context.dataStore.data.map { prefs ->
        TVConfig(
            host = prefs[KEY_HOST] ?: DEFAULT_HOST,
            port = prefs[KEY_PORT] ?: DEFAULT_PORT,
            pairingKey = prefs[KEY_PAIRING_KEY] ?: "",
            sessionId = prefs[KEY_SESSION_ID],
            modelName = prefs[KEY_MODEL_NAME] ?: "LG Smart TV (NetCast)"
        )
    }

    suspend fun saveConfig(config: TVConfig) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = config.host
            prefs[KEY_PORT] = config.port
            prefs[KEY_PAIRING_KEY] = config.pairingKey
            if (config.sessionId != null) {
                prefs[KEY_SESSION_ID] = config.sessionId
            } else {
                prefs.remove(KEY_SESSION_ID)
            }
            prefs[KEY_MODEL_NAME] = config.modelName
        }
    }

    suspend fun updatePairing(pairingKey: String, sessionId: String?) {
        context.dataStore.edit { prefs ->
            prefs[KEY_PAIRING_KEY] = pairingKey
            if (sessionId != null) {
                prefs[KEY_SESSION_ID] = sessionId
            } else {
                prefs.remove(KEY_SESSION_ID)
            }
        }
    }

    /** Sessão nova que a TV deu ao refazer o pareamento com a chave salva (depois de um 401). */
    suspend fun updateSession(sessionId: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_SESSION_ID] = sessionId
        }
    }

    /** Modelo lido da TV ("42LN5700-SA_11"), para o cabeçalho. */
    suspend fun updateModelName(modelName: String) {
        context.dataStore.edit { prefs ->
            prefs[KEY_MODEL_NAME] = modelName
        }
    }

    suspend fun updateHostAndPort(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_PORT] = port
        }
    }

    val tvBoxFlow: Flow<AndroidTvBox?> = context.dataStore.data.map { prefs ->
        val name = prefs[KEY_BOX_NAME]
        val host = prefs[KEY_BOX_HOST]
        if (name == null || host == null) {
            null
        } else {
            AndroidTvBox(
                name = name,
                host = host,
                paired = prefs[KEY_BOX_PAIRED] ?: false,
                mac = prefs[KEY_BOX_MAC],
                bluetoothMac = prefs[KEY_BOX_BT_MAC]
            )
        }
    }

    suspend fun saveTvBox(box: AndroidTvBox) {
        context.dataStore.edit { prefs ->
            prefs[KEY_BOX_NAME] = box.name
            prefs[KEY_BOX_HOST] = box.host
            prefs[KEY_BOX_PAIRED] = box.paired
            if (box.mac != null) prefs[KEY_BOX_MAC] = box.mac else prefs.remove(KEY_BOX_MAC)
            if (box.bluetoothMac != null) prefs[KEY_BOX_BT_MAC] = box.bluetoothMac else prefs.remove(KEY_BOX_BT_MAC)
        }
    }

    suspend fun forgetTvBox() {
        context.dataStore.edit { prefs ->
            prefs.remove(KEY_BOX_NAME)
            prefs.remove(KEY_BOX_HOST)
            prefs.remove(KEY_BOX_PAIRED)
            prefs.remove(KEY_BOX_MAC)
            prefs.remove(KEY_BOX_BT_MAC)
        }
    }
}
