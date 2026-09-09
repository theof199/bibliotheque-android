package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.JournalCreateBody
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ApiClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    // `respond` est une extension de `MockRequestHandleScope` : le gestionnaire
    // doit avoir ce receveur, sinon les appels ci-dessous ne compilent pas.
    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        ApiClient("https://mini-mediatheque.fr/api", MockEngine(handler))

    private suspend fun erreur(block: suspend () -> Unit): ApiError {
        try { block() } catch (e: ApiError) { return e }
        fail("ApiError attendue")
        error("inatteignable")
    }

    @Test
    fun `colle le chemin a la base sans doubler ni perdre le prefixe`() = runTest {
        var url = ""
        val api = client { request ->
            url = request.url.toString()
            respond("""{"items":[],"next_cursor":null}""", HttpStatusCode.OK, json)
        }
        api.searchMovies("chihiro")
        assertEquals("https://mini-mediatheque.fr/api/search?type=movie&q=chihiro", url)
    }

    @Test
    fun `pose l en-tete client sur toute requete`() = runTest {
        var entete: String? = null
        val api = client { request ->
            entete = request.headers["X-Mediatheque-Client"]
            respond("""{"user":{"id":"1","pseudo":"a","identity_color":"#000000"}}""", HttpStatusCode.OK, json)
        }
        api.me()
        assertEquals("android", entete)
    }

    @Test
    fun `un 401 devient une ApiError non authentifiee avec le message du back`() = runTest {
        val api = client {
            respond("""{"code":"UNAUTHENTICATED","message":"Connecte-toi d’abord.","retryable":false}""", HttpStatusCode.Unauthorized, json)
        }
        val e = erreur { api.me() }
        assertTrue(e.isUnauthenticated)
        assertEquals("Connecte-toi d’abord.", e.message)
        assertEquals("UNAUTHENTICATED", e.code)
    }

    @Test
    fun `un 429 porte Retry-After en secondes`() = runTest {
        val api = client {
            respond(
                """{"code":"RATE_LIMITED","message":"Trop de tentatives.","retryable":true}""",
                HttpStatusCode.TooManyRequests,
                headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.RetryAfter to listOf("900")),
            )
        }
        val e = erreur { api.login("a", "b") }
        assertTrue(e.isRateLimited)
        assertEquals(900, e.retryAfterSeconds)
        assertTrue(e.retryable)
    }

    @Test
    fun `une reponse sans enveloppe garde un message lisible`() = runTest {
        val api = client { respond("<html>502</html>", HttpStatusCode.BadGateway) }
        val e = erreur { api.me() }
        assertEquals(502, e.status)
        assertEquals("L’API a répondu 502.", e.message)
    }

    @Test
    fun `une panne reseau devient une ApiError NETWORK, retryable`() = runTest {
        val api = client { throw IOException("connexion refusée") }
        val e = erreur { api.me() }
        assertEquals(ApiError.NETWORK_CODE, e.code)
        assertNull(e.status)
        assertTrue(e.retryable)
        assertEquals(ApiError.NETWORK_MESSAGE, e.message)
    }

    // Le back ne remonte la note au suivi que si le corps porte `rating`
    // (`if (body.rating !== undefined)`) : un `rating: null` systématique
    // défait cette garde. `reactions`, elle, n'a pas ce statut particulier :
    // sa valeur par défaut (`[]`) doit tout de même sortir dans le corps.
    @Test
    fun `un rating nul est omis du corps, mais reactions vide y reste`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond(ITEM, HttpStatusCode.Created, json)
        }
        api.addViewing(
            JournalCreateBody(media_id = "m", finished_at = "2026-09-03", rating = null, reactions = emptyList(), comment = null),
        )
        assertFalse(corps, corps.contains("\"rating\""))
        assertFalse(corps, corps.contains("\"comment\""))
        assertTrue(corps, corps.contains("\"reactions\":[]"))
    }

    // `patchViewing` prend un `JsonObject` construit par l'appelant : un
    // `JsonNull` explicite doit ressortir tel quel, pour effacer un
    // commentaire — à la différence d'un champ absent, qui voudrait dire
    // « ne touche pas ».
    @Test
    fun `un PATCH avec un commentaire nul explicite l efface exactement`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond(ITEM, HttpStatusCode.OK, json)
        }
        api.patchViewing("e0000000-0000-4000-8000-000000000002", buildJsonObject { put("comment", JsonNull) })
        assertEquals("""{"comment":null}""", corps)
    }

    @Test
    fun `un 204 se lit sans corps`() = runTest {
        val api = client { respond("", HttpStatusCode.NoContent) }
        api.deleteViewing("x")
    }

    private companion object {
        const val ITEM = """{"entry":{"id":"e","media_id":"m","finished_at":"2026-09-03","rating":null},"media":{"id":"m","title":"T","cover_url":null,"year":null,"director":null},"carnet":{"reactions":[],"comment":null}}"""
    }
}
