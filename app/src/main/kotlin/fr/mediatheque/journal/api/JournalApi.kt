package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortiesResponse
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import kotlinx.serialization.json.JsonObject

/**
 * La seule porte des écrans vers le réseau. Douze opérations, celles que
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
    suspend fun stats(): StatsResponse
    /** `GET /me/journal?reaction=en_salle` : mes séances, du plus récent au plus ancien. */
    suspend fun seances(cursor: String?): JournalResponse
    /** `GET /reference/sorties` : sorties en salle, semaine en cours et semaine prochaine. */
    suspend fun sorties(): SortiesResponse
}
