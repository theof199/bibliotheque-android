package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * `GET /reference/sorties` (chantier « Au ciné », 14 septembre 2026) : les
 * sorties en salle, dans le pays de l'instance, deux semaines à la fois.
 *
 * `tmdb_id` est numérique ici — à la différence de `SearchResult.external_id`,
 * une chaîne — parce que cette route ne connaît que TMDB et n'a donc pas à
 * porter la généricité de la recherche.
 */
@Serializable
data class SortieFilm(
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val release_date: String? = null,
    val cover_url: String? = null,
    val directors: List<String> = emptyList(),
)

@Serializable
data class SortieSemaine(val du: String, val au: String, val films: List<SortieFilm> = emptyList())

@Serializable
data class SortiesResponse(val en_cours: SortieSemaine, val prochaine: SortieSemaine)
