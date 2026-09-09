package fr.mediatheque.journal

import android.app.Application
import android.content.Context
import fr.mediatheque.journal.api.ApiClient
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.PreferencesSessionStore
import fr.mediatheque.journal.api.SessionCookieJar

/** Sept dépendances, construites une fois. Un cadre d'injection serait plus gros que l'application. */
class AppContainer(context: Context) {
    val cookieJar = SessionCookieJar(PreferencesSessionStore(context))
    val api: JournalApi = ApiClient(BuildConfig.API_BASE_URL, ApiClient.okHttpEngine(cookieJar))
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
