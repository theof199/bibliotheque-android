package fr.mediatheque.journal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.ui.EtatFeuilleDeLecture
import fr.mediatheque.journal.ui.frise.EtatChronique
import fr.mediatheque.journal.ui.frise.etatChroniqueSuivant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Le carton d'un film (brief du 16 septembre 2026, phase 1 « le moteur », recentré sur le film et
 * son réalisateur par le brief du 24 septembre 2026, « le voyage revu ») : `GET
 * /reference/chroniques/films/{tmdbId}`, en pop-in (`FeuilleDeLecture`) plutôt qu'en carte inline
 * depuis ce même brief.
 *
 * Deux usages, un seul `ViewModel` :
 * - après un `create` réussi (`poll = true`) : relu toutes les trois secondes, dix fois au plus
 *   (`etatChroniqueSuivant`, `ui/frise/VoyageEtats.kt` — même règle que la chronique d'année, sur
 *   le même plafond `CHRONIQUE_ESSAIS_MAX`), puis abandon ;
 * - sur les fiches d'un film, « Le film » (`poll = false`) : une seule lecture au montage, puis
 *   relue quand la feuille s'ouvre tant que le carton n'est pas encore prêt.
 */
data class CartonUi(
    val etat: EtatChronique = EtatChronique.NON_CONFIGURE,
    val essais: Int = 0,
    val titre: String? = null,
    val texte: String? = null,
)

/**
 * L'état de la feuille de lecture du carton (décision 4 du brief du 24 septembre 2026, « le voyage
 * revu ») : chargement tant que le statut n'est pas prêt (qu'il s'agisse de la première écriture ou
 * d'une simple relecture), le texte une fois prêt, un message sans bouton « Réessayer » à l'abandon
 * ou sans chroniqueur configuré — la feuille reste fermable dans tous les cas. Fonction pure, testée
 * en JVM.
 */
fun etatFeuilleCarton(ui: CartonUi): EtatFeuilleDeLecture = when (ui.etat) {
    EtatChronique.PRETE -> EtatFeuilleDeLecture.Texte(ui.texte ?: "")
    EtatChronique.EN_PREPARATION -> EtatFeuilleDeLecture.Chargement
    EtatChronique.ABANDON -> EtatFeuilleDeLecture.Erreur("Le chroniqueur n’a pas fini. Reviens plus tard.", retryable = false)
    EtatChronique.NON_CONFIGURE -> EtatFeuilleDeLecture.Erreur("Le chroniqueur n’est pas configuré sur ce serveur.", retryable = false)
}

class CartonViewModel(
    private val api: JournalApi,
    private val tmdbId: Int,
    private val poll: Boolean,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(CartonUi())
    val ui: StateFlow<CartonUi> = _ui

    init {
        viewModelScope.launch {
            while (true) {
                val reponse = try {
                    api.cartonFilm(tmdbId)
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                val (etat, essais) = etatChroniqueSuivant(reponse.configure, reponse.statut, _ui.value.essais)
                _ui.update { it.copy(etat = etat, essais = essais, titre = reponse.titre, texte = reponse.texte) }
                if (!poll || etat != EtatChronique.EN_PREPARATION) return@launch
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
    }
}
