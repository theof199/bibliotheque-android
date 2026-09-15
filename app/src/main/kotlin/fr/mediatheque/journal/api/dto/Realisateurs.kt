package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Les réalisateurs (brief du 15 septembre 2026) : trois formes de données
 * propres à cette source. Les films de sa filmographie sont une forme
 * *partagée* avec les sagas — `VuDuFilm`, `FilmSuivi`, `FilmsResponse`,
 * `api/dto/Suivis.kt`, dont ce fichier ne porte plus qu'une moitié depuis le
 * brief « les sagas » du même jour.
 *
 * `tmdb_id` est numérique partout ici — comme `SortieFilm.tmdb_id` et à la
 * différence de `SearchResult.external_id`, une chaîne : ces routes ne
 * connaissent que TMDB et n'ont pas à porter la généricité de la recherche.
 * Attention, ce n'est pas le même espace de nombres que `FilmSuivi` :
 * `PersonneResult` et `Realisateur` portent l'identifiant d'une **personne**,
 * `FilmSuivi` celui d'un **film**.
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
