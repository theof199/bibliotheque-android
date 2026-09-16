package fr.mediatheque.journal.ui.frise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.AnneeVoyage
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
 * Le Voyage, page d'année (brief du 16 septembre 2026, phase 1) : le cartouche (récit, relu tant
 * que la chronique est en préparation) et « Les essentiels » (l'état de chacun pour le membre de
 * la session, marquer introuvable, demander sur Seerr).
 *
 * L'essentiel de l'état initial vient déjà de `GET /me/voyage` (`FriseViewModel`, chargé une fois
 * pour tout le calendrier) : `AnneeVoyage` passé au constructeur, via `Screen.Annee.voyage`. Cette
 * page ne réappelle donc jamais `/me/voyage` — seule sa propre chronique (`GET
 * /reference/chroniques/annees/{annee}`) se relit, et seulement si l'année n'est pas verrouillée.
 */

data class EssentielAnneeUi(
    val rang: Int,
    val tmdbId: Int,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val realisateur: String,
    val pourquoi: String,
    /** `"vu"` · `"sur_le_plex"` · `"a_trouver"` · `"introuvable"`. */
    val etat: String,
    /** L'état à reprendre si on retire la marque « introuvable » — nul si l'essentiel était déjà marqué au chargement. */
    val etatAvantIntrouvable: String?,
    val note: Int?,
    /** Vrai après un `POST /me/voyage/demander/{tmdbId}` réussi — pastille « demandé », locale : le back ne le redit pas. */
    val demande: Boolean = false,
)

data class AnneeUi(
    val annee: Int,
    val statutVoyage: StatutAnneeVoyage? = null,
    val vus: Int = 0,
    val essentielsTotal: Int? = null,
    val essentielsFaits: Int? = null,
    val essentiels: List<EssentielAnneeUi> = emptyList(),
    val recit: String? = null,
    val faits: List<String> = emptyList(),
    val etatChronique: EtatChronique = EtatChronique.NON_CONFIGURE,
    val essaisChronique: Int = 0,
    /** Les affiches des essentiels d'une année verrouillée, pour le carton « Prochainement » — vide tant que le back ne les sert pas. */
    val apercu: List<String> = emptyList(),
) {
    /** La récompense de festival de l'année (brief du 16 septembre 2026, phase 2) — nulle hors d'une année faite. */
    val recompenseObtenue: Recompense?
        get() = recompenseFaite(statutVoyage, essentielsTotal, essentielsFaits)
}

/** Construit l'état initial depuis le fragment déjà chargé par `FriseViewModel` — fonction pure, testée en JVM. */
fun anneeUiInitiale(annee: Int, snapshot: AnneeVoyage?): AnneeUi = AnneeUi(
    annee = annee,
    statutVoyage = statutAnneeVoyage(snapshot?.statut),
    vus = snapshot?.vus ?: 0,
    essentielsTotal = snapshot?.essentiels_total,
    essentielsFaits = snapshot?.essentiels_faits,
    essentiels = (snapshot?.essentiels ?: emptyList()).map { essentiel ->
        EssentielAnneeUi(
            rang = essentiel.rang,
            tmdbId = essentiel.tmdb_id,
            title = essentiel.title,
            year = essentiel.year,
            coverUrl = essentiel.cover_url,
            realisateur = essentiel.realisateur,
            pourquoi = essentiel.pourquoi,
            etat = essentiel.etat,
            etatAvantIntrouvable = if (essentiel.etat == "introuvable") null else essentiel.etat,
            note = essentiel.note,
        )
    },
    apercu = (snapshot?.essentiels_apercu ?: emptyList()).mapNotNull { it.cover_url },
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

    /**
     * Relance la relecture de la chronique (brief du 16 septembre 2026, phase 2 : « quand on
     * revient sur la page, la relecture repart »). Appelée par `AnneeScreen` à chaque entrée —
     * l'instance de `ViewModel`, elle, survit à la sortie de l'écran, et son compteur d'essais
     * avec elle : sans cette remise à zéro, une page rouverte après un abandon resterait vide
     * pour toujours.
     *
     * Pas de récit pour une année verrouillée (le brief de la phase 1 : « pas de génération
     * d'avance ») — rien à relire. Et rien à refaire non plus sur une chronique déjà prête.
     */
    fun relire() {
        val statut = _ui.value.statutVoyage
        if (statut != StatutAnneeVoyage.OUVERTE && statut != StatutAnneeVoyage.FAITE) return
        if (_ui.value.etatChronique == EtatChronique.PRETE) return
        _ui.update { it.copy(essaisChronique = 0) }
        chargerChronique()
    }

    private fun chargerChronique() {
        pollJob?.cancel()
        pollJob = viewModelScope.launch {
            while (true) {
                val reponse = try {
                    api.chroniqueAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                val (etat, essais) = etatChroniqueSuivant(
                    reponse.configure,
                    reponse.statut,
                    _ui.value.essaisChronique,
                    plafond = CHRONIQUE_ANNEE_ESSAIS_MAX,
                )
                _ui.update { it.copy(etatChronique = etat, essaisChronique = essais, recit = reponse.recit, faits = reponse.faits) }
                if (etat != EtatChronique.EN_PREPARATION) return@launch
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    /** Appui long sur un essentiel non vu (brief) : `PUT /me/introuvables/{tmdbId}`, existant. */
    fun marquerIntrouvable(tmdbId: Int) = basculerIntrouvable(tmdbId, versIntrouvable = true)

    /** Et l'inverse : `DELETE /me/introuvables/{tmdbId}`. */
    fun retirerIntrouvable(tmdbId: Int) = basculerIntrouvable(tmdbId, versIntrouvable = false)

    private fun basculerIntrouvable(tmdbId: Int, versIntrouvable: Boolean) {
        viewModelScope.launch {
            try {
                if (versIntrouvable) api.marquerIntrouvable(tmdbId) else api.retirerIntrouvable(tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend("Impossible pour l’instant")
                return@launch
            }
            _ui.update { ui ->
                ui.copy(
                    essentiels = ui.essentiels.map { essentiel ->
                        if (essentiel.tmdbId != tmdbId) essentiel
                        else essentiel.copy(etat = if (versIntrouvable) "introuvable" else (essentiel.etatAvantIntrouvable ?: "a_trouver"))
                    },
                )
            }
        }
    }

    /** Le bouton « Demander sur Sir » d'un essentiel à trouver : succès → pastille « demandé », erreur → bandeau. */
    fun demander(tmdbId: Int) {
        viewModelScope.launch {
            try {
                api.demanderVoyage(tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            _ui.update { ui ->
                ui.copy(essentiels = ui.essentiels.map { if (it.tmdbId == tmdbId) it.copy(demande = true) else it })
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
