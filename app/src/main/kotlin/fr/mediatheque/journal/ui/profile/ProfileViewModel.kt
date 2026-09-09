package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.movies
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class ProfileUi(val loading: Boolean = true, val total: Int? = null, val thisYear: Int? = null, val error: ApiError? = null)

/** Deux chiffres, lus dans le tableau de bord existant. Rien n'est recalculé ici. */
class ProfileViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUi())
    val ui: StateFlow<ProfileUi> = _ui

    init { load() }

    fun retry() = load()

    private fun load() {
        _ui.value = ProfileUi(loading = true)
        viewModelScope.launch {
            try {
                val periods = api.stats().dashboard.periods
                _ui.value = ProfileUi(loading = false, total = periods.all.counts.movies, thisYear = periods.year.counts.movies)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.value = ProfileUi(loading = false, error = e)
            }
        }
    }
}
