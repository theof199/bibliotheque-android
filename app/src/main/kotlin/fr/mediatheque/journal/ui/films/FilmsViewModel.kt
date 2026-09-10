package fr.mediatheque.journal.ui.films

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FilmsUi(
    val items: List<JournalItem> = emptyList(),
    val loading: Boolean = false,
    val endReached: Boolean = false,
    val error: ApiError? = null,
)

/** Curseur opaque, renvoyé tel quel ; `null` est la fin, et le seul signal. */
class FilmsViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(FilmsUi())
    val ui: StateFlow<FilmsUi> = _ui
    private var cursor: String? = null
    private var enCours: Job? = null

    // Pas d'`init { refresh() }` (jumeau de `ProfileViewModel`) : `Root.kt` déclenche déjà le
    // premier chargement par `LaunchedEffect(Unit) { films.refresh() }` à l'entrée sur l'écran.
    // Les deux ensemble lançaient deux `GET /me/journal` à la première ouverture, sans ordre
    // garanti entre les deux réponses (revue de la vague finale, Important 3).

    // `refresh()` ne vide plus l'état d'un coup (`_ui.value = FilmsUi()`) : partagé entre
    // l'accueil et « Mes films » (même clé `"films"` dans `Root.kt`), ce `ViewModel` se fait
    // rappeler `refresh()` à chaque entrée sur l'un ou l'autre écran, et vider les jaquettes
    // déjà affichées à chaque retour montrait un écran vide et le rond de chargement le temps de
    // l'aller-retour, « Enregistré » compris (relecture de la branche « l'accueil montre les
    // films vus », correction 5).
    fun refresh() {
        enCours?.cancel()
        cursor = null
        // `endReached` doit retomber à faux ici, avant `loadMore()` : sa garde refuserait sinon
        // de relancer une liste déjà entièrement chargée. Les jaquettes et l'erreur, eux,
        // restent en l'état jusqu'à ce que `loadMore()` les traite lui-même juste en dessous.
        _ui.update { it.copy(endReached = false) }
        loadMore()
    }

    fun loadMore() {
        if (enCours?.isActive == true || _ui.value.endReached) return
        _ui.update { it.copy(loading = true, error = null) }
        // Une page 1 (`cursor == null`) REMPLACE toujours la liste affichée ; une page suivante
        // l'ÉTEND. Testé sur `cursor`, pas sur un drapeau posé par `refresh()` : un premier essai
        // avait un drapeau `rafraichissement`, remis à faux dès que ce `loadMore()` démarrait —
        // donc déjà retombé quand « Réessayer » (`onRetry = vm::loadMore`) relançait, après coup,
        // la même page 1 qui venait d'échouer (Wi-Fi coupé). Cette page 1 s'ajoutait alors aux
        // jaquettes gardées à l'écran au lieu de les remplacer : deux fois le même film, deux
        // fois la même clé, et `LazyVerticalGrid` plantait sur « Key was already used »
        // (relecture du 10 septembre 2026). `cursor` n'a pas ce défaut : il reste à `null` tant
        // que la page 1 n'a pas réussi, qu'il s'agisse d'un premier essai ou d'un « Réessayer »
        // après son échec.
        val premierePage = cursor == null
        enCours = viewModelScope.launch {
            try {
                val page = api.journal(cursor)
                cursor = page.next_cursor
                _ui.update {
                    it.copy(
                        items = if (premierePage) page.items else it.items + page.items,
                        loading = false,
                        endReached = page.next_cursor == null,
                    )
                }
            } catch (e: ApiError) {
                _ui.update { it.copy(loading = false, error = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
            }
        }
    }
}
