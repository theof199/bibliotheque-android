package fr.mediatheque.journal.ui.films

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.barreDuBas

/** `Screen.Films` : « Mes films », la liste du journal, empilée depuis le profil seulement. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeFilms() {
    val films: FilmsViewModel = viewModel(key = "films") { FilmsViewModel(container.api, session::expire) }
    // Même piège que `LoginViewModel`/`SearchViewModel` (`Root.kt`, revues des
    // tâches 4 et 5) : ce `ViewModel` est indexé sur l'Activité, la clé fixe ne
    // lui donne pas de portée. Sans ce rechargement à chaque entrée, la liste
    // resterait celle de la première visite après une correction ou une
    // suppression faites depuis `Screen.Edit` (décision 1 de la tâche 7).
    LaunchedEffect(Unit) { films.refresh() }
    FilmsScreen(
        films,
        onBack = nav::pop,
        onOpen = { nav.push(Screen.Edit(it)) },
        // L'action de l'état vide (point 16 de la revue du 24 septembre 2026) :
        // même recherche que le bouton rond de l'accueil.
        onAdd = { nav.push(Screen.Search) },
        // L'affiche partagée (geste 8) : jumeau de l'accueil, même paire.
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        // « Profil » est surlignée ici mais ramène au profil par un `pop`, pas
        // un `push` : cet écran ne s'empile que depuis lui.
        bottomBar = { barreDuBas(Screen.Films, onProfile = nav::pop) },
    )
}
