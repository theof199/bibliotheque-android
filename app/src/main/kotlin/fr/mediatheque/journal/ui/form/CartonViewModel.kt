package fr.mediatheque.journal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.ui.frise.EtatChronique
import fr.mediatheque.journal.ui.frise.etatChroniqueSuivant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le carton « Et pendant ce temps… » (brief du 16 septembre 2026, phase 1 « le moteur ») :
 * `GET /reference/chroniques/films/{tmdbId}`.
 *
 * Deux usages, un seul `ViewModel` :
 * - après un `create` réussi (`poll = true`) : relu toutes les trois secondes, dix fois au plus
 *   (`etatChroniqueSuivant`, `ui/frise/VoyageEtats.kt` — même règle que la chronique d'année, sur
 *   le même plafond `CHRONIQUE_ESSAIS_MAX`), puis abandon ;
 * - en bas de `Screen.Edit` (`poll = false`) : une seule lecture, la carte ne s'affiche que si
 *   elle est déjà prête — jamais de « le chroniqueur arrive… » sur un écran de correction.
 */
data class CartonUi(
    val etat: EtatChronique = EtatChronique.NON_CONFIGURE,
    val essais: Int = 0,
    val contexte: String? = null,
    val faits: List<String> = emptyList(),
)

class CartonViewModel(
    private val api: JournalApi,
    private val tmdbId: Int,
    private val poll: Boolean,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(CartonUi())
    val ui: StateFlow<CartonUi> = _ui

    init {
        viewModelScope.launch {
            while (true) {
                val reponse = try {
                    api.cartonFilm(tmdbId)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                val (etat, essais) = etatChroniqueSuivant(reponse.configure, reponse.statut, _ui.value.essais)
                _ui.update { it.copy(etat = etat, essais = essais, contexte = reponse.contexte, faits = reponse.faits) }
                if (!poll || etat != EtatChronique.EN_PREPARATION) return@launch
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
