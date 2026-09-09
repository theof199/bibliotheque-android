package fr.mediatheque.journal.api

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionCookieJarTest {
    private val api = "https://mini-mediatheque.fr/api/auth/login".toHttpUrl()
    private val ailleurs = "https://example.org/".toHttpUrl()

    private fun cookie(value: String = "abc", expiresIn: Long = 3_600_000) = Cookie.Builder()
        .name(SessionCookieJar.COOKIE_NAME).value(value)
        .hostOnlyDomain("mini-mediatheque.fr").path("/")
        .expiresAt(System.currentTimeMillis() + expiresIn)
        .secure().httpOnly().build()

    @Test
    fun `garde le cookie de session et le renvoie a la meme origine`() {
        val jar = SessionCookieJar(InMemorySessionStore())
        jar.saveFromResponse(api, listOf(cookie()))

        assertEquals(listOf("abc"), jar.loadForRequest(api).map { it.value })
        assertTrue(jar.hasSession)
    }

    @Test
    fun `ne l envoie pas a une autre origine`() {
        val jar = SessionCookieJar(InMemorySessionStore())
        jar.saveFromResponse(api, listOf(cookie()))

        assertTrue(jar.loadForRequest(ailleurs).isEmpty())
    }

    @Test
    fun `ignore les cookies qui ne sont pas la session`() {
        val jar = SessionCookieJar(InMemorySessionStore())
        val autre = Cookie.Builder().name("autre").value("x").hostOnlyDomain("mini-mediatheque.fr").build()
        jar.saveFromResponse(api, listOf(autre))

        assertFalse(jar.hasSession)
    }

    @Test
    fun `survit a un nouveau lancement par le magasin`() {
        val store = InMemorySessionStore()
        SessionCookieJar(store).saveFromResponse(api, listOf(cookie("persistant")))

        val relance = SessionCookieJar(store)
        assertEquals(listOf("persistant"), relance.loadForRequest(api).map { it.value })
    }

    @Test
    fun `remplace le cookie quand le serveur en renvoie un — la session glissante`() {
        val jar = SessionCookieJar(InMemorySessionStore())
        jar.saveFromResponse(api, listOf(cookie("ancien")))
        jar.saveFromResponse(api, listOf(cookie("renouvele")))

        assertEquals(listOf("renouvele"), jar.loadForRequest(api).map { it.value })
    }

    @Test
    fun `un cookie expire efface la session`() {
        val store = InMemorySessionStore()
        val jar = SessionCookieJar(store)
        jar.saveFromResponse(api, listOf(cookie("vivant")))
        jar.saveFromResponse(api, listOf(cookie("mort", expiresIn = -1_000)))

        assertTrue(jar.loadForRequest(api).isEmpty())
        assertNull(store.read())
    }

    @Test
    fun `clear efface la memoire et le magasin`() {
        val store = InMemorySessionStore()
        val jar = SessionCookieJar(store)
        jar.saveFromResponse(api, listOf(cookie()))
        jar.clear()

        assertFalse(jar.hasSession)
        assertNull(store.read())
    }
}
