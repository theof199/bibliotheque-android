package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * `rating`, `reactions` et `comment` ont tous une valeur par défaut nulle : le
 * `Json` qui encode les requêtes (`ApiClient.ApiJson`, `explicitNulls =
 * false`) omet alors la clé plutôt que d'envoyer `null`. Nécessaire pour
 * `rating` — le back ne remonte la note au suivi que si le corps porte
 * `rating` (`if (body.rating !== undefined)`) — et, depuis le correctif du
 * 16 septembre 2026, pour `reactions` et `comment` aussi : le `POST` écrit
 * désormais le carnet champ par champ, comme le `PATCH`, et un champ absent
 * laisse le carnet du jour déjà écrit en place. `FormViewModel` n'envoie donc
 * `reactions` ou `comment` que si le formulaire en porte vraiment ; vide, il
 * les omet plutôt que d'envoyer `[]` ou `""`, ce qui effacerait un carnet déjà
 * écrit sur une revoyure du même jour.
 */
@Serializable
data class JournalCreateBody(
    val media_id: String,
    val finished_at: String,
    val rating: Int? = null,
    val reactions: List<String>? = null,
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
    // Le `tmdb_id` du film (le carnet ne connaît que des films) — depuis le
    // 14 septembre 2026, sert à « Au ciné » à rapprocher une affiche de
    // `SortieFilm.tmdb_id` d'une entrée déjà journalisée. Défaut vide plutôt
    // que nullable : le champ est toujours présent côté back.
    val external_id: String = "",
)

@Serializable
data class Carnet(val reactions: List<String> = emptyList(), val comment: String? = null)

@Serializable
data class JournalItem(val entry: LogEntry, val media: JournalMedia, val carnet: Carnet)

@Serializable
data class JournalResponse(val items: List<JournalItem>, val next_cursor: String? = null)
