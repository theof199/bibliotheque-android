package fr.mediatheque.journal.ui.frise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.ChroniqueBody
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * « Ajouter à la chronique » depuis l'écran de correction du journal (décision 1 du brief du 21
 * septembre 2026, « la chronique et les salles ») : même appel, même règle « une fois » que sur la
 * fiche d'un film du Voyage (`AnneeViewModel.ajouterChronique`) — jumeau plus léger, indépendant de
 * toute salle : `Screen.Edit` ne charge jamais l'année entière, seulement `voyageChronique` puis, sur
 * `202 en_preparation`, une relecture de `voyageAnnee` toutes les cinq secondes jusqu'à ce que le
 * paragraphe soit là, abandon au plafond (`etatParagrapheSuivant`, `CHRONIQUE_ANNEE_ESSAIS_MAX`).
 *
 * `tmdbId` seul : une correction du journal ne porte jamais de `programme_id`, réservé aux salles.
 */
data class ChroniqueUi(val etat: EtatBoutonChronique = EtatBoutonChronique.AJOUTER, val texte: String? = null)

class ChroniqueViewModel(
    private val api: JournalApi,
    private val annee: Int,
    private val tmdbId: Int,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(ChroniqueUi())
    val ui: StateFlow<ChroniqueUi> = _ui

    private var job: Job? = null

    fun ajouter() {
        if (job?.isActive == true) return
        job = viewModelScope.launch {
            val reponse = try {
                api.voyageChronique(annee, ChroniqueBody(tmdb_id = tmdbId))
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }
            val paragraphe = reponse.paragraphe
            if (reponse.statut == "ecrit" && paragraphe != null) {
                _ui.update { ChroniqueUi(EtatBoutonChronique.DANS_LA_CHRONIQUE, paragraphe.texte) }
                return@launch
            }
            _ui.update { it.copy(etat = EtatBoutonChronique.ECRIT_EN_COURS) }

            var essais = 0
            while (true) {
                delay(POLL_INTERVAL_MS)
                val detail = try {
                    api.voyageAnnee(annee)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                val trouve = if (detail.configure && detail.statut == "prete") {
                    detail.paragraphes.firstOrNull { it.tmdb_id == tmdbId }
                } else {
                    null
                }
                if (trouve != null) {
                    _ui.update { ChroniqueUi(EtatBoutonChronique.DANS_LA_CHRONIQUE, trouve.texte) }
                    return@launch
                }
                val (etatSuivant, prochainEssai) = etatParagrapheSuivant(paragrapheTrouve = false, essaisPrecedents = essais)
                essais = prochainEssai
                if (etatSuivant != EtatChronique.EN_PREPARATION) {
                    _ui.update { it.copy(etat = EtatBoutonChronique.AJOUTER) }
                    return@launch
                }
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 5_000L
    }
}
