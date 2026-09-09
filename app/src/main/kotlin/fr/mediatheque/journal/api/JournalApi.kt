package fr.mediatheque.journal.api

import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import kotlinx.serialization.json.JsonObject

/**
 * La seule porte des écrans vers le réseau. Dix opérations, celles que
 * l'application consomme ; les chemins n'existent que dans `Endpoints`, et ne
 * s'emploient que depuis `ApiClient`. Toute fonction peut lever `ApiError`.
 *
 * `patchViewing` reçoit un `JsonObject` déjà construit par l'appelant (la
 * tâche 6) plutôt qu'un DTO à champs fixes : un `PATCH` ne porte que ce qui a
 * changé, `JsonNull` compris pour effacer un commentaire — un DTO à valeurs
 * par défaut ne saurait pas distinguer « inchangé » de « remis à zéro ».
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
}
