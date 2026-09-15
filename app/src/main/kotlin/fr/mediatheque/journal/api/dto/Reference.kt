package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * `GET /reference/sorties` (chantier « Au ciné », 14 puis 15 septembre 2026).
 *
 * Deux natures de données depuis le 15 septembre 2026, dans deux types
 * distincts : `en_cours` (`SortiesEnCours`, mes cinémas, Allociné) et
 * `prochaine` (`SortieSemaine`, TMDB, inchangée dans sa forme).
 *
 * `tmdb_id` est numérique ici — à la différence de `SearchResult.external_id`,
 * une chaîne — parce que cette route ne connaît que TMDB et n'a donc pas à
 * porter la généricité de la recherche.
 *
 * Sans `directors` depuis le correctif du 14 septembre 2026 : le champ venait
 * d'une fiche détaillée relue par film côté back, et relire 40 à 80 fiches
 * d'un coup saturait la file sortante TMDB (`RATE_LIMITED`). Retiré du
 * contrat avec le champ. `SortieCinemaFilm`, ci-dessous, le retrouve : il
 * vient d'Allociné, pas d'une fiche TMDB relue en plus.
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

/**
 * Un film à l'affiche aujourd'hui dans un des cinémas du propriétaire (brief
 * du 15 septembre 2026). `tmdb_id` **nullable** : la résolution par
 * recherche TMDB, côté back, peut ne rien trouver d'assez sûr — une tuile
 * sans `tmdb_id` n'a rien à afficher ni à suivre, elle n'est pas touchable.
 */
@Serializable
data class SortieCinemaFilm(
    val tmdb_id: Int? = null,
    val allocine_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val release_date: String? = null,
    val cover_url: String? = null,
    val directors: List<String> = emptyList(),
    val cinemas: List<String> = emptyList(),
)

/**
 * Les films à l'affiche aujourd'hui dans les cinémas du propriétaire —
 * calculés par une tâche de fond côté back, jamais à la demande.
 * `cinemas_configures` faux tant qu'aucun cinéma n'est configuré :
 * `films` est alors toujours vide. `calcule_le` est nul tant que la tâche
 * n'a jamais tourné.
 */
@Serializable
data class SortiesEnCours(
    val du: String,
    val au: String,
    val calcule_le: String? = null,
    val cinemas_configures: Boolean = false,
    val films: List<SortieCinemaFilm> = emptyList(),
)

@Serializable
data class SortiesResponse(val en_cours: SortiesEnCours, val prochaine: SortieSemaine)

/**
 * `GET /reference/plex` (chantier « La Frise et Ensuite », 15 septembre 2026) : un film demandé
 * sur Seerr et disponible sur le Plex du propriétaire, pas encore vu. `tmdb_id` sert au même
 * rapprochement que `SortieFilm.tmdb_id` — par `media.external_id`, jamais par le titre.
 */
@Serializable
data class PlexFilm(
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val cover_url: String? = null,
    val demande_le: String,
)

/**
 * `configure` faux tant que Seerr n'est pas configuré côté back (`SEERR_URL`, `SEERR_API_KEY`,
 * `SEERR_USER`) — `films` est alors toujours vide, et la Frise ne montre que les vus, sans
 * en-tête « Tu en es à » (brief du 15 septembre 2026). `calcule_le` est nul tant que la tâche de
 * fond n'a jamais tourné.
 */
@Serializable
data class PlexResponse(
    val configure: Boolean = false,
    val calcule_le: String? = null,
    val films: List<PlexFilm> = emptyList(),
)
