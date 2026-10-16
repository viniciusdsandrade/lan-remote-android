package com.lanremote.android.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
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

    suspend fun updateHostAndPort(host: String, port: Int) {
        context.dataStore.edit { prefs ->
            prefs[KEY_HOST] = host
            prefs[KEY_PORT] = port
        }
    }
}
