package fr.mediatheque.journal.api

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl

/**
 * Un seul cookie à garder : la session. OkHttp applique `Secure`, le domaine et
 * l'expiration ; nous, on le fait survivre au lancement suivant, et on le
 * remplace à chaque `Set-Cookie` — la session est glissante, le serveur la
 * renouvelle à chaque appel.
 */
class SessionCookieJar(private val store: SessionStore) : CookieJar {

    // Un cookie retrouvé au magasin peut avoir expiré pendant que l'application ne tournait pas :
    // sans ce filtre, `hasSession` en hériterait et répondrait vrai pour une session qui n'existe
    // plus, comme `loadForRequest` le filtre déjà à chaque requête.
    @Volatile
    private var cached: Cookie? = store.read()?.let { saved ->
        runCatching { json.decodeFromString<StoredCookie>(saved).toCookie() }.getOrNull()
    }?.takeIf { it.expiresAt > System.currentTimeMillis() }

    // Recalculée à chaque lecture, pas figée à la construction : un cookie valide au lancement
    // mais dont l'échéance passe avant qu'on ne consulte `hasSession` ne doit pas rester vrai.
    val hasSession: Boolean get() = cached?.let { it.expiresAt > System.currentTimeMillis() } == true

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cookie = cookies.lastOrNull { it.name == COOKIE_NAME } ?: return
        if (cookie.expiresAt <= System.currentTimeMillis() || cookie.value.isEmpty()) {
            clear()
        } else {
            cached = cookie
            store.write(json.encodeToString(StoredCookie.serializer(), cookie.toStored()))
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookie = cached ?: return emptyList()
        if (cookie.expiresAt <= System.currentTimeMillis()) {
            clear()
            return emptyList()
        }
        return if (cookie.matches(url)) listOf(cookie) else emptyList()
    }

    /** À la déconnexion, et sur tout `401` : la session n'existe plus. */
    fun clear() {
        cached = null
        store.write(null)
    }

    companion object {
        const val COOKIE_NAME = "mediatheque_session"
        private val json = Json
    }
}

@Serializable
private data class StoredCookie(
    val name: String,
    val value: String,
    val expiresAt: Long,
    val domain: String,
    val path: String,
    val secure: Boolean,
    val httpOnly: Boolean,
    val hostOnly: Boolean,
)

private fun Cookie.toStored() = StoredCookie(name, value, expiresAt, domain, path, secure, httpOnly, hostOnly)

private fun StoredCookie.toCookie(): Cookie = Cookie.Builder()
    .name(name).value(value).expiresAt(expiresAt).path(path)
    .apply {
        if (hostOnly) hostOnlyDomain(domain) else domain(domain)
        if (secure) secure()
        if (httpOnly) httpOnly()
    }
    .build()
