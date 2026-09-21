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

/**
 * La page réalisateur (brief du 21 septembre 2026, « la page réalisateur ») : trois formes de
 * données neuves, propres à `GET /reference/films/{tmdbId}/realisateurs` et
 * `GET /me/realisateurs/{tmdbId}/page` — distinctes de `Realisateur` ci-dessus (qui porte
 * `profile_url`/`ajoute_le`, propres à `GET /me/realisateurs`) et de `FilmSuivi` (`Suivis.kt`, qui
 * ne porte ni `type` ni `sur_le_plex`/`demande`/`plex_url`/`annee_ouverte`/`voyage` : la
 * filmographie d'une page mêle films et séries, avec assez d'état du Voyage pour choisir la fiche
 * au tap).
 */

/** Un réalisateur crédité sur un film (`GET /reference/films/{tmdbId}/realisateurs`) — juste de quoi choisir ou naviguer. */
@Serializable
data class RealisateurCredit(val tmdb_id: Int, val name: String)

@Serializable
data class RealisateursDuFilmResponse(val realisateurs: List<RealisateurCredit> = emptyList())

/** Ce film dans mon Voyage, à sa plus ancienne apparition — nul s'il n'est dans aucune de mes salles. */
@Serializable
data class VoyageDeFilmographie(val annee: Int, val salle_id: String, val film_id: String)

/**
 * Un film ou une série de la filmographie complète d'un réalisateur, pour l'écran « page
 * réalisateur ». `type` distingue un film (`"movie"`) d'une série (`"tv"`, dont `vu` est toujours
 * nul : les séries se suivent par `/media`, pas par cette page). `voyage` non nul dit que ce film
 * a sa fiche dans le Voyage plutôt qu'une fiche simple (`destinationFilm`, `RealisateurEtats.kt`).
 */
@Serializable
data class FilmDeFilmographie(
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val release_date: String,
    val cover_url: String? = null,
    val vu: VuDuFilm? = null,
    val introuvable: Boolean = false,
    val type: String = "movie",
    val sur_le_plex: Boolean = false,
    val demande: Boolean = false,
    val plex_url: String? = null,
    val annee_ouverte: Boolean = false,
    val voyage: VoyageDeFilmographie? = null,
)

/** `GET /me/realisateurs/{tmdbId}/page` : sa fiche et sa filmographie complète, films et séries. */
@Serializable
data class RealisateurPageResponse(
    val tmdb_id: Int,
    val name: String,
    val photo_url: String? = null,
    val naissance: String? = null,
    val deces: String? = null,
    val presentation: String = "",
    val suivi: Boolean = false,
    val films: List<FilmDeFilmographie> = emptyList(),
)
