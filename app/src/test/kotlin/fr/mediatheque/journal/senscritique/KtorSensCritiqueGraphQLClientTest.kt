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
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `KtorSensCritiqueGraphQLClient` contre un `MockEngine`. Les champs exacts de `searchResult` et de
 * `DoneResult` ne sont pas connus (brief) : ces tests vérifient ce qui est écrit dans le code —
 * une liste directe, `medias` liste ou objet — et surtout que rien ne plante sur une forme
 * différente ou une erreur.
 */
class KtorSensCritiqueGraphQLClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorSensCritiqueGraphQLClient(HttpClient(MockEngine(handler)) { expectSuccess = false })

    @Test
    fun `search rend les candidats avec medias en liste`() = runTest {
        val api = client {
            respond(
                """{"data":{"searchResult":[{"id":42,"title":"Le Voyage de Chihiro","originalTitle":"Sen to Chihiro","yearOfProduction":2001,"medias":[{"picture":"https://x/aff.jpg"}]}]}}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val outcome = api.search("id-1", "chihiro") as ExternalSearchOutcome.Success
        assertEquals(1, outcome.candidates.size)
        val candidat = outcome.candidates[0]
        assertEquals(42L, candidat.productId)
        assertEquals("Le Voyage de Chihiro", candidat.title)
        assertEquals("Sen to Chihiro", candidat.originalTitle)
        assertEquals(2001, candidat.year)
        assertEquals("https://x/aff.jpg", candidat.pictureUrl)
    }

    @Test
    fun `search extrait la jaquette quand medias est un objet unique, pas une liste`() = runTest {
        val api = client {
            respond("""{"data":{"searchResult":[{"id":1,"title":"X","medias":{"picture":"https://x/y.jpg"}}]}}""", HttpStatusCode.OK, json)
        }
        val outcome = api.search("id-1", "x") as ExternalSearchOutcome.Success
        assertEquals("https://x/y.jpg", outcome.candidates[0].pictureUrl)
    }

    @Test
    fun `search sans resultat rend une liste vide, pas un echec`() = runTest {
        val api = client { respond("""{"data":{"searchResult":[]}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalSearchOutcome.Success(emptyList()), api.search("id-1", "zzzz"))
    }

    // Mutation : ne pas filtrer les elements sans "id" avec `mapNotNull` ferait planter (cast
    // impossible) au lieu de silencieusement les ignorer.
    @Test
    fun `un candidat sans id est ignore, pas de plantage`() = runTest {
        val api = client {
            respond("""{"data":{"searchResult":[{"title":"Sans id"},{"id":2,"title":"Avec id"}]}}""", HttpStatusCode.OK, json)
        }
        val outcome = api.search("id-1", "x") as ExternalSearchOutcome.Success
        assertEquals(listOf(2L), outcome.candidates.map { it.productId })
    }

    @Test
    fun `un 401 HTTP devient Unauthenticated`() = runTest {
        val api = client { respond("""{"errors":[{"message":"nope"}]}""", HttpStatusCode.Unauthorized, json) }
        assertEquals(ExternalSearchOutcome.Unauthenticated, api.search("id-1", "x"))
    }

    // Mutation : chercher "unauthenticated" seul (sans le variant "auth/") laisserait passer ce
    // message precis sous `Failed` au lieu d'`Unauthenticated`.
    @Test
    fun `une erreur GraphQL auth slash unauthenticated-user devient Unauthenticated, meme en 200`() = runTest {
        val api = client { respond("""{"errors":[{"message":"auth/unauthenticated-user"}]}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalSearchOutcome.Unauthenticated, api.search("id-1", "x"))
    }

    @Test
    fun `une autre erreur GraphQL devient Failed, pas Unauthenticated`() = runTest {
        val api = client { respond("""{"errors":[{"message":"Cannot query field x"}]}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalSearchOutcome.Failed, api.search("id-1", "x"))
    }

    @Test
    fun `une panne reseau devient Failed, jamais une exception`() = runTest {
        val api = client { throw java.io.IOException("hors ligne") }
        assertEquals(ExternalSearchOutcome.Failed, api.search("id-1", "x"))
    }

    @Test
    fun `une reponse illisible devient Failed, jamais une exception`() = runTest {
        val api = client { respond("pas du json", HttpStatusCode.OK, json) }
        assertEquals(ExternalSearchOutcome.Failed, api.search("id-1", "x"))
    }

    @Test
    fun `rate et markDone reussis (champ attendu present) rendent Success`() = runTest {
        val api = client { respond("""{"data":{"productRate":{"__typename":"Rating"},"productDone":{"__typename":"DoneResult"}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Success, api.rate("id-1", 42, 8))
        assertEquals(ExternalPushOutcome.Success, api.markDone("id-1", 42))
    }

    // Mineur b de la revue du 14 septembre 2026 : un `200` avec `data` mais sans le champ attendu
    // (ou nul) valait `Success` — un serveur qui refuse la mutation sans lever d'`errors[]` faisait
    // croire a une poussee reussie. Mutation : retirer la verification de `champAttendu` dans
    // `toPushOutcome` (revenir a `is RawOutcome.Ok -> ExternalPushOutcome.Success`) fait echouer
    // les deux assertions ci-dessous.
    @Test
    fun `rate et markDone sans le champ attendu rendent Failed, pas Success`() = runTest {
        val sansChamp = client { respond("""{"data":{}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, sansChamp.rate("id-1", 42, 8))

        val champNul = client { respond("""{"data":{"productDone":null}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, champNul.markDone("id-1", 42))
    }

    @Test
    fun `whoAmI reussi rend vrai, une erreur GraphQL rend faux`() = runTest {
        val ok = client { respond("""{"data":{"user":{}}}""", HttpStatusCode.OK, json) }
        assertTrue(ok.whoAmI("id-1", "TheofB"))

        val ko = client { respond("""{"errors":[{"message":"nope"}]}""", HttpStatusCode.OK, json) }
        assertFalse(ko.whoAmI("id-1", "TheofB"))
    }

    // Mutation : oublier l'en-tete `Authorization` (ou l'ecrire sans "Bearer ") romprait
    // l'authentification aupres de SensCritique sans qu'aucune reponse ne le signale explicitement.
    @Test
    fun `pose le jeton en en-tete Authorization Bearer`() = runTest {
        var entete: String? = null
        val api = client { request ->
            entete = request.headers[HttpHeaders.Authorization]
            respond("""{"data":{"searchResult":[]}}""", HttpStatusCode.OK, json)
        }
        api.search("mon-jeton", "x")
        assertEquals("Bearer mon-jeton", entete)
    }

    @Test
    fun `envoie les mots cles dans les variables GraphQL`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond("""{"data":{"searchResult":[]}}""", HttpStatusCode.OK, json)
        }
        api.search("id-1", "le voyage de chihiro")
        assertTrue(corps.contains("le voyage de chihiro"))
    }
}
