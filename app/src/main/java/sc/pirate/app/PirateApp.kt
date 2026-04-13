package sc.pirate.app

import android.app.Application
import sc.pirate.app.api.ApiClient
import sc.pirate.app.api.SessionStore

class PirateApp : Application() {
    val sessionStore by lazy { SessionStore(this) }
    val apiClient by lazy { ApiClient(sessionStore) }

    override fun onCreate() {
        super.onCreate()
    }
}
