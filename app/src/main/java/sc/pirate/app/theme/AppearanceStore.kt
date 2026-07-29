package sc.pirate.app.theme

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private val Context.appearanceDataStore: DataStore<Preferences> by preferencesDataStore(name = "pirate_appearance")
private val KEY_APPEARANCE_MODE = stringPreferencesKey("appearance_mode")

enum class AppearanceMode(val storageValue: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
    ;

    fun usesDarkTheme(systemInDarkTheme: Boolean): Boolean = when (this) {
        System -> systemInDarkTheme
        Light -> false
        Dark -> true
    }

    companion object {
        fun fromStorage(value: String?): AppearanceMode =
            entries.firstOrNull { it.storageValue == value?.trim()?.lowercase() } ?: System
    }
}

class AppearanceStore(private val context: Context) {
    fun observe(): Flow<AppearanceMode> = context.appearanceDataStore.data
        .map { preferences -> AppearanceMode.fromStorage(preferences[KEY_APPEARANCE_MODE]) }
        .distinctUntilChanged()

    suspend fun set(mode: AppearanceMode) {
        context.appearanceDataStore.edit { preferences ->
            preferences[KEY_APPEARANCE_MODE] = mode.storageValue
        }
    }
}
