package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.FilmographieResponse
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.PersonnesResponse
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.SearchResponse
import fr.mediatheque.journal.api.dto.SessionResponse
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.movies
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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
    // « Au ciné » (brief du 14 puis 15 septembre 2026) : à l'affiche aujourd'hui dans mes
    // cinémas (Allociné), et la semaine prochaine (TMDB). Le contrat n'a pas d'exemple à part
    // pour `GET /me/journal?reaction=` : c'est la même opération, déjà couverte ci-dessus.
    @Test fun `GET reference sorties`() {
        val sorties = lit("/reference/sorties", "get", "200", SortiesResponse.serializer())
        assertEquals("2026-09-15", sorties.en_cours.du)
        assertEquals("2026-09-15", sorties.en_cours.au)
        assertEquals(true, sorties.en_cours.cinemas_configures)
        assertNotNull(sorties.en_cours.calcule_le)
        val film = sorties.en_cours.films.first()
        assertEquals(912649, film.tmdb_id)
        assertEquals(306319, film.allocine_id)
        assertEquals(listOf("UGC Ciné Cité Les Halles", "mk2 Bastille (Beaumarchais)"), film.cinemas)
        assertEquals(listOf("Alix Delaporte"), film.directors)
    }
    // La Frise et « Ensuite » (brief du 15 septembre 2026) : le Plex du propriétaire, demandé
    // sur Seerr. Authentifiée côté back, mais l'exemple du contrat ne dépend pas d'une session.
    @Test fun `GET reference plex`() {
        val plex = lit("/reference/plex", "get", "200", PlexResponse.serializer())
        assertEquals(true, plex.configure)
        assertNotNull(plex.calcule_le)
        val film = plex.films.first()
        assertEquals(27205, film.tmdb_id)
        assertEquals("Inception", film.title)
        assertEquals(2010, film.year)
    }
    @Test fun `POST me journal`() { lit("/me/journal", "post", "201", JournalItem.serializer()) }
    @Test fun `PATCH me journal id`() { lit("/me/journal/{id}", "patch", "200", JournalItem.serializer()) }
    @Test fun `GET stats`() {
        val stats = lit("/stats", "get", "200", StatsResponse.serializer())
        assertEquals(17, stats.dashboard.periods.all.counts.movies)
    }

    // Les réalisateurs (brief du 15 septembre 2026). `DELETE /me/realisateurs/{tmdbId}` rend
    // `204` : aucun corps, donc aucun exemple à lire ici.
    @Test fun `GET reference personnes`() {
        val page = lit("/reference/personnes", "get", "200", PersonnesResponse.serializer())
        val personne = page.results.first()
        assertEquals(525, personne.tmdb_id)
        assertEquals("Christopher Nolan", personne.name)
        assertEquals("https://image.tmdb.org/t/p/w185/xuAIuYSmsUzKlUMBFGVZaWsY3DZ.jpg", personne.profile_url)
    }

    @Test fun `GET me realisateurs`() {
        val suivis = lit("/me/realisateurs", "get", "200", ListSerializer(Realisateur.serializer()))
        assertEquals(525, suivis.first().tmdb_id)
        assertEquals("Christopher Nolan", suivis.first().name)
        assertEquals("2026-09-15T18:22:41.000Z", suivis.first().ajoute_le)
    }

    @Test fun `POST me realisateurs`() { lit("/me/realisateurs", "post", "201", Realisateur.serializer()) }

    // `vu` est la seule forme qui change d'un film à l'autre : nul quand je ne l'ai jamais
    // journalisé, l'entrée la plus récente sinon. C'est `entry_id` qui ouvre la correction
    // depuis la fiche — un renommage côté back casserait ici, avant le téléphone.
    @Test fun `GET me realisateurs films`() {
        val filmo = lit("/me/realisateurs/{tmdbId}/films", "get", "200", FilmographieResponse.serializer())
        val inception = filmo.films.first()
        assertEquals(27205, inception.tmdb_id)
        assertEquals(2010, inception.year)
        assertEquals("2010-07-15", inception.release_date)
        assertEquals("e0000000-0000-4000-8000-000000000002", inception.vu?.entry_id)
        assertEquals(9, inception.vu?.rating)
        assertEquals("2026-07-12", inception.vu?.finished_at)
        assertNull("un film jamais journalisé n'a pas de `vu`", filmo.films[1].vu)
    }
}
