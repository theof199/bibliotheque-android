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
 * `FirebaseSensCritiqueAuthClient` contre un `MockEngine` — jumeau d'`ApiClientTest`. Jamais de
 * vraie requête à Firebase ou SensCritique (brief : « ne sonde pas l'API sans jeton »), tout est
 * simulé.
 */
class FirebaseSensCritiqueAuthClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")
    // Frais a chaque test (JUnit4 recree l'instance de la classe par methode) : les assertions de
    // non-fuite ci-dessous lisent ses lignes sans jamais les melanger entre deux tests.
    private val logger = FakeSensCritiqueLogger()

    private fun client(graphql: SensCritiqueGraphQLClient = FakeSensCritiqueGraphQLClient(), handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        FirebaseSensCritiqueAuthClient(HttpClient(MockEngine(handler)) { expectSuccess = false }, graphql, logger)

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

    // Deuxieme essai reel (revue du 14 septembre 2026) : SignInBody.returnSecureToken avait une
    // valeur par defaut (`= true`), que kotlinx.serialization n'encode jamais quand elle egale le
    // defaut declare (`encodeDefaults` vaut faux) — le champ ne partait pas, et Firebase repondait
    // sans refreshToken ni expiresIn. Mutation : remettre `= true` sur `returnSecureToken` (et
    // laisser la construction sans l'argument) fait disparaitre `"returnSecureToken":true` du
    // corps envoye, cette assertion echoue.
    @Test
    fun `le corps envoye a signInWithPassword porte returnSecureToken vrai`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond("""{"idToken":"id-1","refreshToken":"refresh-1","expiresIn":"3600"}""", HttpStatusCode.OK, json)
        }
        api.signIn("theo@example.com", "secret")
        assertTrue("le corps envoye doit porter returnSecureToken:true, etait : $corps", corps.contains(""""returnSecureToken":true"""))
    }

    // La reponse reelle constatee (deuxieme essai) : 200, displayName present, mais sans
    // refreshToken ni expiresIn — consequence directe du bug ci-dessus. Le journal doit nommer la
    // cause plutot que dire juste « illisible », pour que la prochaine anomalie de ce genre se lise
    // sans deviner. Mutation : revenir a un seul message ("illisible", clefsSeulement) pour ce cas
    // fait echouer cette assertion.
    @Test
    fun `une reponse sans refreshToken journalise la cause precise, pas illisible`() = runTest {
        val api = client {
            respond(
                """{"kind":"x","localId":"1","email":"theo@example.com","displayName":"TheofB","idToken":"id-1","registered":true}""",
                HttpStatusCode.OK,
                json,
            )
        }
        api.signIn("theo@example.com", "secret")
        assertTrue(
            "attendu une ligne 'sans refreshToken', lignes : ${logger.lines}",
            logger.lines.any { it.contains("reponse de connexion sans refreshToken") },
        )
        assertTrue(logger.lines.none { it.contains("reponse de connexion illisible") })
    }

    // Revue du 14 septembre 2026, point 2 : tout 4xx Firebase devient un `Refused` portant son
    // code (jamais un `Unreachable`, quel que soit le code) — c'est `messageForRefus`, cote
    // `SensCritiqueViewModel`, qui choisit le message. Mutation : comparer `code` a une chaine fixe
    // (par exemple "INVALID_LOGIN_CREDENTIALS") au lieu de le transmettre tel quel ferait echouer
    // ce test avec un autre code Firebase que celui-la, EMAIL_NOT_FOUND par exemple.
    @Test
    fun `un 4xx Firebase devient Refused avec son code, quel qu il soit`() = runTest {
        val api = client {
            respond("""{"error":{"code":400,"message":"EMAIL_NOT_FOUND"}}""", HttpStatusCode.BadRequest, json)
        }
        assertEquals(SignInOutcome.Refused("EMAIL_NOT_FOUND"), api.signIn("theo@example.com", "faux"))
    }

    @Test
    fun `un 4xx sans code Firebase lisible devient Refused avec un code nul`() = runTest {
        val api = client { respond("ceci n est pas du json Firebase", HttpStatusCode.BadRequest, json) }
        assertEquals(SignInOutcome.Refused(null), api.signIn("theo@example.com", "x"))
    }

    // Mutation : elargir `REFUS_STATUS_RANGE` au-dela de 400..499 (par exemple 400..599) ferait
    // echouer cette assertion — un 5xx deviendrait `Refused` au lieu d'`Unreachable`.
    @Test
    fun `un 5xx devient injoignable, jamais un refus`() = runTest {
        val api = client {
            respond("""{"error":{"code":503,"message":"SERVICE_UNAVAILABLE"}}""", HttpStatusCode.ServiceUnavailable, json)
        }
        assertEquals(SignInOutcome.Unreachable, api.signIn("theo@example.com", "x"))
    }

    // Point 1 de la revue du 14 septembre 2026 : le code Firebase n'est pas un secret, il se
    // journalise en clair (a la difference du corps d'une reponse reussie). Mutation : revenir a
    // `clefsSeulement(body)` dans ce message ferait echouer cette assertion (la ligne porterait
    // "message" au lieu de "EMAIL_NOT_FOUND").
    @Test
    fun `le journal de connexion refusee porte le code Firebase, pas des noms de clefs`() = runTest {
        val api = client { respond("""{"error":{"code":400,"message":"EMAIL_NOT_FOUND"}}""", HttpStatusCode.BadRequest, json) }
        api.signIn("theo@example.com", "x")
        assertTrue(logger.lines.any { it.contains("EMAIL_NOT_FOUND") })
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

    // Important 3 de la revue du 14 septembre 2026 : seuls 400/401/403 avec un corps d'erreur
    // Firebase valent un vrai refus (le refreshToken n'est plus bon). Mutation : elargir
    // `REFUS_STATUS_CODES` a tous les statuts d'erreur ferait echouer les trois tests suivants
    // (tout deviendrait `Refused`) ; le retrecir a un ensemble vide ferait echouer celui-ci
    // (401 deviendrait `Unreachable`).
    @Test
    fun `un renouvellement refuse par le serveur devient Refused (400 et 401)`() = runTest {
        val api400 = client { respond("""{"error":{"message":"INVALID_REFRESH_TOKEN"}}""", HttpStatusCode.BadRequest, json) }
        assertEquals(RefreshOutcome.Refused, api400.refresh("refresh-1"))

        val api401 = client { respond("""{"error":{"message":"INVALID_REFRESH_TOKEN"}}""", HttpStatusCode.Unauthorized, json) }
        assertEquals(RefreshOutcome.Refused, api401.refresh("refresh-1"))
    }

    // Le coeur de l'important 3 : une panne serveur transitoire ne doit jamais se lire comme un
    // refus (qui deconnecterait le compte cote `SensCritiqueAuthProvider`).
    @Test
    fun `une panne serveur (5xx) du renouvellement devient Unreachable, pas Refused`() = runTest {
        val api = client { respond("""{"error":"panne"}""", HttpStatusCode.ServiceUnavailable, json) }
        assertEquals(RefreshOutcome.Unreachable, api.refresh("refresh-1"))
    }

    @Test
    fun `une panne reseau du renouvellement devient Unreachable, pas Refused`() = runTest {
        val api = client { throw java.io.IOException("hors ligne") }
        assertEquals(RefreshOutcome.Unreachable, api.refresh("refresh-1"))
    }

    @Test
    fun `une reponse de renouvellement illisible devient Unreachable, pas Refused`() = runTest {
        val api = client { respond("ceci n est pas du json", HttpStatusCode.OK, json) }
        assertEquals(RefreshOutcome.Unreachable, api.refresh("refresh-1"))
    }

    // Point 1 de la revue du 14 septembre 2026, meme preuve que pour la connexion.
    @Test
    fun `le journal de renouvellement refuse porte le code Firebase, pas des noms de clefs`() = runTest {
        val api = client { respond("""{"error":{"message":"TOKEN_EXPIRED"}}""", HttpStatusCode.BadRequest, json) }
        api.refresh("refresh-1")
        assertTrue(logger.lines.any { it.contains("TOKEN_EXPIRED") })
    }

    // Critique 1 de la revue du 14 septembre 2026 : la branche attendue (`displayName` absent, la
    // vraie connexion SensCritique) journalisait le corps entier de la reponse Firebase, jeton
    // compris — sur un `release` non minifie, lisible par `bin/logs`. Mutation : remettre
    // `"... : $text"` (le corps brut) a la place de `clefsSeulement(text)` dans le message de cette
    // branche fait echouer cette assertion (la ligne contiendrait "id-secret-1").
    @Test
    fun `aucune ligne journalisee ne porte l idToken ou le refreshToken (displayName absent)`() = runTest {
        val api = client {
            respond("""{"idToken":"id-secret-1","refreshToken":"refresh-secret-1","expiresIn":"3600"}""", HttpStatusCode.OK, json)
        }
        api.signIn("theo@example.com", "secret")

        assertTrue("au moins une ligne journalisee sur cette branche", logger.lines.isNotEmpty())
        for (ligne in logger.lines) {
            assertFalse("une ligne journalisee porte l'idToken : $ligne", ligne.contains("id-secret-1"))
            assertFalse("une ligne journalisee porte le refreshToken : $ligne", ligne.contains("refresh-secret-1"))
        }
    }

    // Même preuve sur les autres branches qui lisent un corps de réponse Firebase : refus de
    // connexion, réponse de connexion illisible, refus de renouvellement — aucune ne doit jamais
    // réciter le corps *en dehors* du code lui-même. `secret-marker` figure ici dans un champ que
    // le code ne lit jamais (`error.message`, lui, est *censé* sortir depuis le point 1 — les
    // tests dédiés du code plus haut le prouvent) : il resterait invisible si l'une de ces branches
    // revenait à `$body`/`$text` brut.
    @Test
    fun `aucune ligne journalisee ne porte le corps de la reponse en dehors du code`() = runTest {
        client { respond("""{"error":{"code":400,"message":"INVALID_LOGIN_CREDENTIALS","details":"secret-marker"}}""", HttpStatusCode.BadRequest, json) }
            .signIn("theo@example.com", "faux")
        client { respond("""{"idToken":"secret-marker"}""", HttpStatusCode.OK, json) }
            .signIn("theo@example.com", "secret")
        client { respond("""{"error":{"code":400,"message":"INVALID_REFRESH_TOKEN","details":"secret-marker"}}""", HttpStatusCode.BadRequest, json) }
            .refresh("refresh-1")

        assertTrue(logger.lines.isNotEmpty())
        for (ligne in logger.lines) {
            assertFalse("une ligne journalisee porte le corps brut : $ligne", ligne.contains("secret-marker"))
        }
    }
}
