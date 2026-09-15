package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.journalComplet
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

data class BilanUi(val journal: BilanJournal? = null)

/**
 * Charge le journal complet pour la carte « Bilan » du profil (brief du
 * 15 septembre 2026) — la seule donnée que la carte ne partage avec aucun
 * autre écran (les réalisateurs et les sagas suivis viennent, eux, du
 * `SuivisViewModel` partagé, déjà tiré par l'accueil et l'écran Suivis).
 *
 * `journal == null` tant que la réponse n'est pas là : « Chargement non
 * bloquant » (brief du même jour), pas de rond de chargement ni d'erreur
 * dédiée — une panne se tait, comme `SuivisViewModel.chargerEntrees` :
 * elle ne prive que cette carte, jamais le reste du profil.
 */
class BilanViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(BilanUi())
    val ui: StateFlow<BilanUi> = _ui
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val journal = try {
                api.journalComplet()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }
            _ui.update { it.copy(journal = bilanJournal(journal, LocalDate.now().year)) }
        }
    }
}
