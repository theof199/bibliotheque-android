package fr.mediatheque.journal.ui.realisateur

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.RealisateurPageResponse
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Ce qu'on sait de la page, une fois `charger` lancé. */
sealed interface EtatPageRealisateur {
    data object EnAttente : EtatPageRealisateur
    data class Pret(val page: RealisateurPageResponse) : EtatPageRealisateur
    data object Indisponible : EtatPageRealisateur
}

data class RealisateurUi(val etat: EtatPageRealisateur = EtatPageRealisateur.EnAttente)

/**
 * La page d'un réalisateur (brief du 21 septembre 2026, « la page réalisateur ») : sa fiche et sa
 * filmographie complète, `GET /me/realisateurs/{tmdbId}/page` — la même, suivi ou non (décision 1
 * du brief). Suivre, retirer, demander sur Sir et marquer/démarquer introuvable partagent un seul
 * chemin : l'appel, puis un rechargement complet — le back recalcule `suivi` et l'état de chaque
 * film, une mise à jour locale devinerait ce qu'il sait déjà (jumeau de `basculerIntrouvable`,
 * `AnneeViewModel.kt`, `ui/frise/`).
 *
 * Pas d'`init { charger() }` (jumeau de `SuivisViewModel`/`FriseViewModel`) : `Root.kt` déclenche
 * le premier chargement par un `LaunchedEffect(Unit)` à l'entrée sur l'écran.
 */
class RealisateurViewModel(
    private val tmdbId: Int,
    private val api: JournalApi,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {
    private val _ui = MutableStateFlow(RealisateurUi())
    val ui: StateFlow<RealisateurUi> = _ui

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun charger() {
        viewModelScope.launch {
            _ui.update { it.copy(etat = EtatPageRealisateur.EnAttente) }
            try {
                val page = api.pageRealisateur(tmdbId)
                _ui.update { it.copy(etat = EtatPageRealisateur.Pret(page)) }
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                _ui.update { it.copy(etat = EtatPageRealisateur.Indisponible) }
            }
        }
    }

    /** « Suivre » (décision 1 du brief) : le `POST`, puis la page se recharge — c'est ce rechargement qui fait passer le bouton à « Suivi ». */
    fun suivre() = ecrire { api.suivreRealisateur(tmdbId) }

    /** « Suivi », un tap le retire (décision 1) : le `DELETE`, puis la page se recharge. */
    fun retirer() = ecrire { api.retirerRealisateur(tmdbId) }

    /** « Demander sur Sir » sur un film de la fiche simple (décision 2 du brief). */
    fun demander(filmTmdbId: Int) = ecrire { api.demanderVoyage(filmTmdbId) }

    /** « Introuvable » sur la fiche simple, et l'étagère (décision 1 du brief). */
    fun marquerIntrouvable(filmTmdbId: Int) = ecrire { api.marquerIntrouvable(filmTmdbId) }

    /** Et son inverse. */
    fun retirerIntrouvable(filmTmdbId: Int) = ecrire { api.retirerIntrouvable(filmTmdbId) }

    private fun ecrire(appel: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                appel()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "Impossible pour l’instant")
                return@launch
            }
            charger()
        }
    }
}
