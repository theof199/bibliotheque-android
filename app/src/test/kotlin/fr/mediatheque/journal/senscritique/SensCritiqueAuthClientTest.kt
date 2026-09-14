package fr.mediatheque.journal.senscritique

import io.ktor.client.HttpClient
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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `GraphQlSensCritiqueAuthClient` contre un `MockEngine` — jumeau d'`ApiClientTest`. Jamais Firebase
 * (brief du 14 septembre 2026, après un premier essai réel : `productRate` a refusé l'`idToken`
 * Firebase avec `auth/unauthenticated-user`, seul le `cookieRef` rendu par cette mutation est
 * accepté) : la connexion est une mutation GraphQL comme les autres.
 */
class SensCritiqueAuthClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    private val logger = FakeSensCritiqueLogger()

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        GraphQlSensCritiqueAuthClient(HttpClient(MockEngine(handler)) { expectSuccess = false }, logger)

    private val reponseReussie = """
        {"data":{"signInWithEmailAndPassword":{"me":{"username":"TheofB"},
         "userCookie":{"cookieRef":"cookie-1","dateExpiration":"2026-10-14T10:00:00Z"}}}}
    """.trimIndent()

    @Test
    fun `une connexion reussie rend le cookieRef, la dateExpiration et le pseudo`() = runTest {
        val api = client { respond(reponseReussie, HttpStatusCode.OK, json) }
        val outcome = api.signIn("theo@example.com", "secret") as SignInOutcome.Success
        assertEquals("cookie-1", outcome.cookieRef)
        assertEquals("2026-10-14T10:00:00Z", outcome.dateExpiration)
        assertEquals("TheofB", outcome.pseudo)
    }

    // Mutation : lire `username` a la racine de `signInWithEmailAndPassword` au lieu de sous `me`
    // fait echouer cette assertion (le pseudo attendu ne s'y trouve pas sous cette forme).
    @Test
    fun `sans username sous me, le pseudo est nul mais la connexion reussit quand meme`() = runTest {
        val api = client {
            respond(
                """{"data":{"signInWithEmailAndPassword":{"me":{"username":null},"userCookie":{"cookieRef":"c","dateExpiration":"2026-10-14T10:00:00Z"}}}}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val outcome = api.signIn("theo@example.com", "secret") as SignInOutcome.Success
        assertNull(outcome.pseudo)
        assertEquals("c", outcome.cookieRef)
    }

    // L'exemple reel (brief du 14 septembre 2026) : `productRate` a repondu avec `code` au premier
    // niveau de l'erreur, pas seulement sous `extensions.code` — la connexion, meme mutation GraphQL,
    // est lue de la meme facon. Mutation : lire uniquement `extensions.code` (jamais `code` direct)
    // fait echouer cette assertion (le code attendu resterait nul).
    @Test
    fun `une erreur GraphQL devient Refused, avec le code journalise`() = runTest {
        val api = client {
            respond("""{"errors":[{"message":"Mot de passe incorrect","code":"auth/wrong-password"}]}""", HttpStatusCode.OK, json)
        }
        assertEquals(SignInOutcome.Refused("auth/wrong-password"), api.signIn("theo@example.com", "faux"))
        assertTrue(logger.lines.any { it.contains("auth/wrong-password") })
    }

    @Test
    fun `une erreur GraphQL sans code lisible devient Refused avec un code nul`() = runTest {
        val api = client { respond("""{"errors":[{"message":"Erreur inconnue"}]}""", HttpStatusCode.OK, json) }
        assertEquals(SignInOutcome.Refused(null), api.signIn("theo@example.com", "faux"))
    }

    // Mineur, defensif : la reponse reelle a une session valide n'a jamais ete vue sans ces deux
    // champs (brief), mais si SensCritique les omettait un jour, mieux vaut « injoignable » (rejoue,
    // ne deconnecte rien) qu'un `NullPointerException` qui remonterait jusqu'a l'appelant.
    @Test
    fun `une reponse de succes sans cookieRef ou dateExpiration devient Unreachable`() = runTest {
        val api = client {
            respond("""{"data":{"signInWithEmailAndPassword":{"me":{"username":"TheofB"},"userCookie":{}}}}""", HttpStatusCode.OK, json)
        }
        assertEquals(SignInOutcome.Unreachable, api.signIn("theo@example.com", "secret"))
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

    // Mutation : intervertir `put("email", email)` et `put("password", password)` (les deux
    // arguments de meme type, String) fait echouer cette assertion — le mot de passe partirait dans
    // la variable `email`.
    @Test
    fun `envoie l email et le mot de passe dans les variables GraphQL, chacun a sa place`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond(reponseReussie, HttpStatusCode.OK, json)
        }
        api.signIn("theo@example.com", "secret-du-proprietaire")
        assertTrue(corps.contains(""""email":"theo@example.com""""))
        assertTrue(corps.contains(""""password":"secret-du-proprietaire""""))
    }

    // Le mot de passe n'est jamais journalise (brief du 14 septembre 2026) — sur aucune des
    // branches qui journalisent quelque chose. Mutation : journaliser le corps de la requete
    // envoyee (`"connexion tentee : $corps"`, par exemple) dans le bloc `try` de `signIn` fait
    // echouer cette assertion.
    //
    // Revue du 14 septembre 2026, important 4 : le dernier appel simule une erreur GraphQL sans
    // `code` (ni racine ni `extensions`) dont le `message` recopie le mot de passe — le cas ou une
    // erreur de validation du fournisseur reproduirait une variable envoyee. Mutation : revenir a
    // `logger.d("connexion refusee : ${'$'}{code ?: errors}")` (le tableau `errors` entier plutot
    // que le compte) fait echouer cette assertion, le `message` de l'erreur portant le mot de passe.
    @Test
    fun `aucune ligne journalisee ne porte le mot de passe, quelle que soit l issue`() = runTest {
        val motDePasse = "mot-de-passe-tres-secret"
        client { respond("""{"errors":[{"message":"x","code":"auth/wrong-password"}]}""", HttpStatusCode.OK, json) }
            .signIn("theo@example.com", motDePasse)
        client { respond("ceci n est pas du json", HttpStatusCode.OK, json) }
            .signIn("theo@example.com", motDePasse)
        client { respond("""{"data":{"signInWithEmailAndPassword":{"me":{},"userCookie":{}}}}""", HttpStatusCode.OK, json) }
            .signIn("theo@example.com", motDePasse)
        client { respond("""{"errors":[{"message":"Variable invalide : $motDePasse"}]}""", HttpStatusCode.OK, json) }
            .signIn("theo@example.com", motDePasse)

        assertTrue(logger.lines.isNotEmpty())
        for (ligne in logger.lines) {
            assertFalse("une ligne journalisee porte le mot de passe : $ligne", ligne.contains(motDePasse))
        }
    }
}
