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
 * `KtorSensCritiqueGraphQLClient` contre un `MockEngine`. Les trois documents (recherche, note,
 * vu, date) viennent des scripts GraphQL du site SensCritique lui-même, vérifiés lors d'un
 * troisième essai réel (revue du 14 septembre 2026) : `searchProductExplorer { items { ... } } `,
 * `productRate { id }`, `productDone { success }`, `setProductDateDone { id }`.
 */
class KtorSensCritiqueGraphQLClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private fun client(handler: suspend MockRequestHandleScope.(HttpRequestData) -> HttpResponseData) =
        KtorSensCritiqueGraphQLClient(HttpClient(MockEngine(handler)) { expectSuccess = false })

    // L'exemple réel du troisième essai (« voyage dans la lune ») : id 364792, originalTitle nul,
    // yearOfProduction 1902, un réalisateur, universe 1 (film).
    private val reponseVoyageDansLaLune = """
        {"data":{"searchProductExplorer":{"items":[
          {"id":364792,"title":"Le Voyage dans la Lune","originalTitle":null,"yearOfProduction":1902,
           "universe":1,"directors":[{"name":"Georges Méliès"}],"medias":{"picture":"https://x/aff.jpg"}}
        ]}}}
    """.trimIndent()

    @Test
    fun `search rend les candidats avec le realisateur et la jaquette`() = runTest {
        val api = client { respond(reponseVoyageDansLaLune, HttpStatusCode.OK, json) }
        val outcome = api.search("id-1", "voyage dans la lune") as ExternalSearchOutcome.Success
        assertEquals(1, outcome.candidates.size)
        val candidat = outcome.candidates[0]
        assertEquals(364792L, candidat.productId)
        assertEquals("Le Voyage dans la Lune", candidat.title)
        assertEquals(null, candidat.originalTitle)
        assertEquals(1902, candidat.year)
        assertEquals("Georges Méliès", candidat.director)
        assertEquals("https://x/aff.jpg", candidat.pictureUrl)
    }

    // Ceinture et bretelles (point 1 de la revue du 14 septembre 2026) : un homonyme livre
    // (universe 2, comme « Voyage dans la Lune / Lettres diverses ») est exclu même si le filtre
    // serveur a laissé passer autre chose que des films. Mutation : retirer `estUnFilm` (ou
    // comparer à autre chose que `1`) fait échouer cette assertion (deux candidats au lieu d'un).
    @Test
    fun `un candidat hors universe 1 (un livre par exemple) est exclu`() = runTest {
        val api = client {
            respond(
                """{"data":{"searchProductExplorer":{"items":[
                    {"id":1,"title":"Un film","universe":1},
                    {"id":2,"title":"Un livre homonyme","universe":2}
                ]}}}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val outcome = api.search("id-1", "x") as ExternalSearchOutcome.Success
        assertEquals(listOf(1L), outcome.candidates.map { it.productId })
    }

    @Test
    fun `search sans resultat rend une liste vide, pas un echec`() = runTest {
        val api = client { respond("""{"data":{"searchProductExplorer":{"items":[]}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalSearchOutcome.Success(emptyList()), api.search("id-1", "zzzz"))
    }

    // Mutation : ne pas filtrer les elements sans "id" avec `mapNotNull` ferait planter (cast
    // impossible) au lieu de silencieusement les ignorer.
    @Test
    fun `un candidat sans id est ignore, pas de plantage`() = runTest {
        val api = client {
            respond(
                """{"data":{"searchProductExplorer":{"items":[{"title":"Sans id","universe":1},{"id":2,"title":"Avec id","universe":1}]}}}""",
                HttpStatusCode.OK,
                json,
            )
        }
        val outcome = api.search("id-1", "x") as ExternalSearchOutcome.Success
        assertEquals(listOf(2L), outcome.candidates.map { it.productId })
    }

    // Point 1 de la revue du 14 septembre 2026 : le filtre univers du site peut être refusé
    // (`BAD_USER_INPUT`) — `search` rejoue alors sans filtre. Mutation : ne pas relancer la
    // recherche sur `BAD_USER_INPUT` (retourner `Failed` direct) fait échouer cette assertion (la
    // liste serait vide au lieu de porter le candidat du second essai).
    @Test
    fun `un BAD_USER_INPUT sur le filtre univers rejoue la recherche sans filtre`() = runTest {
        var tentative = 0
        val api = client { request ->
            tentative += 1
            val corps = String(request.body.toByteArray())
            if (corps.contains(""""f":[""")) {
                respond(
                    """{"errors":[{"message":"Variable filters got invalid value","extensions":{"code":"BAD_USER_INPUT"}}]}""",
                    HttpStatusCode.OK,
                    json,
                )
            } else {
                respond(reponseVoyageDansLaLune, HttpStatusCode.OK, json)
            }
        }
        val outcome = api.search("id-1", "voyage dans la lune") as ExternalSearchOutcome.Success
        assertEquals(2, tentative)
        assertEquals(listOf(364792L), outcome.candidates.map { it.productId })
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
    fun `une autre erreur GraphQL devient Failed direct, sans rejouer sans filtre`() = runTest {
        var tentatives = 0
        val api = client {
            tentatives += 1
            respond("""{"errors":[{"message":"Cannot query field x"}]}""", HttpStatusCode.OK, json)
        }
        assertEquals(ExternalSearchOutcome.Failed, api.search("id-1", "x"))
        assertEquals(1, tentatives)
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
    fun `rate reussi (id present) rend Success`() = runTest {
        val api = client { respond("""{"data":{"productRate":{"id":364792}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Success, api.rate("id-1", 42, 8))
    }

    // Mineur b de la revue precedente, toujours vrai avec le nouveau document : un champ present
    // mais nul, ou absent, ne vaut pas succes. Mutation : verifier seulement `data["productRate"]`
    // (sans descendre a `.id`) fait passer ce test au vert a tort.
    @Test
    fun `rate sans id (absent ou nul) rend Failed, pas Success`() = runTest {
        val absent = client { respond("""{"data":{"productRate":{}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, absent.rate("id-1", 42, 8))

        val nul = client { respond("""{"data":{"productRate":{"id":null}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, nul.rate("id-1", 42, 8))
    }

    @Test
    fun `markDone reussi (success vrai) rend Success`() = runTest {
        val api = client { respond("""{"data":{"productDone":{"success":true}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Success, api.markDone("id-1", 42))
    }

    // Document du site : `success` doit valoir vrai, pas juste exister. Mutation : verifier
    // seulement la presence du champ `success` (sans regarder sa valeur booleenne) fait passer ces
    // deux assertions au vert a tort.
    @Test
    fun `markDone avec success faux ou absent rend Failed`() = runTest {
        val faux = client { respond("""{"data":{"productDone":{"success":false}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, faux.markDone("id-1", 42))

        val absent = client { respond("""{"data":{"productDone":{}}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, absent.markDone("id-1", 42))
    }

    @Test
    fun `setDate reussi (id present) rend Success, avec la date au format AAAA-MM-JJ`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond("""{"data":{"setProductDateDone":{"id":364792}}}""", HttpStatusCode.OK, json)
        }
        assertEquals(ExternalPushOutcome.Success, api.setDate("id-1", 364792, "2026-09-10"))
        assertTrue(corps.contains(""""d":"2026-09-10""""))
    }

    @Test
    fun `setDate sans id rend Failed`() = runTest {
        val api = client { respond("""{"data":{"setProductDateDone":null}}""", HttpStatusCode.OK, json) }
        assertEquals(ExternalPushOutcome.Failed, api.setDate("id-1", 42, "2026-09-10"))
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
            respond("""{"data":{"searchProductExplorer":{"items":[]}}}""", HttpStatusCode.OK, json)
        }
        api.search("mon-jeton", "x")
        assertEquals("Bearer mon-jeton", entete)
    }

    @Test
    fun `envoie les mots cles dans les variables GraphQL`() = runTest {
        var corps = ""
        val api = client { request ->
            corps = String(request.body.toByteArray())
            respond("""{"data":{"searchProductExplorer":{"items":[]}}}""", HttpStatusCode.OK, json)
        }
        api.search("id-1", "le voyage de chihiro")
        assertTrue(corps.contains("le voyage de chihiro"))
    }
}
