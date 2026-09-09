package fr.mediatheque.journal.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SearchUi(
    val query: String = "",
    val results: List<SearchResult> = emptyList(),
    val loading: Boolean = false,
    val error: ApiError? = null,
    /** La dernière requête réellement envoyée — pour « Rien trouvé pour “…” ». */
    val searched: String = "",
)

/**
 * Une frappe, 300 ms de silence, une requête ; une nouvelle frappe annule la
 * requête en vol (`collectLatest`). Les résultats précédents restent affichés
 * jusqu'aux nouveaux — design §6.
 */
@OptIn(FlowPreview::class)
class SearchViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val query = MutableStateFlow("")
    private val _ui = MutableStateFlow(SearchUi())
    val ui: StateFlow<SearchUi> = _ui

    init {
        viewModelScope.launch {
            query.debounce(300).distinctUntilChanged().collectLatest { q -> search(q) }
        }
    }

    fun onQueryChange(value: String) {
        query.value = value
        _ui.update { it.copy(query = value) }
    }

    fun retry() {
        viewModelScope.launch { search(query.value) }
    }

    private suspend fun search(q: String) {
        val trimmed = q.trim()
        if (trimmed.isEmpty()) {
            _ui.update { it.copy(results = emptyList(), loading = false, error = null, searched = "") }
            return
        }
        _ui.update { it.copy(loading = true, error = null) }
        try {
            val results = api.searchMovies(trimmed)
            _ui.update { it.copy(results = results, loading = false, searched = trimmed) }
        } catch (e: ApiError) {
            if (e.isUnauthenticated) {
                _ui.update { it.copy(loading = false) }
                onUnauthenticated()
            } else {
                _ui.update { it.copy(loading = false, error = e, searched = trimmed) }
            }
        }
    }
}
