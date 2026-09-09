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

    fun refresh() {
        enCours?.cancel()
        cursor = null
        _ui.value = FilmsUi()
        loadMore()
    }

    fun loadMore() {
        if (enCours?.isActive == true || _ui.value.endReached) return
        _ui.update { it.copy(loading = true, error = null) }
        enCours = viewModelScope.launch {
            try {
                val page = api.journal(cursor)
                cursor = page.next_cursor
                _ui.update { it.copy(items = it.items + page.items, loading = false, endReached = page.next_cursor == null) }
            } catch (e: ApiError) {
                _ui.update { it.copy(loading = false, error = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
            }
        }
    }
}
