package fr.mediatheque.journal.senscritique

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `FirebaseSensCritiqueAuthClient` contre un `MockEngine` — jumeau d'`ApiClientTest`. Jamais de
 * vraie requête à Firebase ou SensCritique (brief : « ne sonde pas l'API sans jeton »), tout est
 * simulé.
 */
class FirebaseSensCritiqueAuthClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(graphql: SensCritiqueGraphQLClient = FakeSensCritiqueGraphQLClient(), handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        FirebaseSensCritiqueAuthClient(HttpClient(MockEngine(handler)) { expectSuccess = false }, graphql)

    @Test
    fun `une connexion reussie rend l idToken, le refreshToken, l expiration et le pseudo`() = runTest {
        val api = client {
            respond(
                """{"idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600","displayName":"TheofB"}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val outcome = api.signIn("theo@example.com", "secret") as SignInOutcome.Success
        assertEquals("id-1", outcome.idToken)
        assertEquals("refresh-1", outcome.refreshToken)
        assertEquals(3600L, outcome.expiresInSeconds)
        assertEquals("TheofB", outcome.pseudo)
    }

    @Test
    fun `sans displayName, la connexion reussit quand meme, pseudo nul`() = runTest {
        val api = client {
            respond("""{"idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600"}""", HttpStatusCode.OK, json)
        }
        val outcome = api.signIn("theo@example.com", "secret") as SignInOutcome.Success
        assertNull(outcome.pseudo)
    }

    // Mutation : comparer a une autre chaine que "INVALID_LOGIN_CREDENTIALS" (par exemple la chaine
    // vide) ferait passer ce test en `Unreachable` au lieu d'`InvalidCredentials`.
    @Test
    fun `INVALID_LOGIN_CREDENTIALS devient des identifiants refuses`() = runTest {
        val api = client {
            respond("""{"error":{"code":400,"message":"INVALID_LOGIN_CREDENTIALS"}}""", HttpStatusCode.BadRequest, json)
        }
        assertEquals(SignInOutcome.InvalidCredentials, api.signIn("theo@example.com", "faux"))
    }

    @Test
    fun `une autre erreur Firebase devient injoignable, pas des identifiants refuses`() = runTest {
        val api = client {
            respond("""{"error":{"code":400,"message":"TOO_MANY_ATTEMPTS_TRY_LATER"}}""", HttpStatusCode.BadRequest, json)
        }
        assertEquals(SignInOutcome.Unreachable, api.signIn("theo@example.com", "x"))
    }

    @Test
    fun `une panne reseau devient injoignable, jamais une exception`() = runTest {
        val api = client { throw java.io.IOException("hors ligne") }
        assertEquals(SignInOutcome.Unreachable, api.signIn("theo@example.com", "x"))
    }

    @Test
    fun `une reponse illisible devient injoignable, jamais une exception`() = runTest {
        val api = client { respond("ceci n est pas du json", HttpStatusCode.OK, json) }
        assertEquals(SignInOutcome.Unreachable, api.signIn("theo@example.com", "x"))
    }

    // La verification GraphQL du pseudo ne doit jamais faire echouer une connexion Firebase
    // reussie (brief : « best-effort »). Mutation : propager l'echec de `whoAmI` en
    // `SignInOutcome.Unreachable` ferait echouer cette assertion.
    @Test
    fun `un echec de la verification GraphQL ne fait pas echouer la connexion`() = runTest {
        val graphql = FakeSensCritiqueGraphQLClient(onWhoAmI = { _, _ -> false })
        val api = client(graphql) {
            respond("""{"idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600","displayName":"TheofB"}""", HttpStatusCode.OK, json)
        }
        val outcome = api.signIn("theo@example.com", "secret")
        assertTrue(outcome is SignInOutcome.Success)
        assertEquals(listOf("id-1" to "TheofB"), graphql.whoAmICalls)
    }

    @Test
    fun `un renouvellement reussi rend le nouvel idToken`() = runTest {
        val api = client {
            respond(
                """{"access_token":"a","id_token":"id-2","refresh_token":"refresh-2","expires_in":"3600","token_type":"Bearer"}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val outcome = api.refresh("refresh-1") as RefreshOutcome.Success
        assertEquals("id-2", outcome.idToken)
        assertEquals("refresh-2", outcome.refreshToken)
    }

    @Test
    fun `un renouvellement refuse par le serveur devient Refused`() = runTest {
        val api = client { respond("""{"error":{"message":"INVALID_REFRESH_TOKEN"}}""", HttpStatusCode.BadRequest, json) }
        assertEquals(RefreshOutcome.Refused, api.refresh("refresh-1"))
    }
}
