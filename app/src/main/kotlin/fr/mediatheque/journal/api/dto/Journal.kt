package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * `rating` et `comment` ont une valeur par défaut nulle : le `Json` qui encode
 * les requêtes (`ApiClient.ApiJson`, `explicitNulls = false`) omet alors la
 * clé plutôt que d'envoyer `null`. C'est nécessaire pour `rating` — le back ne
 * remonte la note au suivi que si le corps porte `rating`
 * (`if (body.rating !== undefined)`) — et sans effet pour `comment`, que le
 * back traite de la même façon absent ou nul. `reactions` a aussi une valeur
 * par défaut, mais `encodeDefaults = true` la fait tout de même sortir en
 * `[]`, jamais en clé absente.
 */
@Serializable
data class JournalCreateBody(
    val media_id: String,
    val finished_at: String,
    val rating: Int? = null,
    val reactions: List<String> = emptyList(),
    val comment: String? = null,
)

@Serializable
data class LogEntry(val id: String, val media_id: String, val finished_at: String, val rating: Int? = null)

@Serializable
data class JournalMedia(
    val id: String,
    val title: String,
    val cover_url: String? = null,
    val year: Int? = null,
    val director: String? = null,
)

@Serializable
data class Carnet(val reactions: List<String> = emptyList(), val comment: String? = null)

@Serializable
data class JournalItem(val entry: LogEntry, val media: JournalMedia, val carnet: Carnet)

@Serializable
data class JournalResponse(val items: List<JournalItem>, val next_cursor: String? = null)
