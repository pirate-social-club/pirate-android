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
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.Json
import sc.pirate.app.api.model.SessionExchangeResponse

private val Context.sessionDataStore: DataStore<Preferences> by preferencesDataStore(name = "pirate_session")

private val KEY_SESSION = stringPreferencesKey("session_json")

class SessionStore(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    @Volatile private var cachedSession: SessionExchangeResponse? = null
    @Volatile private var cacheLoaded: Boolean = false

    suspend fun get(): SessionExchangeResponse? {
        if (cacheLoaded) {
            val cached = cachedSession
            if (cached != null && SessionExpiry.isExpired(cached.accessToken)) {
                clear()
                return null
            }
            return cached
        }

        val prefs = context.sessionDataStore.data.first()
        val raw = prefs[KEY_SESSION]
        val session = raw?.let {
            try {
                json.decodeFromString<SessionExchangeResponse>(it)
            } catch (_: Exception) {
                null
            }
        }
        if (session != null && SessionExpiry.isExpired(session.accessToken)) {
            clear()
            return null
        }
        cachedSession = session
        cacheLoaded = true
        return session
    }

    fun observe(): Flow<SessionExchangeResponse?> =
        context.sessionDataStore.data.map { prefs ->
            val raw = prefs[KEY_SESSION] ?: return@map null
            val session = try {
                json.decodeFromString<SessionExchangeResponse>(raw)
            } catch (_: Exception) {
                null
            }
            session?.takeUnless { SessionExpiry.isExpired(it.accessToken) }
        }.onEach {
            cachedSession = it
            cacheLoaded = true
        }

    suspend fun set(session: SessionExchangeResponse) {
        context.sessionDataStore.edit { prefs ->
            prefs[KEY_SESSION] = json.encodeToString(SessionExchangeResponse.serializer(), session)
        }
        cachedSession = session
        cacheLoaded = true
    }

    suspend fun clear() {
        context.sessionDataStore.edit { prefs ->
            prefs.remove(KEY_SESSION)
        }
        cachedSession = null
        cacheLoaded = true
    }

    suspend fun getAccessToken(): String? = get()?.accessToken
}
