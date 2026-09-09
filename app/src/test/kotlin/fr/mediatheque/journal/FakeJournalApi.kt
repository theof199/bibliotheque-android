package fr.mediatheque.journal

import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.AddMediaResponse
import fr.mediatheque.journal.api.dto.AddedMedia
import fr.mediatheque.journal.api.dto.Carnet
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalMedia
import fr.mediatheque.journal.api.dto.JournalResponse
import fr.mediatheque.journal.api.dto.LogEntry
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.StatsResponse
import fr.mediatheque.journal.api.dto.User
import kotlinx.serialization.json.JsonObject

class FakeJournalApi : JournalApi {
    val calls = mutableListOf<String>()

    var onLogin: suspend (String, String) -> User = { _, _ -> ALICE }
    var onMe: suspend () -> User = { ALICE }
    var onLogout: suspend () -> Unit = {}
    var onSearch: suspend (String) -> List<SearchResult> = { emptyList() }
    var onAddMedia: suspend (SearchResult) -> AddMediaResponse = { r -> AddMediaResponse(true, AddedMedia("m-${r.external_id}", r.title)) }
    var onJournal: suspend (String?) -> JournalResponse = { JournalResponse(emptyList(), null) }
    var onAddViewing: suspend (JournalCreateBody) -> JournalItem = { b -> item(b.media_id, b.finished_at, b.rating, b.reactions, b.comment) }
    // `patchViewing` reçoit un `JsonObject` (tâche 3, décision 1) : le corps ne porte que ce qui a
    // changé, la valeur par défaut ne le lit donc pas.
    var onPatchViewing: suspend (String, JsonObject) -> JournalItem = { id, _ -> item("m", "2026-01-01T00:00:00.000Z", null, emptyList(), null, id) }
    var onDeleteViewing: suspend (String) -> Unit = {}
    var onStats: suspend () -> StatsResponse = { error("onStats non configuré") }

    override suspend fun login(pseudo: String, password: String) = track("login $pseudo") { onLogin(pseudo, password) }
    override suspend fun me() = track("me") { onMe() }
    override suspend fun logout() = track("logout") { onLogout() }
    override suspend fun searchMovies(query: String) = track("search $query") { onSearch(query) }
    override suspend fun addMedia(result: SearchResult) = track("addMedia ${result.external_id}") { onAddMedia(result) }
    override suspend fun journal(cursor: String?) = track("journal $cursor") { onJournal(cursor) }
    override suspend fun addViewing(body: JournalCreateBody) = track("addViewing ${body.media_id}") { onAddViewing(body) }
    override suspend fun patchViewing(id: String, body: JsonObject) = track("patchViewing $id") { onPatchViewing(id, body) }
    override suspend fun deleteViewing(id: String) = track("deleteViewing $id") { onDeleteViewing(id) }
    override suspend fun stats() = track("stats") { onStats() }

    private suspend fun <T> track(name: String, block: suspend () -> T): T {
        calls += name
        return block()
    }

    companion object {
        val ALICE = User("u-alice", "alice", "#E4572E")

        fun item(mediaId: String, finishedAt: String, rating: Int?, reactions: List<String>, comment: String?, id: String = "e-$finishedAt") =
            JournalItem(LogEntry(id, mediaId, finishedAt, rating), JournalMedia(mediaId, "Un film"), Carnet(reactions, comment))

        fun unauthorized() = ApiError("UNAUTHENTICATED", "Connecte-toi d’abord.", retryable = false, status = 401)
        fun rateLimited(seconds: Int) = ApiError("RATE_LIMITED", "Trop de tentatives.", retryable = true, status = 429, retryAfterSeconds = seconds)
        fun network() = ApiError.network(java.io.IOException("hors ligne"))
        fun refused() = ApiError("UNAUTHENTICATED", "Pseudo ou mot de passe incorrect.", retryable = false, status = 401)
    }
}
