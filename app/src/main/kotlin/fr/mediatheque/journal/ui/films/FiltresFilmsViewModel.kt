package fr.mediatheque.journal.ui.films

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

/**
 * La recherche, le tri et les filtres de « Mes films » (`FiltresFilms`, `FilmsEtats.kt`).
 *
 * **Mémorisés pour la session, jamais persistés** (décision du propriétaire du 24 septembre
 * 2026) : `FilmsRoute.kt` indexe ce `ViewModel` sur l'Activité, sous la clé
 * `"mes-films-filtres"` — quitter l'écran puis y revenir retrouve les mêmes filtres, tuer
 * l'appli les remet à zéro. Rien n'est écrit sur le disque.
 *
 * Séparé de `FilmsViewModel`, que l'accueil partage sous la clé `"films"` : filtrer « Mes films »
 * ne doit rien changer à ce que montre l'accueil.
 */
class FiltresFilmsViewModel : ViewModel() {
    private val _filtres = MutableStateFlow(FiltresFilms())
    val filtres: StateFlow<FiltresFilms> = _filtres

    fun setTexte(texte: String) {
        _filtres.update { it.copy(texte = texte) }
    }

    /**
     * Le tap sur la puce Date : récents d'abord ↔ anciens d'abord. Depuis un tri par note, revient
     * au tri par date par défaut (récents d'abord) plutôt que de sauter aux anciens.
     */
    fun basculerOrdreDate() {
        _filtres.update {
            it.copy(tri = if (it.tri == TriFilms.DATE_DESC) TriFilms.DATE_ASC else TriFilms.DATE_DESC)
        }
    }

    fun setTri(tri: TriFilms) {
        _filtres.update { it.copy(tri = tri) }
    }

    /** `null` retire la note minimale. */
    fun setNoteMin(noteMin: Int?) {
        _filtres.update { it.copy(noteMin = noteMin) }
    }

    /** Coche la réaction si elle ne l'était pas, la décoche sinon. */
    fun basculerReaction(key: String) {
        _filtres.update { it.copy(reactions = if (key in it.reactions) it.reactions - key else it.reactions + key) }
    }

    /** Tout revient à zéro : texte vide, récents d'abord, aucune note minimale, aucune réaction. */
    fun effacer() {
        _filtres.value = FiltresFilms()
    }
}
