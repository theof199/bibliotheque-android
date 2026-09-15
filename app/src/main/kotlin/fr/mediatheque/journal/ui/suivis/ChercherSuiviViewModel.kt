package fr.mediatheque.journal.ui.suivis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La requête réellement envoyée à `GET /reference/personnes` ou
 * `GET /reference/sagas`, ou `null` quand il n'y a rien à chercher. Le back
 * exige `q` d'au moins un caractère (`400` sinon) : une entrée vide, ou faite
 * d'espaces, ne doit donc pas partir — et une fois la croix d'effacement
 * touchée, les résultats précédents s'en vont avec elle plutôt que de rester
 * sous un champ vide.
 */
fun requeteUtile(saisie: String): String? = saisie.trim().ifEmpty { null }

data class ChercherSuiviUi(
    val query: String = "",
    val results: List<EntiteSuivie> = emptyList(),
    val loading: Boolean = false,
    val error: ApiError? = null,
    /** La dernière requête réellement envoyée — pour « Rien trouvé pour “…” ». */
    val searched: String = "",
)

/**
 * Une frappe, 400 ms de silence, une requête (brief du 15 septembre 2026 ; la
 * recherche de films en attend 300, elle) ; une nouvelle frappe annule la
 * requête en vol (`collectLatest`). Les résultats précédents restent
 * affichés jusqu'aux nouveaux — design §6.
 *
 * Indépendant de `SuivisViewModel` (jumeau de l'ancien
 * `ChercherRealisateurViewModel`) : il ne fait que chercher, sur la source
 * fixée à sa construction (`Root.kt` en fait une nouvelle instance par
 * segment). C'est `SuivisViewModel.ajouter` qui suit l'entité choisie, parce
 * que c'est lui qui tient la liste à rafraîchir et le bandeau « Ajouté » à
 * montrer.
 */
@OptIn(FlowPreview::class)
class ChercherSuiviViewModel(
    private val api: JournalApi,
    private val source: SourceSuivi,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _ui = MutableStateFlow(ChercherSuiviUi())
    val ui: StateFlow<ChercherSuiviUi> = _ui

    init {
        viewModelScope.launch {
            query.debounce(400).distinctUntilChanged().collectLatest { q -> chercher(q) }
        }
    }

    /**
     * Jumeau de `SearchViewModel.reset()` : ce `ViewModel` est indexé sur
     * l'Activité (la clé de `viewModel(key = ...)` inclut la source, mais ne
     * lui donne pas de portée pour autant). Sans remise à zéro explicite, la
     * même instance reviendrait à chaque ouverture de l'écran, requête et
     * résultats de la visite précédente compris.
     */
    fun reset() {
        query.value = ""
        _ui.value = ChercherSuiviUi()
    }

    fun onQueryChange(value: String) {
        query.value = value
        _ui.update { it.copy(query = value) }
    }

    fun retry() {
        viewModelScope.launch { chercher(query.value) }
    }

    private suspend fun chercher(saisie: String) {
        val q = requeteUtile(saisie)
        if (q == null) {
            _ui.update { it.copy(results = emptyList(), loading = false, error = null, searched = "") }
            return
        }
        _ui.update { it.copy(loading = true, error = null) }
        try {
            val results = when (source) {
                SourceSuivi.REALISATEURS -> api.chercherPersonnes(q).map { it.versEntite() }
                SourceSuivi.SAGAS -> api.chercherSagas(q).map { it.versEntite() }
            }
            _ui.update { it.copy(results = results, loading = false, searched = q) }
        } catch (e: ApiError) {
            if (e.isUnauthenticated) {
                _ui.update { it.copy(loading = false) }
                onUnauthenticated()
            } else {
                _ui.update { it.copy(loading = false, error = e, searched = q) }
            }
        }
    }
}
