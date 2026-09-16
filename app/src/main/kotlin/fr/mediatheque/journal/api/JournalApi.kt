package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.CollectionResult
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.PersonneResult
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.Saga
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.api.dto.ChroniqueAnneeResponse
import fr.mediatheque.journal.api.dto.CartonFilmResponse
import fr.mediatheque.journal.api.dto.DemanderVoyageResponse
import fr.mediatheque.journal.api.dto.VoyageResponse
import kotlinx.serialization.json.JsonObject

/**
 * La seule porte des écrans vers le réseau. Vingt-sept opérations, celles que
 * l'application consomme ; les chemins n'existent que dans `Endpoints`, et ne
 * s'emploient que depuis `ApiClient`. Toute fonction peut lever `ApiError`.
 *
 * `patchViewing` reçoit un `JsonObject` déjà construit par l'appelant (la
 * tâche 6) plutôt qu'un DTO à champs fixes : un `PATCH` ne porte que ce qui a
 * changé, `JsonNull` compris pour effacer un commentaire — un DTO à valeurs
 * par défaut ne saurait pas distinguer « inchangé » de « remis à zéro ».
 *
 * `seances` et `sorties` sont à « Au ciné » (brief du 14 septembre 2026) ce
 * que `journal` et `searchMovies` sont ailleurs : une méthode dédiée plutôt
 * qu'un paramètre optionnel sur `journal`, pour ne rien changer aux appels
 * existants de `FilmsViewModel`.
 */
interface JournalApi {
    suspend fun login(pseudo: String, password: String): User
    suspend fun me(): User
    suspend fun logout()
    suspend fun searchMovies(query: String): List<SearchResult>
    suspend fun addMedia(result: SearchResult): AddMediaResponse
    suspend fun journal(cursor: String?): JournalResponse
    suspend fun addViewing(body: JournalCreateBody): JournalItem
    suspend fun patchViewing(id: String, body: JsonObject): JournalItem
    suspend fun deleteViewing(id: String)

    /**
     * `POST /me/journal/import/letterboxd` (brief du 16 septembre 2026) : le contenu de
     * `diary.csv`, tel quel. La réponse n'arrive qu'une fois le fichier entièrement traité —
     * jusqu'à quelques minutes pour un gros fichier, `ApiClient` lui donne un délai propre.
     */
    suspend fun importLetterboxd(csv: String): ImportLetterboxdResponse
    suspend fun stats(): StatsResponse
    /** `GET /me/journal?reaction=en_salle` : mes séances, du plus récent au plus ancien. */
    suspend fun seances(cursor: String?): JournalResponse
    /** `GET /reference/sorties` : sorties en salle, semaine en cours et semaine prochaine. */
    suspend fun sorties(): SortiesResponse
    /** `GET /reference/plex` : le Plex du propriétaire, demandé sur Seerr — la Frise et « Ensuite ». */
    suspend fun plex(): PlexResponse

    /** `GET /reference/personnes?q=` : dix réalisateurs au plus, pour le « + » de l'écran Réalisateurs. */
    suspend fun chercherPersonnes(query: String): List<PersonneResult>

    /** `GET /me/realisateurs` : ceux que je suis, du plus récemment ajouté au plus ancien. Sans pagination. */
    suspend fun realisateurs(): List<Realisateur>

    /** `POST /me/realisateurs` : idempotent côté back (200 au lieu de 201 si déjà suivi). */
    suspend fun suivreRealisateur(tmdbId: Int): Realisateur

    /** `DELETE /me/realisateurs/{tmdbId}` : `404` si je ne le suis pas. */
    suspend fun retirerRealisateur(tmdbId: Int)

    /** `GET /me/realisateurs/{tmdbId}/films` : sa filmographie, de la plus ancienne sortie à la plus récente. */
    suspend fun filmographie(tmdbId: Int): List<FilmSuivi>

    /** `PUT /me/introuvables/{tmdbId}` : marque un film (son propre `tmdb_id`) introuvable. Idempotent, toujours `204`. */
    suspend fun marquerIntrouvable(tmdbId: Int)

    /** `DELETE /me/introuvables/{tmdbId}` : retire la marque. Toujours `204`, même si rien n'était marqué. */
    suspend fun retirerIntrouvable(tmdbId: Int)

    // --- Les sagas (brief du 15 septembre 2026), jumelles des cinq ci-dessus. ---

    /** `GET /reference/sagas?q=` : dix sagas au plus, pour le « + » de l'écran Suivis, segment Sagas. */
    suspend fun chercherSagas(query: String): List<CollectionResult>

    /** `GET /me/sagas` : celles que je suis, de la plus récemment ajoutée à la plus ancienne. Sans pagination. */
    suspend fun sagas(): List<Saga>

    /** `POST /me/sagas` : idempotent côté back (200 au lieu de 201 si déjà suivie). */
    suspend fun suivreSaga(tmdbId: Int): Saga

    /** `DELETE /me/sagas/{tmdbId}` : `404` si je ne la suis pas. */
    suspend fun retirerSaga(tmdbId: Int)

    /** `GET /me/sagas/{tmdbId}/films` : ses films, de la plus ancienne sortie à la plus récente. */
    suspend fun filmsDeSaga(tmdbId: Int): List<FilmSuivi>

    /**
     * `PUT /me/sagas/{tmdbId}/films/{filmId}` : ajoute un film absent de la
     * collection (brief « les films de saga ajoutés à la main », 15 septembre
     * 2026). Idempotent, toujours `204`. `404` si la saga n'est pas suivie, ou
     * si TMDB ne connaît pas ce `filmId`.
     */
    suspend fun ajouterFilmSaga(tmdbId: Int, filmId: Int)

    /** `DELETE /me/sagas/{tmdbId}/films/{filmId}` : le retire. Toujours `204`, même si le film n'avait pas été ajouté. */
    suspend fun retirerFilmSaga(tmdbId: Int, filmId: Int)

    // --- Le Voyage (brief du 16 septembre 2026), phase 1 « le moteur ». ---

    /** `GET /me/voyage` : ma progression, année par année, depuis 1895. Mise en cache 60 s côté back. */
    suspend fun voyage(): VoyageResponse

    /** `GET /reference/chroniques/annees/{annee}` : le récit et les essentiels d'une année, écrits par Claude. */
    suspend fun chroniqueAnnee(annee: Int): ChroniqueAnneeResponse

    /** `GET /reference/chroniques/films/{tmdbId}` : le carton « Et pendant ce temps… » d'un film. */
    suspend fun cartonFilm(tmdbId: Int): CartonFilmResponse

    /** `POST /me/voyage/demander/{tmdbId}` : demande l'essentiel sur Seerr. `201` à la création, `200` s'il l'était déjà. */
    suspend fun demanderVoyage(tmdbId: Int): DemanderVoyageResponse
}

/**
 * Toutes les pages de `GET /me/journal`, chargées à la suite. La Frise veut le journal entier
 * pour le ranger par année ; l'écran Réalisateurs le veut pour retrouver une entrée par son
 * `entry_id` — le contrat n'expose aucun `GET /me/journal/{id}`, seulement `PATCH` et `DELETE`.
 * Une seule implémentation, pour que les deux s'arrêtent sur la même condition (`next_cursor`
 * nul) plutôt que chacune sur la sienne.
 */
suspend fun JournalApi.journalComplet(): List<JournalItem> {
    val items = mutableListOf<JournalItem>()
    var cursor: String? = null
    do {
        val page = journal(cursor)
        items += page.items
        cursor = page.next_cursor
    } while (cursor != null)
    return items
}
