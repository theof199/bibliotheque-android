package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * `GET /reference/sorties` (chantier « Au ciné », 14 septembre 2026) : les
 * sorties en salle, dans le pays de l'instance, deux semaines à la fois.
 *
 * `tmdb_id` est numérique ici — à la différence de `SearchResult.external_id`,
 * une chaîne — parce que cette route ne connaît que TMDB et n'a donc pas à
 * porter la généricité de la recherche.
 *
 * Sans `directors` depuis le correctif du 14 septembre 2026 : le champ venait
 * d'une fiche détaillée relue par film côté back, et relire 40 à 80 fiches
 * d'un coup saturait la file sortante TMDB (`RATE_LIMITED`). Retiré du
 * contrat avec le champ.
 */
@Serializable
data class SortieFilm(
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val release_date: String? = null,
    val cover_url: String? = null,
)

@Serializable
data class SortieSemaine(val du: String, val au: String, val films: List<SortieFilm> = emptyList())

@Serializable
data class SortiesResponse(val en_cours: SortieSemaine, val prochaine: SortieSemaine)
