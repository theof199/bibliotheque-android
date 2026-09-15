package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Les réalisateurs (brief du 15 septembre 2026) : cinq routes du back, trois
 * formes de données.
 *
 * `tmdb_id` est numérique partout ici — comme `SortieFilm.tmdb_id` et à la
 * différence de `SearchResult.external_id`, une chaîne : ces routes ne
 * connaissent que TMDB et n'ont pas à porter la généricité de la recherche.
 * Attention, ce n'est pas le même espace de nombres d'une forme à l'autre :
 * `PersonneResult` et `Realisateur` portent l'identifiant d'une **personne**,
 * `FilmDeRealisateur` celui d'un **film**.
 */

/** Un résultat de `GET /reference/personnes?q=` : une personne dont TMDB dit qu'elle réalise. */
@Serializable
data class PersonneResult(val tmdb_id: Int, val name: String, val profile_url: String? = null)

@Serializable
data class PersonnesResponse(val results: List<PersonneResult> = emptyList())

/**
 * Un réalisateur suivi (`GET /me/realisateurs`, et la réponse de `POST`).
 * `name` et `profile_url` sont ceux que TMDB donnait au moment de l'ajout : le
 * back ne le rappelle pas pour rendre cette liste.
 */
@Serializable
data class Realisateur(
    val tmdb_id: Int,
    val name: String,
    val profile_url: String? = null,
    val ajoute_le: String,
)

@Serializable
data class SuivreRealisateurBody(val tmdb_id: Int)

/**
 * Mon visionnage le plus récent d'un film de la filmographie — nul si je ne
 * l'ai jamais journalisé. `entry_id` désigne l'entrée de journal : c'est par
 * lui que la fiche ouvre la correction.
 */
@Serializable
data class VuDuFilm(val entry_id: String, val rating: Int? = null, val finished_at: String)

/**
 * Un film de la filmographie (`GET /me/realisateurs/{tmdbId}/films`), de la
 * plus ancienne sortie à la plus récente. Un film sans date de sortie ne
 * figure pas dans la liste côté back : `release_date` est donc toujours là,
 * et `year` avec elle en pratique.
 *
 * `introuvable` (décision du propriétaire du 15 septembre 2026) est vrai si
 * *je* l'ai moi-même marqué introuvable — jamais la marque d'un autre membre.
 * Par défaut à `false` : le contrat le rend toujours, mais un test qui
 * construit ce DTO à la main n'a pas à le répéter à chaque appel.
 */
@Serializable
data class FilmDeRealisateur(
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val release_date: String? = null,
    val cover_url: String? = null,
    val vu: VuDuFilm? = null,
    val introuvable: Boolean = false,
)

@Serializable
data class FilmographieResponse(val films: List<FilmDeRealisateur> = emptyList())
