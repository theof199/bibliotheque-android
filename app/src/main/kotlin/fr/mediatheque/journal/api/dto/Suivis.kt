package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Ce qu'un réalisateur suivi et une saga suivie ont en commun (brief « les
 * sagas », 15 septembre 2026, jumelle du brief « les réalisateurs ») : la
 * forme d'un film dans leur liste de films est **littéralement la même** côté
 * back (`GET /me/realisateurs/{tmdbId}/films` et `GET /me/sagas/{tmdbId}/films`
 * rendent tous deux `{ films: [{ tmdb_id, title, original_title, year,
 * release_date, cover_url, vu, introuvable }] }`) — un seul DTO les sert
 * plutôt qu'une copie qui diffère à la première retouche. Ce qui distingue
 * les deux sources (le nom, la photo ou l'affiche, les cinq appels réseau)
 * reste dans `Realisateurs.kt` et `Sagas.kt`, propre à chacune.
 */

/**
 * Mon visionnage le plus récent d'un film — nul si je ne l'ai jamais
 * journalisé. `entry_id` désigne l'entrée de journal : c'est par lui que la
 * fiche ouvre la correction.
 */
@Serializable
data class VuDuFilm(val entry_id: String, val rating: Int? = null, val finished_at: String)

/**
 * Un film d'une filmographie ou d'une saga, de la plus ancienne sortie à la
 * plus récente. Un film sans date de sortie ne figure pas dans la liste côté
 * back : `release_date` est donc toujours là, et `year` avec elle en
 * pratique.
 *
 * `introuvable` (décision du propriétaire du 15 septembre 2026) est vrai si
 * *je* l'ai moi-même marqué introuvable — jamais la marque d'un autre membre,
 * et la même marque quelle que soit la liste où le film apparaît (une
 * fiche de réalisateur ou une saga). Par défaut à `false` : le contrat le
 * rend toujours, mais un test qui construit ce DTO à la main n'a pas à le
 * répéter à chaque appel.
 */
@Serializable
data class FilmSuivi(
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
data class FilmsResponse(val films: List<FilmSuivi> = emptyList())
