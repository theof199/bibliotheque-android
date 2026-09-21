package fr.mediatheque.journal.ui.frise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.AnneeVoyage
import fr.mediatheque.journal.api.dto.AnneeVoyageDetailResponse
import fr.mediatheque.journal.api.dto.BobineVoyage
import fr.mediatheque.journal.api.dto.FilmSalleVoyage
import fr.mediatheque.journal.api.dto.MaturiteVoyage
import fr.mediatheque.journal.api.dto.PodiumMarcheVoyage
import fr.mediatheque.journal.api.dto.ProgrammeVoyage
import fr.mediatheque.journal.api.dto.SalleVoyage
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
 * « le ticket ») : la chronique de l'année (ouverture, faits), ses salles, son podium et le ticket
 * qu'elle a pu gagner vers l'année suivante, chacun avec ses films et mon état sur chacun, depuis
 * `GET /me/voyage/annees/{annee}`.
 *
 * Remplace entièrement le modèle « essentiels » du 16 septembre 2026 — plus de frontière : une
 * année ouverte se creuse en salles, sans jamais être « finie ». Le podium (étape 2), lui, s'écrit
 * et se retire d'ici (`poserPodium`, `retirerPodium`) ; le ticket (étape 3, `utiliserTicket`) a
 * remplacé le bouton provisoire « Année suivante ». `Screen.FicheVoyage`
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
) {
    /** La récompense de l'année — nulle à cette étape, le back n'en sert aucune (`VoyageCarte.kt`). */
    val recompenseObtenue: Recompense? get() = null
}

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

    /**
     * Relance la relecture de l'année (spec du 19 septembre 2026, §3 : « jamais un écran muet »).
     * Appelée par `AnneeScreen` à chaque entrée — l'instance de `ViewModel`, elle, survit à la
     * sortie de l'écran, et son compteur d'essais avec elle : sans cette remise à zéro, une page
     * rouverte après un abandon resterait vide pour toujours.
     *
     * Rien à relire sur une année déjà prête, ni sur une année verrouillée qu'une visite ne peut
     * pas ouvrir (le back le refuse, `enfiler l'ouverture` n'étant tenté qu'une fois par année).
     */
    fun relire() {
        if (_ui.value.etat == EtatAnnee.PRETE || _ui.value.etat == EtatAnnee.VERROUILLEE) return
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
            )
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
            relireSalles()
        }
    }

    private fun relireSalles() {
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

    companion object {
        /** La chronique d'une année (§3 de la spec du 19 septembre 2026) : cinq secondes l'essai. */
        const val POLL_INTERVAL_MS = 5_000L

        /** Une fournée (« En voir plus ») : même intervalle que le carton d'un film, trois secondes — un appel du même ordre de grandeur. */
        const val FOURNEE_POLL_INTERVAL_MS = 3_000L
    }
}
