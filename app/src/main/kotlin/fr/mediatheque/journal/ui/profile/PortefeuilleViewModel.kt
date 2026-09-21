package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.ui.frise.TicketPortefeuilleUi
import fr.mediatheque.journal.ui.frise.trierPortefeuille
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PortefeuilleUi(
    /** Nul tant que `GET /me/voyage/tickets` n'a pas répondu — la carte « Aucun ticket » n'apparaît qu'une fois la réponse là, vide. */
    val tickets: List<TicketPortefeuilleUi>? = null,
    val error: ApiError? = null,
)

/**
 * Le portefeuille de tickets (décision 3 du brief du 21 septembre 2026, « le ticket ») : charge
 * ses propres données (`GET /me/voyage/tickets`), pas depuis `FriseViewModel` — jumeau de
 * `BilanViewModel` à côté, indexé sur l'Activité (`Root.kt`, clé `"portefeuille"`).
 */
class PortefeuilleViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(PortefeuilleUi())
    val ui: StateFlow<PortefeuilleUi> = _ui
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch { charger() }
    }

    /**
     * « Utiliser » sur un ticket non utilisé du portefeuille (décision 3) : même appel que le
     * calque (`POST .../utiliser`), puis recharge sa propre liste — attendue avant `onEcrit`, pour
     * que le portefeuille soit déjà à jour quand l'appelant fait suivre `frise.refresh()`.
     */
    fun utiliser(annee: Int, onEcrit: () -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            try {
                api.utiliserTicket(annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                return@launch
            }
            charger()
            onEcrit()
        }
    }

    private suspend fun charger() {
        val reponse = try {
            api.voyageTickets()
        } catch (e: ApiError) {
            if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
            return
        }
        val tickets = reponse.tickets.map { TicketPortefeuilleUi(it.annee, it.motif, it.utilise_le) }
        _ui.update { it.copy(tickets = trierPortefeuille(tickets), error = null) }
    }
}
