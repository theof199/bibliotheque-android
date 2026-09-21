package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.ui.formatCentimes
import fr.mediatheque.journal.ui.formatMoisAnnee
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Une dépense mensuelle au chroniqueur, telle que `GET /me/voyage/depenses` la donne pour un mois. */
data class DepenseMoisUi(val mois: String, val appels: Int, val coutCentimes: Double)

data class DepensesUi(
    /** Nul tant que `GET /me/voyage/depenses` n'a pas répondu — pas encore une absence. */
    val mois: List<DepenseMoisUi>? = null,
    val error: ApiError? = null,
)

/**
 * Les dépenses au chroniqueur (décision 2 du brief du 21 septembre 2026, « les dépenses »), sous
 * le portefeuille : charge ses propres données (`GET /me/voyage/depenses`), pas depuis
 * `FriseViewModel` — jumeau de `PortefeuilleViewModel` à côté, indexé sur l'Activité (`Root.kt`,
 * clé `"depenses"`). Aucun appel IA : cette lecture ne fait qu'agréger des appels déjà passés.
 */
class DepensesViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(DepensesUi())
    val ui: StateFlow<DepensesUi> = _ui
    private var job: Job? = null

    fun refresh() {
        job?.cancel()
        job = viewModelScope.launch {
            val reponse = try {
                api.voyageDepenses()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                return@launch
            }
            val mois = reponse.mois.map { DepenseMoisUi(it.mois, it.appels, it.cout_centimes) }
            _ui.update { it.copy(mois = mois, error = null) }
        }
    }
}

/** « appel » au singulier seulement à un — jumeau des pluriels français usuels du dépôt. */
private fun motAppel(appels: Int): String = if (appels == 1) "appel" else "appels"

private fun ligneDepense(label: String, appels: Int, coutCentimes: Double): String =
    "$label : ${formatCentimes(coutCentimes)} centimes · $appels ${motAppel(appels)}"

/**
 * La ligne du mois courant, sous le portefeuille (décision 2 du brief du 21 septembre 2026, « les
 * dépenses ») : « Ce mois-ci : 12,7 centimes · 10 appels », ou « 0 centime · 0 appel » — forme
 * figée, pas dérivée de `ligneDepense` — quand le mois manque de la réponse. Fonction pure, testée
 * en JVM.
 */
fun ligneMoisCourant(moisCourant: DepenseMoisUi?): String =
    if (moisCourant == null) "Ce mois-ci : 0 centime · 0 appel" else ligneDepense("Ce mois-ci", moisCourant.appels, moisCourant.coutCentimes)

/** Une ligne de mois précédent, une fois la ligne du mois courant dépliée : « Août 2026 : 4,2 centimes · 3 appels ». */
fun ligneMoisPrecedent(mois: DepenseMoisUi): String = ligneDepense(formatMoisAnnee(mois.mois), mois.appels, mois.coutCentimes)

/**
 * Les mois précédents, par mois décroissant (décision 2) — le mois courant excepté ; le tri
 * lexicographique suffit, `mois` étant toujours `"aaaa-mm"`. Fonction pure, testée en JVM.
 */
fun triMoisPrecedents(mois: List<DepenseMoisUi>, moisCourant: String): List<DepenseMoisUi> =
    mois.filterNot { it.mois == moisCourant }.sortedByDescending { it.mois }
