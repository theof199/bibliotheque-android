package fr.mediatheque.journal.ui.realisateur

import androidx.lifecycle.ViewModel
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.RealisateurCredit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/** Ce qu'on sait des réalisateurs crédités sur un film, une fois `resoudre` revenu. */
sealed interface EtatRealisateursFilm {
    data class Pret(val realisateurs: List<RealisateurCredit>) : EtatRealisateursFilm
    data object Indisponible : EtatRealisateursFilm
}

/**
 * Résout les réalisateurs crédités sur un film (décision 3 du brief du 21 septembre 2026, « la
 * page réalisateur ») : une seule instance partagée par tous les écrans qui affichent un nom de
 * réalisateur touchable (`Root.kt`, clé `"realisateur-resolveur"`) — la fiche du Voyage, l'écran
 * de correction, la carte de soirée, la fiche simple d'un film, chacun via `NomRealisateurTouchable`
 * (`RealisateurScreen.kt`). Le cache est en mémoire, par `tmdb_id` de **film** : un même film
 * touché depuis deux écrans ne rappelle le back qu'une fois.
 */
class RealisateurResolveur(private val api: JournalApi) : ViewModel() {
    private val _etats = MutableStateFlow<Map<Int, EtatRealisateursFilm>>(emptyMap())
    val etats: StateFlow<Map<Int, EtatRealisateursFilm>> = _etats

    /** Résout, en cache : un second appel sur le même film ne rappelle jamais le back. */
    suspend fun resoudre(filmTmdbId: Int): List<RealisateurCredit> {
        (_etats.value[filmTmdbId] as? EtatRealisateursFilm.Pret)?.let { return it.realisateurs }
        return try {
            val realisateurs = api.realisateursDuFilm(filmTmdbId)
            _etats.update { it + (filmTmdbId to EtatRealisateursFilm.Pret(realisateurs)) }
            realisateurs
        } catch (e: ApiError) {
            _etats.update { it + (filmTmdbId to EtatRealisateursFilm.Indisponible) }
            emptyList()
        }
    }
}
