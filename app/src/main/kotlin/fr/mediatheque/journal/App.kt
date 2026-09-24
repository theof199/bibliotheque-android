package fr.mediatheque.journal

import android.app.Application
import android.content.Context
import fr.mediatheque.journal.api.ApiClient
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.PreferencesSessionStore
import fr.mediatheque.journal.api.SessionCookieJar
import fr.mediatheque.journal.search.PreferencesRecentSearchesStore
import fr.mediatheque.journal.search.RecentSearchesStore
import fr.mediatheque.journal.senscritique.GraphQlSensCritiqueAuthClient
import fr.mediatheque.journal.senscritique.KeystoreSensCritiqueStore
import fr.mediatheque.journal.senscritique.KtorSensCritiqueGraphQLClient
import fr.mediatheque.journal.senscritique.SensCritiqueAuthClient
import fr.mediatheque.journal.senscritique.SensCritiqueRatingService
import fr.mediatheque.journal.senscritique.SensCritiqueStore
import fr.mediatheque.journal.senscritique.SensCritiqueSync
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import okhttp3.CookieJar

/** Construites une fois. Un cadre d'injection serait plus gros que l'application. */
class AppContainer(context: Context) {
    val cookieJar = SessionCookieJar(PreferencesSessionStore(context))
    val api: JournalApi = ApiClient(BuildConfig.API_BASE_URL, ApiClient.okHttpEngine(cookieJar))

    /** Les dix dernières recherches (point 6 de la revue du 24 septembre 2026) : locales, jamais envoyées au back. */
    val recentSearches: RecentSearchesStore = PreferencesRecentSearchesStore(context)

    // SensCritique (brief du 14 septembre 2026) : le client Ktor existant, réutilisé avec un
    // client sans cookie jar (`CookieJar.NO_COOKIES`) — jamais celui de la médiathèque, dont le
    // cookie n'a rien à faire vers SensCritique. Le délai de 10 s (brief §6) est posé ici, une
    // fois pour tous les appels SensCritique (connexion et GraphQL, le même point d'entrée) —
    // jamais par un `withTimeout` local à chaque appel, qui se comporte mal sous une horloge de
    // test virtuelle.
    private val sensCritiqueHttp = HttpClient(ApiClient.okHttpEngine(CookieJar.NO_COOKIES)) {
        expectSuccess = false
        install(ContentNegotiation) { json(ApiClient.ApiJson) }
        install(HttpTimeout) { requestTimeoutMillis = 10_000 }
    }
    val sensCritiqueStore: SensCritiqueStore = KeystoreSensCritiqueStore(context)
    private val sensCritiqueGraphQL = KtorSensCritiqueGraphQLClient(sensCritiqueHttp)
    val sensCritiqueAuthClient: SensCritiqueAuthClient = GraphQlSensCritiqueAuthClient(sensCritiqueHttp)
    private val sensCritiqueService = SensCritiqueRatingService(sensCritiqueStore, sensCritiqueGraphQL)
    val sensCritiqueSync = SensCritiqueSync(sensCritiqueService, sensCritiqueStore)
}

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
