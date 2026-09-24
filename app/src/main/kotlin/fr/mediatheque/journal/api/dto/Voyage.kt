package fr.mediatheque.journal.api.dto

import kotlinx.serialization.Serializable

/**
 * Le Voyage : traverser l'histoire du cinéma année par année, depuis 1895. Réécrit pour le brief
 * du 21 septembre 2026 (« l'année en étages », puis « le ticket », étape 3, puis « la chronique et
 * les salles », étape 4, puis « les récompenses et le passeport », étape 5) — l'année n'est plus
 * une case qu'on coche mais un lieu qu'on creuse en salles, tant qu'on veut, avant de tourner la
 * page avec le ticket. `AnneeSuivanteResponse` a disparu avec le bouton provisoire qui l'appelait ;
 * la récompense et le passeport, eux, reviennent sur les nouveaux seuils de la spec du
 * 19 septembre 2026, §6 (Ours, Lion, Palme — plus jamais l'ancien calcul par essentiels).
 *
 * Revu le 24 septembre 2026 (« le voyage revu ») : « Ajouter à la chronique » (par film) et le
 * carnet PDF ont disparu du contrat, remplacés par deux pop-in synchrones — le contexte d'une
 * salle (`SalleVoyage.contexte`, `POST .../salles/{salleId}/contexte`) et le générique de fin
 * d'année (`AnneeVoyageDetailResponse.generique`, `POST .../generique`) — et le carton d'un film
 * (`CartonFilmResponse`) se recentre sur le film et son réalisateur (`titre`, `texte`), à la place
 * de l'ancien « Et pendant ce temps… » (`contexte`, `faits`).
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
    /** `"ours"` · `"lion"` · `"palme"` (étape 5, spec du 19 septembre 2026, §6) — nulle sans aucun film vu. */
    val recompense: String? = null,
)

/**
 * Une décennie bouclée du passeport, telle que `GET /me/voyage` la donne (`tampons`, étape 5) :
 * chacune de ses dix années a un Ours et le ticket de la décennie suivante a été utilisé.
 */
@Serializable
data class TamponVoyage(val decennie: Int, val boucle_le: String)

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
    /** Le passeport : les décennies bouclées, décennie croissante (étape 5). */
    val tampons: List<TamponVoyage> = emptyList(),
    /** La séance prise, tant que son long n'est pas encore vu (brief du 21 septembre 2026, « la séance ») — nulle sinon. */
    val seance_prise: SeancePriseVoyage? = null,
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
    /** Un vrai paragraphe sur ce que la salle raconte de l'année — nul tant qu'il n'a pas été demandé (`POST .../contexte`, décision 3 du brief du 24 septembre 2026, « le voyage revu »). */
    val contexte: String? = null,
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
 * La dernière demande de nouvelle salle, tant qu'elle compte encore : `en_cours`, ou `refusee` et
 * pas encore vue (décision 3 du brief du 21 septembre 2026, « la chronique et les salles »).
 */
@Serializable
data class DemandeSalleVoyage(
    val id: String,
    val demande: String,
    /** `"en_cours"` · `"creee"` · `"refusee"` — en pratique jamais `creee` ici : une fois acceptée, la salle est dans `salles` et cette demande disparaît. */
    val statut: String,
    /** Pourquoi il n'y avait pas de quoi — seulement si `refusee`. */
    val motif: String? = null,
    val salle_id: String? = null,
)

/**
 * Une salle que le chroniqueur propose sans jamais l'ouvrir (brief du 22 septembre 2026, « les
 * pistes ») : une pastille au-dessus du champ de « Nouvelle salle » — vide possible, renouvelée
 * trois à la fois par `POST /me/voyage/annees/{annee}/pistes`. Utiliser une piste pour ouvrir une
 * salle (`POST /me/voyage/annees/{annee}/salles`, corps `piste`) l'en retire.
 */
@Serializable
data class PisteVoyage(val nom: String, val raison: String)

/**
 * Ma progression vers le Lion et la Palme, pour une année (`prete` seulement, étape 5) : de quoi
 * dessiner la ligne « *N* essentiels sur *M* · *N* salles complètes sur *M* » sous la profondeur.
 */
@Serializable
data class ProgressionVoyage(
    val essentiels_vus: Int = 0,
    val essentiels_total: Int = 0,
    val salles_completes: Int = 0,
    val salles_autres: Int = 0,
)

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
    /** `"ours"` · `"lion"` · `"palme"` (`prete` seulement, étape 5) — nulle tant qu'aucun film de l'année n'est vu. */
    val recompense: String? = null,
    /** `prete` seulement (étape 5). */
    val progression: ProgressionVoyage? = null,
    val ouverture: String? = null,
    val faits: List<String> = emptyList(),
    val ecrite_le: String? = null,
    val salles: List<SalleVoyage> = emptyList(),
    /** Les trois marches, dans l'ordre — vide (plutôt que `[null, null, null]`) tant que le back n'en sert pas. */
    val podium: List<PodiumMarcheVoyage?> = emptyList(),
    val maturite: MaturiteVoyage? = null,
    val ticket: TicketAnneeVoyage? = null,
    val demande_salle: DemandeSalleVoyage? = null,
    /** Des salles que le chroniqueur propose sans les ouvrir (brief du 22 septembre 2026, « les pistes ») — `prete` seulement, vide possible. */
    val pistes: List<PisteVoyage> = emptyList(),
    /** Mes séances composées cette année (brief du 21 septembre 2026, « la séance »), `prete` seulement. */
    val seances: List<SeanceVoyage> = emptyList(),
    /** Une composition vient d'être demandée et s'écrit encore — `prete` seulement. */
    val seance_en_cours: Boolean = false,
    /** Le générique de fin d'année — mon parcours dans l'année, nul tant qu'il n'a pas été demandé (`POST .../generique`, décision 5 du brief du 24 septembre 2026, « le voyage revu »). Aussi écrit en arrière-plan à l'octroi du ticket. */
    val generique: String? = null,
)

/**
 * Corps de `POST /me/voyage/annees/{annee}/salles` : une phrase de 1 à 200 caractères, `piste`
 * (nul par défaut) reprenant le `nom` d'une piste de l'année si j'en ai suivi une (brief du
 * 22 septembre 2026, « les pistes »).
 */
@Serializable
data class DemandeSalleBody(val demande: String, val piste: String? = null)

/** `POST /me/voyage/annees/{annee}/salles` — toujours `202`, la génération vient de s'enfiler. */
@Serializable
data class DemandeSalleEcritureResponse(val statut: String = "en_preparation", val demande_id: String = "")

/**
 * `POST /me/voyage/annees/{annee}/pistes` (brief du 22 septembre 2026, « les pistes ») : les
 * trois pistes qui remplacent la liste précédente de l'année — appel synchrone, sans `statut`
 * `en_preparation` ni relecture.
 */
@Serializable
data class PistesVoyageResponse(val pistes: List<PisteVoyage> = emptyList())

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

/**
 * `GET /reference/chroniques/films/{tmdbId}` — le carton d'un film, recentré sur le film et son
 * réalisateur depuis le brief du 24 septembre 2026, « le voyage revu » (version 2 : la réalisation,
 * les intentions du réalisateur, la réception et les innovations — plus de `contexte` ni de
 * `faits`, remplacés par `titre` et `texte`).
 */
@Serializable
data class CartonFilmResponse(
    val configure: Boolean = false,
    val statut: String? = null,
    val tmdb_id: Int? = null,
    val titre: String? = null,
    val texte: String? = null,
)

/** `POST /me/voyage/annees/{annee}/salles/{salleId}/contexte` — le contexte de cette salle, écrit ou déjà là. */
@Serializable
data class SalleContexteEcritureResponse(val contexte: String)

/** `POST /me/voyage/annees/{annee}/generique` — le générique de fin d'année, écrit ou déjà là. */
@Serializable
data class GeneriqueEcritureResponse(val generique: String)

/** `POST /me/voyage/demander/{tmdbId}`. */
@Serializable
data class DemanderVoyageResponse(val demande: Boolean = false)

// --- La séance (brief du 21 septembre 2026, « la séance »). ---

/** La bobine composée pour ce soir — seulement son identité, jamais son état (celui du `court` qui la porte fait foi). */
@Serializable
data class SeanceBobineVoyage(val tmdb_id: Int, val title: String)

/** Le long ou le court d'une séance : `bobine` non nulle seulement quand le court est une bobine précise du programme. */
@Serializable
data class SeanceFilmVoyage(
    val film_id: String,
    val tmdb_id: Int,
    val title: String,
    val cover_url: String? = null,
    val salle: String,
    val etat: String,
    val plex_url: String? = null,
    val bobine: SeanceBobineVoyage? = null,
)

/** Une séance composée par le chroniqueur — un long jamais vu, un court en ouverture, une anecdote. */
@Serializable
data class SeanceVoyage(
    val id: String,
    val rang: Int,
    /** `"proposee"` · `"prise"` · `"ignoree"`. */
    val statut: String,
    val composee_le: String,
    val anecdote: String,
    val long: SeanceFilmVoyage,
    val court: SeanceFilmVoyage? = null,
)

/** `POST /me/voyage/annees/{annee}/seances` — toujours `202`, la composition vient de s'enfiler (ou en avait déjà une en cours). */
@Serializable
data class SeanceComposerResponse(val statut: String = "en_preparation")

/** Corps de `POST /me/voyage/seances/{id}/remplacer` : `morceau` (`"long"`|`"court"`), `film_id`, `+ bobine_tmdb_id` pour une bobine. */
@Serializable
data class SeanceRemplacerBody(val morceau: String, val film_id: String, val bobine_tmdb_id: Int? = null)

/** `POST .../remplacer`, `.../prendre`, `.../ignorer` — la séance après l'écriture. */
@Serializable
data class SeanceEcritureResponse(val seance: SeanceVoyage)

/** Le long de la séance prise, tel que `GET /me/voyage` le donne. */
@Serializable
data class SeancePriseFilmVoyage(val title: String, val cover_url: String? = null)

/** Le court de la séance prise — juste son titre. */
@Serializable
data class SeancePriseCourtVoyage(val title: String)

/** La séance prise, tant que son long n'est pas encore vu — nulle dès qu'il l'est. */
@Serializable
data class SeancePriseVoyage(
    val id: String,
    val annee: Int,
    val long: SeancePriseFilmVoyage,
    val court: SeancePriseCourtVoyage? = null,
)

/**
 * La dépense IA d'un mois, pour moi seul (décision 2 du brief du 21 septembre 2026, « les
 * dépenses ») : une ligne `appels_ia` par appel réussi (ouverture, fournée) déclenché — le carton
 * d'un film n'y figure pas, il ne dépend d'aucun membre en particulier. `cout_centimes` est une
 * estimation au tarif public d'Anthropic, la facture du compte Anthropic fait foi.
 */
@Serializable
data class DepenseMoisVoyage(
    /** Année-mois, ISO, fuseau du serveur — `"2026-08"`. */
    val mois: String,
    val appels: Int,
    val input_tokens: Int,
    val output_tokens: Int,
    val cout_centimes: Double,
)

/** `GET /me/voyage/depenses` — mes dépenses au chroniqueur, du plus ancien au plus récent. */
@Serializable
data class VoyageDepensesResponse(val mois: List<DepenseMoisVoyage> = emptyList())
