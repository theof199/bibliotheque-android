package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.SearchResponse
import fr.mediatheque.journal.api.dto.SessionResponse
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.movies
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/**
 * `contract/openapi.json` est la copie du contrat du back. Chaque opération
 * consommée y porte un exemple de réponse réel ; ce test le désérialise dans
 * notre DTO. Un champ que le back retire ou renomme casse ici, avant le
 * téléphone. Un champ qu'il ajoute ne casse rien : c'est `ignoreUnknownKeys`.
 *
 * `/auth/logout` et `DELETE /me/journal/{id}` rendent `204` : aucun exemple de
 * corps, ce test n'en attend donc aucun pour ces deux opérations.
 */
class ContractTest {
    private val contrat: JsonElement by lazy {
        Json.parseToJsonElement(File("../contract/openapi.json").readText())
    }

    private fun exemple(path: String, method: String, code: String): JsonElement {
        val response = contrat.jsonObject["paths"]!!.jsonObject[path]!!.jsonObject[method]!!
            .jsonObject["responses"]!!.jsonObject[code]
        assertNotNull("pas de réponse $code pour $method $path dans le contrat", response)
        val value = response!!.jsonObject["content"]!!.jsonObject["application/json"]!!
            .jsonObject["examples"]!!.jsonObject["Réponse type"]!!.jsonObject["value"]
        assertNotNull("pas d'exemple pour $method $path $code", value)
        return value!!
    }

    private fun <T> lit(path: String, method: String, code: String, serializer: KSerializer<T>): T =
        ApiClient.ApiJson.decodeFromJsonElement(serializer, exemple(path, method, code))

    @Test fun `POST auth login`() { lit("/auth/login", "post", "200", SessionResponse.serializer()) }
    @Test fun `GET auth me`() { lit("/auth/me", "get", "200", SessionResponse.serializer()) }
    @Test fun `GET search`() {
        val page = lit("/search", "get", "200", SearchResponse.serializer())
        assertEquals("tmdb", page.items.first().source)
    }
    @Test fun `POST media`() { lit("/media", "post", "201", AddMediaResponse.serializer()) }
    // Cinq valeurs de l'exemple, pas seulement sa forme : les champs que le back pourrait renommer
    // sans que ce test bouge (revue de la vague finale, mineur 7).
    @Test fun `GET me journal`() {
        val page = lit("/me/journal", "get", "200", JournalResponse.serializer())
        val item = page.items.first()
        assertEquals(9, item.entry.rating)
        assertEquals("https://image.tmdb.org/t/p/w500/9gk7adZmeSSuQfZBtWWLIcVcSY.jpg", item.media.cover_url)
        // Le `tmdb_id` (brief du 14 septembre 2026, « Au ciné ») : sans lui dans le contrat,
        // le rapprochement par `tmdb_id` des sorties en salle casserait en silence.
        assertEquals("27205", item.media.external_id)
        assertEquals(listOf("adore", "touche"), item.carnet.reactions)
        assertEquals("Revu avec Léa, toujours aussi fort.", item.carnet.comment)
        assertEquals(null, page.next_cursor)
    }
    // « Au ciné » (brief du 14 septembre 2026) : sorties en salle, semaine en cours et semaine
    // prochaine. Le contrat n'a pas d'exemple à part pour `GET /me/journal?reaction=` : c'est la
    // même opération, déjà couverte ci-dessus.
    @Test fun `GET reference sorties`() {
        val sorties = lit("/reference/sorties", "get", "200", SortiesResponse.serializer())
        assertEquals("2026-09-14", sorties.en_cours.du)
        assertEquals("2026-09-20", sorties.en_cours.au)
        val film = sorties.en_cours.films.first()
        assertEquals(912649, film.tmdb_id)
    }
    @Test fun `POST me journal`() { lit("/me/journal", "post", "201", JournalItem.serializer()) }
    @Test fun `PATCH me journal id`() { lit("/me/journal/{id}", "patch", "200", JournalItem.serializer()) }
    @Test fun `GET stats`() {
        val stats = lit("/stats", "get", "200", StatsResponse.serializer())
        assertEquals(17, stats.dashboard.periods.all.counts.movies)
    }
}
