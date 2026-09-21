package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Le Voyage : traverser l'histoire du cinéma année par année, depuis 1895. Réécrit pour le brief
 * du 21 septembre 2026 (« l'année en étages », puis « le ticket », étape 3) — l'année n'est plus
 * une case qu'on coche mais un lieu qu'on creuse en salles, tant qu'on veut, avant de tourner la
 * page avec le ticket. Rien de l'ancien modèle (essentiels, récompense par année, `frontiere`)
 * n'en reste, et `AnneeSuivanteResponse` a disparu avec le bouton provisoire qui l'appelait.
 */

/** Une année telle que `GET /me/voyage` la donne dans sa liste, pour la carte. */
@Serializable
data class AnneeVoyage(
    val annee: Int,
    /** `"ouverte"` (avant l'année en cours, toujours creusable) · `"en_cours"` · `"verrouillee"`. */
    val statut: String,
    /** Une ouverture existe déjà pour cette année, quel que soit son statut. */
    val visitee: Boolean = false,
    /** Films de mon journal sortis cette année-là ; un programme compte un, jamais ses bobines séparément. */
    val profondeur: Int = 0,
    /** L'affiche du n°1 du podium (brief du 21 septembre 2026, « le podium ») — nulle si la marche 1 est vide. */
    val affiche_url: String? = null,
)

/**
 * Un ticket gagné et pas encore montré (brief du 21 septembre 2026, « le ticket ») : porté par
 * `VoyageResponse.ticket_a_montrer`, à afficher une fois puis `POST /me/voyage/tickets/{annee}/montre`.
 */
@Serializable
data class TicketAMontrerVoyage(val annee: Int, val motif: String, val emis_le: String)

/** `GET /me/voyage` — ma progression, la carte. */
@Serializable
data class VoyageResponse(
    val configure: Boolean = false,
    val depart: Int = 1895,
    val annee_en_cours: Int = 1895,
    val annees: List<AnneeVoyage> = emptyList(),
    /** Non nul une seule fois, tant que je ne l'ai pas montré (brief du 21 septembre 2026, « le ticket »). */
    val ticket_a_montrer: TicketAMontrerVoyage? = null,
)

/** Une bobine d'un programme (avant ~1915), avec mon état sur elle. */
@Serializable
data class BobineVoyage(
    val tmdb_id: Int,
    val title: String,
    val duree_min: Int,
    val cover_url: String? = null,
    /** Lien web ou `plex://`, selon ce que le back a trouvé — jamais les deux, nul si absente du Plex. */
    val plex_url: String? = null,
    /** `"vu"` · `"sur_le_plex"` · `"demande"` · `"a_demander"` · `"introuvable"`. */
    val etat: String,
)

/** Une séance composée par le chroniqueur, avant les longs métrages. */
@Serializable
data class ProgrammeVoyage(
    val duree_min: Int,
    val bobines: List<BobineVoyage> = emptyList(),
)

/** Un film d'une salle, avec mon état et ma note — un programme s'il porte `programme`. */
@Serializable
data class FilmSalleVoyage(
    val id: String,
    val rang: Int,
    val tmdb_id: Int,
    val title: String,
    val original_title: String? = null,
    val year: Int? = null,
    val realisateur: String,
    /** Deux phrases, nulle si le film était déjà vu quand le chroniqueur l'a proposé. */
    val raison: String? = null,
    val cover_url: String? = null,
    val plex_url: String? = null,
    val etat: String,
    val note: Int? = null,
    val programme: ProgrammeVoyage? = null,
)

/** Une salle de l'année, avec ses films dans l'ordre. */
@Serializable
data class SalleVoyage(
    val id: String,
    val rang: Int,
    val nom: String,
    val raison_d_etre: String,
    /** `"essentiels"` · `"ailleurs"` · nulle pour une salle nommée par le chroniqueur. */
    val cle: String? = null,
    val epuisee: Boolean = false,
    val fournee_en_cours: Boolean = false,
    val films: List<FilmSalleVoyage> = emptyList(),
)

/**
 * Une marche du podium, occupée (brief du 21 septembre 2026, « le podium ») : un item nul de la
 * liste de `AnneeVoyageDetailResponse.podium` ou de `PodiumResponse.podium` dit une marche vide,
 * jamais cette forme avec des champs nuls dedans.
 */
@Serializable
data class PodiumMarcheVoyage(
    val place: Int,
    val tmdb_id: Int? = null,
    val programme_id: String? = null,
    val title: String = "",
    val cover_url: String? = null,
)

/** Le dernier jugement de maturité du chroniqueur sur une année (`prete` seulement, brief du 21 septembre 2026, « le ticket »). */
@Serializable
data class MaturiteVoyage(val mure: Boolean, val motif: String, val jugee_le: String)

/** Le ticket vers l'année suivante, s'il a été gagné (`prete` seulement) — `utilise_le` nul tant qu'il dort. */
@Serializable
data class TicketAnneeVoyage(val annee: Int, val emis_le: String, val utilise_le: String? = null)

/**
 * `GET /me/voyage/annees/{annee}` — les trois formes possibles du back aplaties en un seul DTO :
 * `configure` et `statut` disent laquelle est arrivée (`configure: false` / `en_preparation` /
 * `verrouillee` / `prete`), comme le faisait `ChroniqueAnneeResponse` pour l'ancien modèle. C'est
 * `EtatAnnee` (`ui/frise/AnneeViewModel.kt`) qui les interprète, en fonction pure et testée en JVM.
 */
@Serializable
data class AnneeVoyageDetailResponse(
    val configure: Boolean = false,
    val statut: String? = null,
    val annee: Int? = null,
    val profondeur: Int? = null,
    val ouverture: String? = null,
    val faits: List<String> = emptyList(),
    val ecrite_le: String? = null,
    val salles: List<SalleVoyage> = emptyList(),
    /** Les trois marches, dans l'ordre — vide (plutôt que `[null, null, null]`) tant que le back n'en sert pas. */
    val podium: List<PodiumMarcheVoyage?> = emptyList(),
    val maturite: MaturiteVoyage? = null,
    val ticket: TicketAnneeVoyage? = null,
)

/** Corps de `PUT /me/voyage/annees/{annee}/podium/{place}` : `tmdb_id` **ou** `programme_id`, jamais les deux. */
@Serializable
data class PodiumBody(val tmdb_id: Int? = null, val programme_id: String? = null)

/** `PUT /me/voyage/annees/{annee}/podium/{place}` — le podium complet après l'écriture. */
@Serializable
data class PodiumResponse(val podium: List<PodiumMarcheVoyage?> = emptyList())

/**
 * Un ticket du portefeuille, tel que `GET /me/voyage/tickets` le donne (brief du 21 septembre
 * 2026, « le ticket ») : `montre_le` et `utilise_le` restent nuls jusqu'à `POST .../montre` et
 * `POST .../utiliser`. Un ticket ne se périme pas.
 */
@Serializable
data class TicketVoyage(
    val annee: Int,
    val motif: String,
    val emis_le: String,
    val montre_le: String? = null,
    val utilise_le: String? = null,
)

/** `GET /me/voyage/tickets` — mon portefeuille de tickets, par année croissante. */
@Serializable
data class VoyageTicketsResponse(val tickets: List<TicketVoyage> = emptyList())

/** `POST /me/voyage/tickets/{annee}/utiliser` — le ticket est encaissé, mon année en cours a avancé. */
@Serializable
data class TicketUtiliseResponse(val annee_en_cours: Int = 1895)

/** `POST /me/voyage/salles/{salleId}/plus` — « En voir plus » sur une salle. */
@Serializable
data class SallePlusResponse(
    /** `"en_preparation"` (la fournée vient de s'enfiler, ou en avait déjà une en cours) · `"epuisee"`. */
    val statut: String = "epuisee",
)

/** `GET /reference/chroniques/films/{tmdbId}` — le carton « Et pendant ce temps… », inchangé. */
@Serializable
data class CartonFilmResponse(
    val configure: Boolean = false,
    val statut: String? = null,
    val tmdb_id: Int? = null,
    val contexte: String? = null,
    val faits: List<String> = emptyList(),
)

/** `POST /me/voyage/demander/{tmdbId}`. */
@Serializable
data class DemanderVoyageResponse(val demande: Boolean = false)
