package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Le Voyage (brief du 16 septembre 2026, phase 1 « le moteur ») : traverser l'histoire du cinéma
 * année par année, depuis 1895. Claude écrit un récit et des essentiels par année, et un carton
 * « Et pendant ce temps… » par film journalisé.
 *
 * Comme `PlexResponse`, des DTO plats plutôt qu'une union stricte : `configure` et `statut` disent
 * laquelle des trois formes du back est arrivée (`configure: false` / `en_preparation` /
 * `prete`) — c'est `EtatChronique` (`ui/frise/AnneeViewModel.kt`) qui les interprète, en fonction
 * pure et testée en JVM.
 */

@Serializable
data class ChroniqueEssentiel(
    val rang: Int,
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val realisateur: String,
    val pourquoi: String,
    val cover_url: String? = null,
)

/** `GET /reference/chroniques/annees/{annee}`. */
@Serializable
data class ChroniqueAnneeResponse(
    val configure: Boolean = false,
    val statut: String? = null,
    val annee: Int? = null,
    val recit: String? = null,
    val faits: List<String> = emptyList(),
    val essentiels: List<ChroniqueEssentiel> = emptyList(),
)

/** `GET /reference/chroniques/films/{tmdbId}` — le carton « Et pendant ce temps… ». */
@Serializable
data class CartonFilmResponse(
    val configure: Boolean = false,
    val statut: String? = null,
    val tmdb_id: Int? = null,
    val contexte: String? = null,
    val faits: List<String> = emptyList(),
)

/**
 * Un essentiel de l'année ouverte, avec mon état — seule l'année ouverte de `VoyageResponse` en
 * porte (`GET /me/voyage`) ; `GET /reference/chroniques/annees/{annee}` sert la même liste, sans
 * `etat` ni `note`, qui dépendent du membre.
 */
@Serializable
data class EssentielVoyage(
    val rang: Int,
    val tmdb_id: Int,
    val title: String,
    val year: Int? = null,
    val cover_url: String? = null,
    val realisateur: String,
    val pourquoi: String,
    /** `"vu"` · `"sur_le_plex"` · `"a_trouver"` · `"introuvable"`. */
    val etat: String,
    val note: Int? = null,
)

/**
 * Une affiche d'essentiel d'une année **verrouillée** — le carton « Prochainement » d'`AnneeScreen`
 * la montre floutée (brief du 16 septembre 2026, phase 2). Sans titre ni rien d'autre : une année
 * pas encore ouverte ne se déflore pas.
 */
@Serializable
data class ApercuEssentiel(val cover_url: String? = null)

@Serializable
data class AnneeVoyage(
    val annee: Int,
    /** `"faite"` · `"ouverte"` · `"verrouillee"`. */
    val statut: String,
    val vus: Int = 0,
    val essentiels_total: Int? = null,
    val essentiels_faits: Int? = null,
    /** Seulement sur l'année ouverte, et seulement si sa chronique existe déjà. */
    val essentiels: List<EssentielVoyage>? = null,
    /**
     * Les affiches des essentiels d'une année **verrouillée**, quand sa chronique existe déjà
     * (brief du 16 septembre 2026, phase 2). Optionnel et nullable : le lot back qui l'ajoute
     * vient après celui-ci, et l'instance en ligne ne le sert pas encore — absent ou vide, le
     * carton « Prochainement » se contente de compter les essentiels qui attendent.
     */
    val essentiels_apercu: List<ApercuEssentiel>? = null,
)

/** `GET /me/voyage` — ma progression. */
@Serializable
data class VoyageResponse(
    val configure: Boolean = false,
    val depart: Int = 1895,
    val frontiere: Int = 1895,
    /** `"ouverte"` · `"en_preparation"`. */
    val frontiere_statut: String = "en_preparation",
    val annees: List<AnneeVoyage> = emptyList(),
)

/** `POST /me/voyage/demander/{tmdbId}`. */
@Serializable
data class DemanderVoyageResponse(val demande: Boolean = false)
