package fr.mediatheque.journal.ui.frise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.BobineVoyage
import fr.mediatheque.journal.api.dto.CarnetAnneeVoyage
import fr.mediatheque.journal.api.dto.ChroniqueBody
import fr.mediatheque.journal.api.dto.DemandeSalleVoyage
import fr.mediatheque.journal.api.dto.FilmSalleVoyage
import fr.mediatheque.journal.api.dto.MaturiteVoyage
import fr.mediatheque.journal.api.dto.ParagrapheVoyage
import fr.mediatheque.journal.api.dto.PisteVoyage
import fr.mediatheque.journal.api.dto.PodiumMarcheVoyage
import fr.mediatheque.journal.api.dto.ProgrammeVoyage
import fr.mediatheque.journal.api.dto.ProgressionVoyage
import fr.mediatheque.journal.api.dto.SalleVoyage
import fr.mediatheque.journal.api.dto.SeanceBobineVoyage
import fr.mediatheque.journal.api.dto.SeanceFilmVoyage
import fr.mediatheque.journal.api.dto.SeanceRemplacerBody
import fr.mediatheque.journal.api.dto.SeanceVoyage
import fr.mediatheque.journal.api.dto.TicketAnneeVoyage
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le Voyage, page d'année (brief du 21 septembre 2026, « l'année en étages », « le podium », puis
 * « le ticket », puis « la chronique et les salles ») : la chronique de l'année (ouverture, faits,
 * paragraphes), ses salles, son podium et le ticket qu'elle a pu gagner vers l'année suivante,
 * chacun avec ses films et mon état sur chacun, depuis `GET /me/voyage/annees/{annee}`.
 *
 * Remplace entièrement le modèle « essentiels » du 16 septembre 2026 — plus de frontière : une
 * année ouverte se creuse en salles, sans jamais être « finie ». Le podium (étape 2), lui, s'écrit
 * et se retire d'ici (`poserPodium`, `retirerPodium`) ; le ticket (étape 3, `utiliserTicket`) a
 * remplacé le bouton provisoire « Année suivante » ; l'étape 4 (`ajouterChronique`,
 * `ouvrirNouvelleSalle`) allonge la chronique et ouvre des salles à la demande. `Screen.FicheVoyage`
 * lit ce même `ViewModel` (indexé sur l'année, `Root.kt`) pour la fiche d'un film ou d'une bobine,
 * plutôt que d'en recharger une copie.
 */

/** Un film d'une salle est vu, sur le Plex, demandé, à demander ou introuvable. */
data class BobineUi(
    val tmdbId: Int,
    val title: String,
    val dureeMin: Int,
    val coverUrl: String?,
    val plexUrl: String?,
    val etat: String,
)

data class ProgrammeUi(val dureeMin: Int, val bobines: List<BobineUi>)

data class FilmSalleUi(
    val id: String,
    val rang: Int,
    val tmdbId: Int,
    val title: String,
    val originalTitle: String?,
    val year: Int?,
    val realisateur: String,
    val raison: String?,
    val coverUrl: String?,
    val plexUrl: String?,
    val etat: String,
    val note: Int?,
    val programme: ProgrammeUi?,
)

data class SalleUi(
    val id: String,
    val rang: Int,
    val nom: String,
    val raisonDEtre: String,
    val cle: String?,
    val epuisee: Boolean,
    val fourneeEnCours: Boolean,
    val films: List<FilmSalleUi>,
)

/** Une marche du podium, occupée (brief du 21 septembre 2026, « le podium ») — `null` dans `AnneeUi.podium` pour une marche vide. */
data class PodiumMarcheUi(val place: Int, val tmdbId: Int?, val programmeId: String?, val title: String, val coverUrl: String?)

/** Le dernier jugement du chroniqueur sur cette année (décision 4 du brief du 21 septembre 2026, « le ticket »). */
data class MaturiteUi(val mure: Boolean, val motif: String)

/** Le ticket vers l'année suivante, s'il a été gagné (décision 4) — `utilise` dit s'il l'a déjà été encaissé. */
data class TicketAnneeUi(val annee: Int, val utilise: Boolean)

/** Un paragraphe de la chronique, ajouté à la demande sur un film (décision 1 du brief du 21 septembre 2026, « la chronique et les salles »). */
data class ParagrapheUi(
    val id: String,
    val tmdbId: Int?,
    val programmeId: String?,
    val titre: String,
    val texte: String,
    val ecritLe: String,
    val filmTitle: String,
    val filmCoverUrl: String?,
)

/** La dernière demande de nouvelle salle, tant qu'elle compte encore (décision 3 du brief du 21 septembre 2026). */
data class DemandeSalleUi(val id: String, val demande: String, val statut: String, val motif: String?)

/** Ma progression vers le Lion et la Palme, pour cette année (étape 5 du brief du 21 septembre 2026, « les récompenses »). */
data class ProgressionUi(val essentielsVus: Int, val essentielsTotal: Int, val sallesCompletes: Int, val sallesAutres: Int)

/** La bobine composée pour ce soir (décision 2 du brief du 21 septembre 2026, « la séance ») — un simple titre, jamais son propre état. */
data class SeanceBobineUi(val tmdbId: Int, val title: String)

/** Le long ou le court d'une séance (décision 2) : état et lien Plex portés par ce film lui-même, même quand `bobine` en précise une. */
data class SeanceFilmUi(
    val filmId: String,
    val tmdbId: Int,
    val title: String,
    val coverUrl: String?,
    val salle: String,
    val etat: String,
    val plexUrl: String?,
    val bobine: SeanceBobineUi?,
)

/** Une séance composée par le chroniqueur (décision 1-2 du brief du 21 septembre 2026, « la séance »). */
data class SeanceUi(
    val id: String,
    val rang: Int,
    /** `"proposee"` · `"prise"` · `"ignoree"`. */
    val statut: String,
    val composeeLe: String,
    val anecdote: String,
    val long: SeanceFilmUi,
    val court: SeanceFilmUi?,
)

/**
 * La ligne sous la profondeur, dans l'en-tête de la fiche d'année (décision 2 du brief du
 * 21 septembre 2026, « les récompenses ») : « *N* essentiels sur *M* · *N* salles complètes sur
 * *M* », ou « Aucun essentiel encore » si l'année n'a pas d'essentiel connu — nulle (la ligne ne
 * s'affiche pas) tant que la progression n'est pas encore chargée. Fonction pure, testée en JVM.
 */
fun ligneProgression(progression: ProgressionUi?): String? {
    if (progression == null) return null
    if (progression.essentielsTotal == 0) return "Aucun essentiel encore"
    val essentiels = "${progression.essentielsVus} essentiel${if (progression.essentielsVus > 1) "s" else ""} sur ${progression.essentielsTotal}"
    val salles = "${progression.sallesCompletes} salle${if (progression.sallesCompletes > 1) "s" else ""} " +
        "complète${if (progression.sallesCompletes > 1) "s" else ""} sur ${progression.sallesAutres}"
    return "$essentiels · $salles"
}

/**
 * La ligne du bas de la fiche d'année (décision 4 du brief du 21 septembre 2026, « le ticket »),
 * à la place du bouton provisoire « Année suivante » : le ticket non utilisé prime sur le verdict
 * de maturité — fonction pure, testée en JVM.
 */
sealed interface LigneBasAnnee {
    /** « Ton ticket pour *anneeSuivante* t'attend », avec un bouton « Utiliser ». */
    data class TicketEnAttente(val anneeSuivante: Int) : LigneBasAnnee
    /** « Pas encore mûre : *motif* », sans bouton. */
    data class PasEncoreMure(val motif: String) : LigneBasAnnee
    /** Rien : ticket déjà utilisé, ou verdict positif sans ticket émis pour l'instant. */
    data object Rien : LigneBasAnnee
}

fun ligneBasAnnee(ticket: TicketAnneeUi?, maturite: MaturiteUi?): LigneBasAnnee = when {
    ticket != null && !ticket.utilise -> LigneBasAnnee.TicketEnAttente(ticket.annee)
    maturite != null && !maturite.mure -> LigneBasAnnee.PasEncoreMure(maturite.motif)
    else -> LigneBasAnnee.Rien
}

/** L'état d'une année en détail — jumeau d'`EtatChronique`, avec `VERROUILLEE` en plus (§2 du brief). */
enum class EtatAnnee { PRETE, EN_PREPARATION, VERROUILLEE, ABANDON, NON_CONFIGURE }

data class AnneeUi(
    val annee: Int,
    val statutVoyage: StatutAnneeVoyage? = null,
    val etat: EtatAnnee = EtatAnnee.NON_CONFIGURE,
    val essais: Int = 0,
    val profondeur: Int = 0,
    /** Repliée par défaut (spec du 19 septembre 2026, §3) — « Lire la suite » la déplie. */
    val ouvertureDepliee: Boolean = false,
    val ouverture: String? = null,
    val faits: List<String> = emptyList(),
    val salles: List<SalleUi> = emptyList(),
    /** Les trois marches, dans l'ordre — chacune nulle si vide (brief du 21 septembre 2026, « le podium »). */
    val podium: List<PodiumMarcheUi?> = List(3) { null },
    /** Le dernier jugement de maturité (décision 4 du brief du 21 septembre 2026, « le ticket ») — nul tant qu'aucun film de l'année n'a encore été noté. */
    val maturite: MaturiteUi? = null,
    /** Le ticket vers l'année suivante, s'il a été gagné (décision 4) — nul sinon. */
    val ticket: TicketAnneeUi? = null,
    /** Par `ecritLe` croissant (décision 1 du brief du 21 septembre 2026, « la chronique et les salles »). */
    val paragraphes: List<ParagrapheUi> = emptyList(),
    /** Les cibles (`tmdbId` à `programmeId`) dont le paragraphe est en cours d'écriture — celles du back et celle qu'on vient de demander, avant la première relecture. */
    val paragraphesEnCours: Set<Pair<Int?, String?>> = emptySet(),
    /** La dernière demande de nouvelle salle, tant qu'elle compte encore (décision 3) — nulle sinon. */
    val demandeSalle: DemandeSalleUi? = null,
    /** La récompense de l'année (`prete` seulement, étape 5, « les récompenses ») — nulle sans aucun film vu. */
    val recompense: Recompense? = null,
    /** Ma progression vers le Lion et la Palme (`prete` seulement, étape 5) — nulle avant le premier chargement. */
    val progression: ProgressionUi? = null,
    /** Mes séances composées cette année, par rang croissant (décision 1-2 du brief du 21 septembre 2026, « la séance ») — `prete` seulement. */
    val seances: List<SeanceUi> = emptyList(),
    /** Une composition vient d'être demandée et s'écrit encore (décision 1) — `prete` seulement. */
    val seanceEnCours: Boolean = false,
    /** Le carnet de cette année (décision 2 du brief du 22 septembre 2026, « le carnet ») — nul tant qu'il n'a pas été fabriqué. */
    val carnet: CarnetUi? = null,
    /** Sa fabrication tourne encore (décision 2) — `prete` seulement. */
    val carnetEnCours: Boolean = false,
    /** Les pistes de salles proposées par le chroniqueur (brief du 22 septembre 2026, « les pistes ») — `prete` seulement, vide possible. */
    val pistes: List<PisteUi> = emptyList(),
    /** « D'autres pistes » vient d'être demandé et l'appel synchrone tourne encore (décision 3 du brief). */
    val pistesEnCours: Boolean = false,
)

private fun BobineVoyage.versUi() = BobineUi(tmdb_id, title, duree_min, cover_url, plex_url, etat)
private fun ProgrammeVoyage.versUi() = ProgrammeUi(duree_min, bobines.map { it.versUi() })
private fun FilmSalleVoyage.versUi() = FilmSalleUi(
    id = id,
    rang = rang,
    tmdbId = tmdb_id,
    title = title,
    originalTitle = original_title,
    year = year,
    realisateur = realisateur,
    raison = raison,
    coverUrl = cover_url,
    plexUrl = plex_url,
    etat = etat,
    note = note,
    programme = programme?.versUi(),
)
private fun SalleVoyage.versUi() = SalleUi(id, rang, nom, raison_d_etre, cle, epuisee, fournee_en_cours, films.map { it.versUi() })
private fun PodiumMarcheVoyage.versUi() = PodiumMarcheUi(place, tmdb_id, programme_id, title, cover_url)
private fun MaturiteVoyage.versUi() = MaturiteUi(mure, motif)
private fun TicketAnneeVoyage.versUi() = TicketAnneeUi(annee, utilise = utilise_le != null)
private fun ParagrapheVoyage.versUi() = ParagrapheUi(id, tmdb_id, programme_id, titre, texte, ecrit_le, film.title, film.cover_url)
private fun DemandeSalleVoyage.versUi() = DemandeSalleUi(id, demande, statut, motif)
private fun PisteVoyage.versUi() = PisteUi(nom, raison)
private fun ProgressionVoyage.versUi() = ProgressionUi(essentiels_vus, essentiels_total, salles_completes, salles_autres)
private fun SeanceBobineVoyage.versUi() = SeanceBobineUi(tmdb_id, title)
private fun SeanceFilmVoyage.versUi() = SeanceFilmUi(film_id, tmdb_id, title, cover_url, salle, etat, plex_url, bobine?.versUi())
private fun SeanceVoyage.versUi() = SeanceUi(id, rang, statut, composee_le, anecdote, long.versUi(), court?.versUi())
private fun CarnetAnneeVoyage.versUi() = CarnetUi(fabrique_le, pages)

/** Toujours trois marches, une entrée nulle pour chacune que le back ne sert pas (encore vide, ou réponse plus courte). */
private fun List<PodiumMarcheVoyage?>.versPodiumUi(): List<PodiumMarcheUi?> = (0..2).map { i -> getOrNull(i)?.versUi() }

/** Construit l'état initial depuis le fragment déjà chargé par `FriseViewModel` — fonction pure, testée en JVM. */
fun anneeUiInitiale(annee: Int, snapshot: AnneeVoyage?): AnneeUi = AnneeUi(
    annee = annee,
    statutVoyage = statutAnneeVoyage(snapshot?.statut),
    profondeur = snapshot?.profondeur ?: 0,
)

class AnneeViewModel(
    private val api: JournalApi,
    private val annee: Int,
    snapshot: AnneeVoyage?,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(anneeUiInitiale(annee, snapshot))
    val ui: StateFlow<AnneeUi> = _ui

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var pollJob: Job? = null
    private val salleJobs = mutableMapOf<String, Job>()
    private val chroniqueJobs = mutableMapOf<Pair<Int?, String?>, Job>()
    private var salleDemandeJob: Job? = null

    /**
     * Relance la relecture de l'année (spec du 19 septembre 2026, §3 : « jamais un écran muet »).
     * Appelée par `AnneeScreen` à chaque entrée — l'instance de `ViewModel`, elle, survit à la
     * sortie de l'écran, et son compteur d'essais avec elle : sans cette remise à zéro, une page
     * rouverte après un abandon resterait vide pour toujours.
     *
     * Rien à relire sur une année déjà prête. Une année **verrouillée**, elle, se relit à chaque
     * entrée : un ticket a pu être utilisé depuis (21 septembre 2026 : 1896 restait sur
     * « Prochainement » après le ticket, jusqu'au redémarrage de l'appli).
     */
    fun relire() {
        if (_ui.value.etat == EtatAnnee.PRETE) return
        _ui.update { it.copy(essais = 0) }
        charger()
    }

    private fun charger() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val reponse = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                appliquer(reponse)
                if (_ui.value.etat != EtatAnnee.EN_PREPARATION) return@launch
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private fun appliquer(reponse: AnneeVoyageDetailResponse) {
        // La forme « verrouillée » est terminale, distincte de « en préparation » : la confondre
        // avec `etatChroniqueSuivant` (qui ne connaît que prête/en attente) ferait sonder pour
        // rien une année qui ne s'ouvrira jamais toute seule (spec du 19 septembre 2026, §5).
        if (reponse.configure && reponse.statut == "verrouillee") {
            _ui.update {
                it.copy(
                    statutVoyage = StatutAnneeVoyage.VERROUILLEE,
                    etat = EtatAnnee.VERROUILLEE,
                    profondeur = reponse.profondeur ?: it.profondeur,
                )
            }
            return
        }
        val (etatChronique, essais) = etatChroniqueSuivant(
            reponse.configure,
            reponse.statut,
            _ui.value.essais,
            plafond = CHRONIQUE_ANNEE_ESSAIS_MAX,
        )
        val etat = when (etatChronique) {
            EtatChronique.PRETE -> EtatAnnee.PRETE
            EtatChronique.EN_PREPARATION -> EtatAnnee.EN_PREPARATION
            EtatChronique.ABANDON -> EtatAnnee.ABANDON
            EtatChronique.NON_CONFIGURE -> EtatAnnee.NON_CONFIGURE
        }
        _ui.update {
            it.copy(
                etat = etat,
                essais = essais,
                profondeur = reponse.profondeur ?: it.profondeur,
                ouverture = reponse.ouverture ?: it.ouverture,
                faits = reponse.faits.ifEmpty { it.faits },
                salles = if (etat == EtatAnnee.PRETE) reponse.salles.sortedBy { s -> s.rang }.map { s -> s.versUi() } else it.salles,
                podium = if (etat == EtatAnnee.PRETE) reponse.podium.versPodiumUi() else it.podium,
                maturite = if (etat == EtatAnnee.PRETE) reponse.maturite?.versUi() else it.maturite,
                ticket = if (etat == EtatAnnee.PRETE) reponse.ticket?.versUi() else it.ticket,
                paragraphes = if (etat == EtatAnnee.PRETE) reponse.paragraphes.map { p -> p.versUi() } else it.paragraphes,
                paragraphesEnCours = if (etat == EtatAnnee.PRETE) {
                    reponse.paragraphes_en_cours.map { p -> p.tmdb_id to p.programme_id }.toSet()
                } else {
                    it.paragraphesEnCours
                },
                recompense = if (etat == EtatAnnee.PRETE) recompenseDe(reponse.recompense) else it.recompense,
                progression = if (etat == EtatAnnee.PRETE) reponse.progression?.versUi() else it.progression,
                seances = if (etat == EtatAnnee.PRETE) reponse.seances.sortedBy { s -> s.rang }.map { s -> s.versUi() } else it.seances,
                seanceEnCours = if (etat == EtatAnnee.PRETE) reponse.seance_en_cours else it.seanceEnCours,
                carnet = if (etat == EtatAnnee.PRETE) reponse.carnet?.versUi() else it.carnet,
                carnetEnCours = if (etat == EtatAnnee.PRETE) reponse.carnet_en_cours else it.carnetEnCours,
                pistes = if (etat == EtatAnnee.PRETE) reponse.pistes.map { p -> p.versUi() } else it.pistes,
            )
        }
        if (etat == EtatAnnee.PRETE) appliquerDemandeSalle(reponse.demande_salle)
    }

    /**
     * Met à jour `ui.demandeSalle` et marque vue une demande refusée, une seule fois par
     * identifiant (décision 3 du brief du 21 septembre 2026, « la chronique et les salles ») : dès
     * son premier affichage, jamais deux fois pour la même demande quel que soit le nombre de
     * relectures qui la revoient encore refusée.
     */
    private var demandeSalleVueEnvoyeePour: String? = null

    private fun appliquerDemandeSalle(demande: DemandeSalleVoyage?) {
        _ui.update { it.copy(demandeSalle = demande?.versUi()) }
        if (demande != null && demande.statut == "refusee" && demandeSalleVueEnvoyeePour != demande.id) {
            demandeSalleVueEnvoyeePour = demande.id
            viewModelScope.launch {
                try {
                    api.voyageDemandeSalleVue(demande.id)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                }
            }
        }
    }

    /** « Lire la suite » sur le cartouche — l'ouverture, repliée à trois lignes par défaut, se déplie. */
    fun deplierOuverture() {
        _ui.update { it.copy(ouvertureDepliee = true) }
    }

    /**
     * « En voir plus » sur une salle : enfile une fournée (`202`, ou une déjà en cours) — marquée
     * tout de suite « se remplit » pour ne pas laisser le bouton muet le temps du premier aller-
     * retour — ou constate qu'elle est déjà épuisée (`200`) sans rien enfiler. La salle se relit
     * ensuite jusqu'à ce que `fournee_en_cours` retombe (nouveaux films, ou « Salle épuisée »),
     * abandon au plafond (`etatFourneeSuivant`, même intervalle que le carton d'un film).
     */
    fun voirPlus(salleId: String) {
        if (salleJobs[salleId]?.isActive == true) return
        salleJobs[salleId] = viewModelScope.launch {
            val reponse = try {
                api.voyageSallePlus(salleId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            if (reponse.statut == "epuisee") {
                mettreAJourSalle(salleId) { it.copy(epuisee = true, fourneeEnCours = false) }
                return@launch
            }
            mettreAJourSalle(salleId) { it.copy(fourneeEnCours = true) }

            var essais = 0
            while (true) {
                delay(FOURNEE_POLL_INTERVAL_MS)
                val detail = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                if (detail.configure && detail.statut == "prete") {
                    _ui.update { it.copy(salles = detail.salles.sortedBy { s -> s.rang }.map { s -> s.versUi() }) }
                }
                val fourneeEnCours = _ui.value.salles.firstOrNull { it.id == salleId }?.fourneeEnCours ?: false
                val (etat, prochainEssai) = etatFourneeSuivant(fourneeEnCours, essais)
                essais = prochainEssai
                if (etat != EtatFournee.EN_COURS) return@launch
            }
        }
    }

    /**
     * « Ajouter à la chronique » (décision 1 du brief du 21 septembre 2026, « la chronique et les
     * salles ») : sur un film vu, ou un programme entièrement vu — `tmdbId` **ou** `programmeId`,
     * jamais les deux. `200 ecrit` affiche directement le paragraphe (déjà existant, jamais
     * régénéré) ; `202 en_preparation` marque tout de suite le bouton « Le chroniqueur écrit… »
     * (optimiste, jumeau de `voirPlus`) puis relit l'année toutes les cinq secondes jusqu'à ce que
     * `paragraphes` porte ce film, abandon au plafond (`etatParagrapheSuivant`).
     */
    fun ajouterChronique(tmdbId: Int?, programmeId: String?) {
        val cle = tmdbId to programmeId
        if (chroniqueJobs[cle]?.isActive == true) return
        chroniqueJobs[cle] = viewModelScope.launch {
            val reponse = try {
                api.voyageChronique(annee, ChroniqueBody(tmdbId, programmeId))
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            val paragraphe = reponse.paragraphe
            if (reponse.statut == "ecrit" && paragraphe != null) {
                _ui.update {
                    it.copy(
                        paragraphes = it.paragraphes.filterNot { p -> p.id == paragraphe.id } + paragraphe.versUi(),
                        paragraphesEnCours = it.paragraphesEnCours - cle,
                    )
                }
                return@launch
            }
            _ui.update { it.copy(paragraphesEnCours = it.paragraphesEnCours + cle) }

            var essais = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val detail = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                if (detail.configure && detail.statut == "prete") {
                    _ui.update { it.copy(paragraphes = detail.paragraphes.map { p -> p.versUi() }) }
                }
                val trouve = _ui.value.paragraphes.any { (it.tmdbId to it.programmeId) == cle }
                val (etatSuivant, prochainEssai) = etatParagrapheSuivant(trouve, essais)
                essais = prochainEssai
                if (etatSuivant != EtatChronique.EN_PREPARATION) {
                    _ui.update { it.copy(paragraphesEnCours = it.paragraphesEnCours - cle) }
                    return@launch
                }
            }
        }
    }

    /**
     * « Ouvrir une nouvelle salle » (décision 3 du brief du 21 septembre 2026, décision 1-2 du
     * brief du 22 septembre 2026, « les pistes ») : enfile la demande (toujours `202`), retire tout
     * de suite `piste` de la liste des pistes si elle en portait une (`pistesApresUsage`, décision
     * 2 — le back l'a retirée aussi) et marque l'étagère fantôme (optimiste, jumeau de `voirPlus`),
     * puis relit l'année toutes les cinq secondes jusqu'à ce que `demande_salle` ne soit plus
     * `en_cours` (`creee` : la salle apparaît dans `salles`, le bouton revient ; `refusee` : le
     * motif s'affiche, marqué vu par `appliquerDemandeSalle`), abandon au plafond de l'année sinon
     * (`etatFourneeSuivant`, même plafond que la chronique — `CHRONIQUE_ANNEE_ESSAIS_MAX`).
     */
    fun ouvrirNouvelleSalle(demande: String, piste: String? = null) {
        if (salleDemandeJob?.isActive == true) return
        salleDemandeJob = viewModelScope.launch {
            val reponse = try {
                api.voyageDemanderSalle(annee, demande, piste)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            _ui.update {
                it.copy(
                    demandeSalle = DemandeSalleUi(reponse.demande_id, demande, "en_cours", null),
                    pistes = pistesApresUsage(it.pistes, piste),
                )
            }

            var essais = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val detail = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                if (detail.configure && detail.statut == "prete") {
                    _ui.update { it.copy(salles = detail.salles.sortedBy { s -> s.rang }.map { s -> s.versUi() }) }
                    appliquerDemandeSalle(detail.demande_salle)
                }
                val enCours = _ui.value.demandeSalle?.statut == "en_cours"
                val (etat, prochainEssai) = etatFourneeSuivant(enCours, essais, plafond = CHRONIQUE_ANNEE_ESSAIS_MAX)
                essais = prochainEssai
                if (etat != EtatFournee.EN_COURS) return@launch
            }
        }
    }

    /** Le bouton « Demander sur Sir » de la fiche d'un film ou d'une bobine à demander. */
    fun demander(tmdbId: Int) {
        viewModelScope.launch {
            try {
                api.demanderVoyage(tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            mettreAJourFilm(tmdbId) { it.copy(etat = "demande") }
            // Un film demandé depuis la carte de soirée porte le même `tmdb_id` dans `salles`
            // (décision 2 du brief du 21 septembre 2026, « la séance ») : la carte se met à jour
            // sans attendre `relireApresSeance`.
            mettreAJourSeanceFilm(tmdbId) { it.copy(etat = "demande") }
        }
    }

    /** « Introuvable » sur la fiche d'un film : `PUT /me/introuvables/{tmdbId}`, existant. */
    fun marquerIntrouvable(tmdbId: Int) = basculerIntrouvable(tmdbId, versIntrouvable = true)

    /** Et son inverse : `DELETE /me/introuvables/{tmdbId}`. */
    fun retirerIntrouvable(tmdbId: Int) = basculerIntrouvable(tmdbId, versIntrouvable = false)

    private fun basculerIntrouvable(tmdbId: Int, versIntrouvable: Boolean) {
        viewModelScope.launch {
            try {
                if (versIntrouvable) api.marquerIntrouvable(tmdbId) else api.retirerIntrouvable(tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend("Impossible pour l’instant")
                return@launch
            }
            // Sans mémoire de l'état d'avant (contrairement aux essentiels du 16 septembre 2026) :
            // `GET /me/voyage/annees/{annee}` recalcule `etat` à chaque lecture, un « remettre à
            // voir » retombe donc sur ce que le back sait déjà (sur le Plex, demandé, à demander).
            // On relit les salles plutôt que de deviner localement lequel des trois c'est.
            relireApresEnregistrement()
        }
    }

    /**
     * Relit les salles de l'année, sans passer par `etat` ni `essais` (jumelle de `relireApresPodium`
     * et `relireApresSeance`) — à la différence de `relire()`, elle ne rend jamais la main tout de
     * suite sur une année déjà `PRETE` : c'est justement le cas qu'elle sert. Appelée ici après
     * « Introuvable »/« Le remettre à voir », et par `Root.kt` (`Screen.Annee`, `Screen.FicheVoyage`)
     * au retour du formulaire, quand `FriseViewModel.ui.enregistrements` a changé depuis le dernier
     * chargement (22 septembre 2026, correctif « la fiche du Voyage se relit après un
     * enregistrement ») : sans elle, un film tout juste noté restait affiché « à voir » dans sa
     * salle jusqu'à la fermeture et la réouverture de l'application.
     */
    fun relireApresEnregistrement() {
        viewModelScope.launch {
            val reponse = try {
                api.voyageAnnee(annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }
            if (reponse.configure && reponse.statut == "prete") {
                _ui.update { it.copy(salles = reponse.salles.sortedBy { s -> s.rang }.map { s -> s.versUi() }) }
            }
        }
    }

    /**
     * « Utiliser » sur la ligne du bas de la fiche (décision 4 du brief du 21 septembre 2026,
     * « le ticket »), à la place du bouton provisoire « Année suivante » qu'il remplace : encaisse
     * le ticket vers l'année suivante — `ui.ticket.annee`, jamais `annee + 1` recalculé ici, pour
     * rester juste si le back a défini le ticket autrement un jour. Sans ticket en attente, ne
     * fait rien (le bouton n'est de toute façon rendu qu'à cette condition).
     */
    fun utiliserTicket(onEcrit: () -> Unit) {
        val ticket = _ui.value.ticket ?: return
        if (ticket.utilise) return
        viewModelScope.launch {
            try {
                api.utiliserTicket(ticket.annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            // Optimiste, comme l'ancien bouton provisoire : cette année n'est plus « en cours »
            // une fois le ticket encaissé, sans attendre un rechargement.
            _ui.update { it.copy(statutVoyage = StatutAnneeVoyage.OUVERTE, ticket = it.ticket?.copy(utilise = true)) }
            onEcrit()
        }
    }

    /**
     * Pose ou déplace un candidat sur une marche (décision 2 et 3 du brief du 21 septembre 2026,
     * « le podium »). Après l'écriture, l'année se relit tout entière — `PUT` peut avoir vidé une
     * autre marche (le candidat s'y trouvait déjà) et une simple mise à jour locale de `place` ne le
     * verrait pas — puis `onEcrit` est appelé pour que l'appelant fasse suivre `frise.refresh()`,
     * seule source de l'affiche de la carte.
     */
    fun poserPodium(place: Int, candidat: CandidatPodium, onEcrit: () -> Unit) {
        viewModelScope.launch {
            try {
                api.poserPodium(annee, place, corpsPodium(candidat))
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            relireApresPodium(onEcrit)
        }
    }

    /** Vide une marche (appui long sur une marche occupée, ou « Retirer du podium » de sa feuille). Idempotent côté back. */
    fun retirerPodium(place: Int, onEcrit: () -> Unit) {
        viewModelScope.launch {
            try {
                api.retirerPodium(annee, place)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            relireApresPodium(onEcrit)
        }
    }

    private suspend fun relireApresPodium(onEcrit: () -> Unit) {
        val reponse = try {
            api.voyageAnnee(annee)
        } catch (e: ApiError) {
            if (e.isUnauthenticated) onUnauthenticated()
            return
        }
        if (reponse.configure && reponse.statut == "prete") {
            _ui.update { it.copy(podium = reponse.podium.versPodiumUi()) }
        }
        onEcrit()
    }

    private fun mettreAJourFilm(tmdbId: Int, transforme: (FilmSalleUi) -> FilmSalleUi) {
        _ui.update { ui ->
            ui.copy(
                salles = ui.salles.map { salle ->
                    salle.copy(films = salle.films.map { film -> if (film.tmdbId == tmdbId) transforme(film) else film })
                },
            )
        }
    }

    private fun mettreAJourSalle(salleId: String, transforme: (SalleUi) -> SalleUi) {
        _ui.update { ui -> ui.copy(salles = ui.salles.map { salle -> if (salle.id == salleId) transforme(salle) else salle }) }
    }

    private fun mettreAJourSeanceFilm(tmdbId: Int, transforme: (SeanceFilmUi) -> SeanceFilmUi) {
        _ui.update { ui ->
            ui.copy(
                seances = ui.seances.map { s ->
                    s.copy(
                        long = if (s.long.tmdbId == tmdbId) transforme(s.long) else s.long,
                        court = s.court?.let { c -> if (c.tmdbId == tmdbId) transforme(c) else c },
                    )
                },
            )
        }
    }

    // --- La séance (brief du 21 septembre 2026, « la séance »). ---

    private var seanceJob: Job? = null

    /**
     * « Composer une séance » (décision 1) : enfile la composition (toujours `202`), marque tout de
     * suite `seanceEnCours` (optimiste, jumeau de `voirPlus`), puis relit l'année toutes les cinq
     * secondes jusqu'à ce que `seance_en_cours` retombe, abandon au plafond de l'année
     * (`etatSeanceSuivant`, qui réutilise `etatChroniqueSuivant`, `CHRONIQUE_ANNEE_ESSAIS_MAX`).
     * Une `409` ou une `400` envoie le message du back au bandeau, sans rien changer d'autre.
     *
     * Une composition qui s'arrête sans avoir rien produit de neuf ne doit jamais revenir muette au
     * bouton (décision du propriétaire du 21 septembre 2026, « une composition abandonnée le dit ») :
     * `seancesAvant` capture le compte au tout début, comparé à la fin par `messageEchecComposition`
     * — nul dès qu'une séance de plus est apparue, un message distinct sinon selon que le plafond
     * est atteint ou que `seance_en_cours` est retombé tout seul. `seanceEnCours` est alors forcé à
     * faux : sans ça, un abandon au plafond (où le back n'a jamais dit `seance_en_cours: false`)
     * laisserait la carte d'attente affichée pour toujours.
     */
    fun composerSeance() {
        if (seanceJob?.isActive == true) return
        val seancesAvant = _ui.value.seances.size
        seanceJob = viewModelScope.launch {
            try {
                api.voyageComposerSeance(annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            _ui.update { it.copy(seanceEnCours = true) }

            var essais = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val detail = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                if (detail.configure && detail.statut == "prete") {
                    _ui.update {
                        it.copy(
                            seances = detail.seances.sortedBy { s -> s.rang }.map { s -> s.versUi() },
                            seanceEnCours = detail.seance_en_cours,
                        )
                    }
                }
                val (etatSuivant, prochainEssai) = etatSeanceSuivant(_ui.value.seanceEnCours, essais)
                essais = prochainEssai
                if (etatSuivant != EtatChronique.EN_PREPARATION) {
                    messageEchecComposition(etatSuivant, seancesAvant, _ui.value.seances.size)?.let { message ->
                        _ui.update { it.copy(seanceEnCours = false) }
                        _messages.trySend(message)
                    }
                    return@launch
                }
            }
        }
    }

    /** Après « Prendre », « Ignorer » ou « Remplacer » (décision 2-3) : l'année se relit tout entière — jumeau de `relireApresPodium`. */
    private suspend fun relireApresSeance() {
        val reponse = try {
            api.voyageAnnee(annee)
        } catch (e: ApiError) {
            if (e.isUnauthenticated) onUnauthenticated()
            return
        }
        if (reponse.configure && reponse.statut == "prete") {
            _ui.update {
                it.copy(
                    seances = reponse.seances.sortedBy { s -> s.rang }.map { s -> s.versUi() },
                    seanceEnCours = reponse.seance_en_cours,
                )
            }
        }
    }

    /** « Prendre » (décision 2) : `onEcrit` fait suivre `frise.refresh()`, la ligne « Ce soir » de l'accueil en dépend. */
    fun prendreSeance(id: String, onEcrit: () -> Unit) {
        viewModelScope.launch {
            try {
                api.voyagePrendreSeance(id)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            relireApresSeance()
            onEcrit()
        }
    }

    /** « Ignorer » (décision 2) : la carte disparaît (`etatZoneSeance` retombe à `RIEN`), sans toucher à l'accueil. */
    fun ignorerSeance(id: String) {
        viewModelScope.launch {
            try {
                api.voyageIgnorerSeance(id)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            relireApresSeance()
        }
    }

    /** « Autre long » / « Autre court » (décision 3) : le corps est construit localement (`corpsRemplacementSeance`), sans appel de plus avant celui-ci. */
    fun remplacerSeance(id: String, corps: SeanceRemplacerBody) {
        viewModelScope.launch {
            try {
                api.voyageRemplacerSeance(id, corps)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            relireApresSeance()
        }
    }

    // --- Les pistes de salles (brief du 22 septembre 2026, « les pistes »). ---

    private var pistesJob: Job? = null

    /**
     * « D'autres pistes » (décision 3 du brief) : appel **synchrone** au chroniqueur — pas
     * d'enfilement ni de relecture, contrairement au reste du Voyage (`fabriquerCarnet`,
     * `composerSeance`…). Marque `pistesEnCours` tout de suite, le temps de l'appel ; les trois
     * pistes rendues remplacent la liste précédente. Un échec envoie le message du back au bandeau
     * et rend le bouton (`pistesEnCours` retombe), sans toucher aux pistes déjà affichées.
     */
    fun demanderPistes() {
        if (pistesJob?.isActive == true) return
        pistesJob = viewModelScope.launch {
            _ui.update { it.copy(pistesEnCours = true) }
            val reponse = try {
                api.voyagePistes(annee)
            } catch (e: ApiError) {
                _ui.update { it.copy(pistesEnCours = false) }
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            _ui.update { it.copy(pistes = reponse.pistes.map { p -> p.versUi() }, pistesEnCours = false) }
        }
    }

    // --- Le carnet (brief du 22 septembre 2026, « le carnet »). ---

    private var carnetJob: Job? = null

    /**
     * « Faire le carnet »/« Refaire le carnet » (décision 2) : lance ou relance la fabrication,
     * marque tout de suite `carnetEnCours` (optimiste, jumeau de `voirPlus`/`composerSeance`), puis
     * relit l'année toutes les cinq secondes jusqu'à ce que `carnet_en_cours` retombe, abandon au
     * plafond de l'année (`etatFourneeSuivant`, même plafond que la chronique et les salles —
     * `CHRONIQUE_ANNEE_ESSAIS_MAX`). Une erreur (dont une `409`, une fabrication déjà en cours)
     * envoie le message du back au bandeau, sans marquer en cours.
     */
    fun fabriquerCarnet() {
        if (carnetJob?.isActive == true) return
        carnetJob = viewModelScope.launch {
            try {
                api.voyageFabriquerCarnet(annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            _ui.update { it.copy(carnetEnCours = true) }

            var essais = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val detail = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                if (detail.configure && detail.statut == "prete") {
                    _ui.update { it.copy(carnet = detail.carnet?.versUi(), carnetEnCours = detail.carnet_en_cours) }
                }
                val (etat, prochainEssai) = etatFourneeSuivant(_ui.value.carnetEnCours, essais, plafond = CHRONIQUE_ANNEE_ESSAIS_MAX)
                essais = prochainEssai
                if (etat != EtatFournee.EN_COURS) return@launch
            }
        }
    }

    /**
     * Un tap sur « Fabriqué le… » (décision 4) : les octets seuls — c'est l'appelant (`AnneeScreen`,
     * `ouvrirCarnet`) qui les écrit dans `cacheDir` et ouvre l'intention, avec le `Context` qu'un
     * `ViewModel` ne doit pas tenir. `null` après une session expirée, déjà traitée ici comme
     * partout ailleurs.
     */
    suspend fun telechargerCarnetPdf(): ByteArray? = try {
        api.telechargerCarnetPdf(annee)
    } catch (e: ApiError) {
        if (e.isUnauthenticated) {
            onUnauthenticated()
            null
        } else {
            throw e
        }
    }

    companion object {
        /** La chronique d'une année (§3 de la spec du 19 septembre 2026) : cinq secondes l'essai. */
        const val POLL_INTERVAL_MS = 5_000L

        /** Une fournée (« En voir plus ») : même intervalle que le carton d'un film, trois secondes — un appel du même ordre de grandeur. */
        const val FOURNEE_POLL_INTERVAL_MS = 3_000L
    }
}
