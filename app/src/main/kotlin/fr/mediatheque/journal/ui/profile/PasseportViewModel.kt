package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.journalComplet
import fr.mediatheque.journal.ui.frise.TamponDecennie
import fr.mediatheque.journal.ui.frise.VoyageUi
import fr.mediatheque.journal.ui.frise.construireTamponDecennie
import fr.mediatheque.journal.ui.frise.toVoyageUi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PasseportUi(
    /** Nul tant que `GET /me/voyage` n'a pas répondu — la carte « Aucun tampon » n'apparaît qu'une fois la réponse là, vide. */
    val tampons: List<TamponDecennie>? = null,
    val error: ApiError? = null,
)

/**
 * Le passeport (décision 3 du brief du 21 septembre 2026, « les récompenses ») : charge ses
 * propres données (`GET /me/voyage`), pas depuis `FriseViewModel` — jumeau de
 * `PortefeuilleViewModel` à côté, indexé sur l'Activité (`Root.kt`, clé `"passeport"`). Plus jamais
 * vide quand Profil s'ouvre en premier, avant que la Frise n'ait chargé.
 *
 * Chaque tampon de la carte n'a besoin ici que de sa décennie et de sa récompense — `journal` vide
 * dans `construireTamponDecennie` (un seul appel, pas de journal complet à charger juste pour la
 * carte). `premiereEntree` et `films`, que le générique affiche, restent vides le temps d'un tap :
 * `ouvrirGenerique` les complète depuis le journal déjà chargé par la Frise (`Root.kt`), ou le
 * charge lui-même si elle ne l'a pas encore fait.
 */
class PasseportViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(PasseportUi())
    val ui: StateFlow<PasseportUi> = _ui
    private var refreshJob: Job? = null
    private var generiqueJob: Job? = null

    /** La dernière réponse de `GET /me/voyage`, gardée pour `ouvrirGenerique` : `recompense` par année sans un second appel. */
    private var voyage: VoyageUi = VoyageUi()

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val reponse = try {
                api.voyage()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                return@launch
            }
            voyage = reponse.toVoyageUi()
            val tampons = voyage.tampons.map { t -> construireTamponDecennie(t.decennie, voyage, emptyList()) }
            _ui.update { it.copy(tampons = tampons, error = null) }
        }
    }

    /**
     * Le tampon complet d'une décennie, pour rejouer son générique (décision 3) : réutilise
     * `journalDejaCharge` (la Frise, si elle a déjà chargé) plutôt que de le redemander ; s'il est
     * vide, le charge elle-même. `voyage` vient du dernier `refresh()` — le tap n'est possible que
     * depuis un tampon qu'il a déjà affiché.
     */
    fun ouvrirGenerique(decennie: Int, journalDejaCharge: List<JournalItem>, onPret: (TamponDecennie) -> Unit) {
        generiqueJob?.cancel()
        generiqueJob = viewModelScope.launch {
            val journal = journalDejaCharge.ifEmpty {
                try {
                    api.journalComplet()
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                    return@launch
                }
            }
            onPret(construireTamponDecennie(decennie, voyage, journal))
        }
    }
}
