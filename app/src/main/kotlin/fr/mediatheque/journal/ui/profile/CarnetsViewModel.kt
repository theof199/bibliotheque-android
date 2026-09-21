package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.ui.frise.CarnetProfilUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CarnetsUi(
    /** Nul tant que `GET /me/voyage/carnets` n'a pas répondu — « Aucun carnet pour l'instant » n'apparaît qu'une fois la réponse là, vide. */
    val carnets: List<CarnetProfilUi>? = null,
    val enCours: List<Int> = emptyList(),
    val error: ApiError? = null,
)

/**
 * Le bloc « Carnets » du profil (décision 3 du brief du 22 septembre 2026, « le carnet »), sous
 * les Dépenses : charge ses propres données (`GET /me/voyage/carnets`), pas depuis `FriseViewModel` —
 * jumeau de `PortefeuilleViewModel`/`DepensesViewModel` à côté, indexé sur l'Activité (`Root.kt`,
 * clé `"carnets"`).
 */
class CarnetsViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(CarnetsUi())
    val ui: StateFlow<CarnetsUi> = _ui
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val reponse = try {
                api.voyageCarnets()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                return@launch
            }
            val carnets = reponse.carnets.map { CarnetProfilUi(it.annee, it.pages, it.fabrique_le) }
            _ui.update { it.copy(carnets = carnets, enCours = reponse.en_cours, error = null) }
        }
    }

    /**
     * Un tap sur une ligne du bloc (décision 4) : les octets seuls — c'est l'écran, seul à tenir un
     * `Context`, qui les écrit dans `cacheDir` et ouvre l'intention (`ouvrirCarnet`). `null` après
     * une session expirée, déjà traitée ici comme partout ailleurs.
     */
    suspend fun telechargerCarnetPdf(annee: Int): ByteArray? = try {
        api.telechargerCarnetPdf(annee)
    } catch (e: ApiError) {
        if (e.isUnauthenticated) {
            onUnauthenticated()
            null
        } else {
            throw e
        }
    }
}
