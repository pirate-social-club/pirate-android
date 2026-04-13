package sc.pirate.app.api

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import sc.pirate.app.api.model.SessionExchangeResponse

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "pirate_session")

private val KEY_SESSION = stringPreferencesKey("session_json")

class SessionStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun get(): SessionExchangeResponse? {
        val prefs = context.sessionDataStore.data.first()
        val raw = prefs[KEY_SESSION] ?: return null
        return try {
            json.decodeFromString<SessionExchangeResponse>(raw)
        } catch (_: Exception) {
            null
        }
    }

    fun observe(): Flow<SessionExchangeResponse?> =
        context.sessionDataStore.data.map { prefs ->
            val raw = prefs[KEY_SESSION] ?: return@map null
            try {
                json.decodeFromString<SessionExchangeResponse>(raw)
            } catch (_: Exception) {
                null
            }
        }

    suspend fun set(session: SessionExchangeResponse) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_SESSION] = json.encodeToString(SessionExchangeResponse.serializer(), session)
        }
    }

    suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_SESSION)
        }
    }

    suspend fun getAccessToken(): String? = get()?.accessToken
}
