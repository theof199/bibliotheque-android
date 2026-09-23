package fr.mediatheque.journal.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import fr.mediatheque.journal.AppContainer
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.ui.frise.FriseViewModel
import fr.mediatheque.journal.ui.profile.LetterboxdImportViewModel
import fr.mediatheque.journal.ui.profile.SensCritiqueViewModel
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.search.SearchViewModel
import fr.mediatheque.journal.ui.suivis.SuivisViewModel

/**
 * Ce que toute route d'écran reçoit de `Root.kt` (découpage du 23 septembre 2026) : le conteneur,
 * la pile, la session, l'utilisateur connecté, les six `ViewModel` hoistés hors de
 * l'`AnimatedContent` (voir leurs commentaires dans `Root.kt` : chacun y est indexé sur l'Activité
 * et remis à zéro ou rafraîchi par un effet qui doit survivre au changement d'écran), les réactions
 * favorites calculées au même niveau, et les deux portées du mouvement partagé.
 *
 * Construite **dans** la lambda de l'`AnimatedContent`, à chaque composition d'une branche, jamais
 * `remember` : `animatedVisibilityScope` est le `this` de cette lambda, et pendant une transition
 * la branche qui sort et celle qui entre en ont chacune une. `user` vient du `SessionState.SignedIn`
 * courant, qui ne change qu'en quittant cet état.
 *
 * Chaque paquet `ui/<feature>/` déclare ses branches comme des extensions de cette classe
 * (`routeHome`, `routeAnnee`, …) : le corps d'une route est ce qui vivait dans la branche
 * correspondante du `when` de `Root.kt`, à l'identique — mêmes clés de `viewModel`, mêmes
 * `LaunchedEffect` (qui restent donc disposés et rejoués à chaque entrée, comme avant).
 */
@OptIn(ExperimentalSharedTransitionApi::class)
class PorteeEcrans(
    val container: AppContainer,
    val nav: Navigator,
    val session: SessionViewModel,
    val user: User,
    val search: SearchViewModel,
    val senscritique: SensCritiqueViewModel,
    val frise: FriseViewModel,
    val suivis: SuivisViewModel,
    val realisateurResolveur: RealisateurResolveur,
    val letterboxd: LetterboxdImportViewModel,
    /**
     * Les réactions favorites (point 7 de la revue du 24 septembre 2026) : calculées une fois dans
     * `Root.kt` sur le journal déjà chargé par la Frise, pour le formulaire (création et correction,
     * `FormRoutes.kt`) — jamais une requête réseau de plus, jamais recalculées à chaque écran.
     */
    val reactionsFavorites: List<String>,
    anneeAVerifierVerdictState: MutableState<Int?>,
    val sharedTransitionScope: SharedTransitionScope,
    val animatedVisibilityScope: AnimatedVisibilityScope,
) {
    /**
     * Le verdict de maturité (brief du 25 septembre 2026, « le verdict de maturité se relit ») :
     * l'année de sortie d'un film tout juste journalisé, posée par `nav.ticketRelectures`, pour la
     * veille du verdict sur `Screen.Annee`. L'état lui-même est hoisté dans `Root.kt` (hors de
     * l'`AnimatedContent`, sans quoi l'événement à un coup pourrait arriver avant que
     * `Screen.Annee` ne soit recomposé pour le collecter) ; ici, une propriété déléguée dessus, lue
     * et remise à `null` par `routeAnnee`.
     */
    var anneeAVerifierVerdict: Int? by anneeAVerifierVerdictState
}

/**
 * La barre du bas câblée sur la pile, un seul endroit pour les six écrans qui la portent (accueil,
 * Frise, Suivis, « Au ciné », profil, « Mes films ») plutôt que six copies du même bloc.
 *
 * `onProfile` : « Profil » est surlignée sur « Mes films » mais y ramène au profil par un `pop`,
 * pas un `push` — cet écran ne s'empile que depuis lui (`FilmsRoute.kt` passe `nav::pop`).
 */
@Composable
fun PorteeEcrans.barreDuBas(screen: Screen, onProfile: () -> Unit = { nav.push(Screen.Profile) }) {
    JournalBottomBar(
        screen,
        onHome = { nav.home() },
        onFrise = { nav.push(Screen.Frise) },
        onSuivis = { nav.push(Screen.Suivis) },
        onCinema = { nav.push(Screen.Cinema) },
        onProfile = onProfile,
    )
}
