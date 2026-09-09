package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.movies
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Pas de `loading` : `ProfileScreen` ne le lit jamais (rien à l'écran ne distingue « en train de
// charger » de « pas encore de chiffre », décision 3 de la tâche 7 déjà) — un champ mort (revue de
// la vague finale, mineur 10).
data class ProfileUi(val total: Int? = null, val thisYear: Int? = null, val error: ApiError? = null)

/**
 * Deux chiffres, lus dans le tableau de bord existant. Rien n'est recalculé ici.
 *
 * Un seul déclencheur : `ProfileScreen` appelle `retry()` depuis un `LaunchedEffect(Unit)`
 * à chaque entrée sur l'écran (le `ViewModel` est indexé sur l'Activité, clé fixe
 * `"profile"` — sans ce rappel, les chiffres resteraient ceux du premier chargement, en
 * retard d'un film après une correction). Un `init { load() }` en plus de ce rappel
 * lançait deux `GET /stats` concurrents à chaque ouverture, sans ordre garanti entre les
 * deux réponses, sur une route qui recalcule quatre périodes sans cache : retiré (revue du
 * tour de correction 1).
 */
class ProfileViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(ProfileUi())
    val ui: StateFlow<ProfileUi> = _ui
    private var enCours: Job? = null

    fun retry() = load()

    private fun load() {
        // Annule le chargement en cours plutôt que d'en empiler un second : un
        // « Réessayer » qui suit de près le précédent ne lance qu'un `GET /stats` de plus,
        // jamais deux en vol sans ordre garanti (jumeau de `FilmsViewModel`).
        enCours?.cancel()
        _ui.value = ProfileUi()
        enCours = viewModelScope.launch {
            try {
                val periods = api.stats().dashboard.periods
                _ui.value = ProfileUi(total = periods.all.counts.movies, thisYear = periods.year.counts.movies)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.value = ProfileUi(error = e)
            }
        }
    }
}
