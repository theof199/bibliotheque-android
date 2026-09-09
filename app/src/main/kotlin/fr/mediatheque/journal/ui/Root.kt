package fr.mediatheque.journal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.AppContainer
import fr.mediatheque.journal.ui.films.FilmsScreen
import fr.mediatheque.journal.ui.films.FilmsViewModel
import fr.mediatheque.journal.ui.form.FormMode
import fr.mediatheque.journal.ui.form.FormScreen
import fr.mediatheque.journal.ui.form.FormViewModel
import fr.mediatheque.journal.ui.home.HomeScreen
import fr.mediatheque.journal.ui.login.LoginScreen
import fr.mediatheque.journal.ui.login.LoginViewModel
import fr.mediatheque.journal.ui.profile.ProfileScreen
import fr.mediatheque.journal.ui.profile.ProfileViewModel
import fr.mediatheque.journal.ui.search.SearchScreen
import fr.mediatheque.journal.ui.search.SearchViewModel

@Composable
fun Root(container: AppContainer) {
    val session: SessionViewModel = viewModel { SessionViewModel(container.api, container.cookieJar) }
    val state by session.state.collectAsState()

    when (val s = state) {
        SessionState.Checking -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        SessionState.SignedOut -> {
            val login: LoginViewModel = viewModel { LoginViewModel(container.api, session::signedIn) }
            // Ce `ViewModel` est indexé sur l'Activité (aucune clé) : sans remise à zéro, la même
            // instance revient après une déconnexion, pseudo et mot de passe encore remplis (revue
            // de la tâche 4). On la remet à chaque entrée dans cet état plutôt que de lui donner une
            // clé, qui la recréerait sans besoin dès la connexion initiale.
            LaunchedEffect(Unit) { login.reset() }
            LoginScreen(login)
        }
        is SessionState.Unreachable -> Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp), contentAlignment = Alignment.Center) {
            ErrorBlock(s.message, retryable = true, onRetry = session::retry)
        }
        is SessionState.SignedIn -> {
            val nav = rememberNavigator()
            BackHandler(enabled = nav.canPop) { nav.pop() }
            Crossfade(targetState = nav.current, animationSpec = tween(200), label = "ecran") { screen ->
                when (screen) {
                    Screen.Home -> HomeScreen(
                        message = nav.pendingMessage,
                        onMessageShown = { nav.pendingMessage = null },
                        onAdd = { nav.push(Screen.Search) },
                        onProfile = { nav.push(Screen.Profile) },
                    )
                    Screen.Search -> {
                        val search: SearchViewModel = viewModel(key = "search") { SearchViewModel(container.api, session::expire) }
                        // Ce `ViewModel` est indexé sur l'Activité (la clé ci-dessus ne change rien à
                        // sa portée) : sans remise à zéro, la même instance revient à chaque ouverture
                        // de l'écran, requête et résultats de la visite précédente compris — jumeau du
                        // piège réglé sur `LoginViewModel` ci-dessus (revue de la tâche 5).
                        LaunchedEffect(Unit) { search.reset() }
                        SearchScreen(search, onBack = nav::pop, onPick = { nav.push(Screen.Form(it)) })
                    }
                    is Screen.Form -> {
                        // Ce `ViewModel` est indexé sur l'Activité (jumeau du piège réglé sur
                        // `SearchViewModel.reset()` ci-dessus) : la clé ne donne pas de portée,
                        // elle nomme une case dans son magasin. Une clé fixe (`"form"`) rendrait
                        // le `FormViewModel` du premier film à tous les suivants ; l'identité du
                        // film dans la clé ouvre une case par film.
                        val form: FormViewModel = viewModel(key = "form:${screen.result.source}:${screen.result.external_id}") {
                            FormViewModel(container.api, FormMode.Create(screen.result), session::expire)
                        }
                        FormScreen(form, nav = nav, onBack = nav::pop)
                    }
                    Screen.Profile -> {
                        val profile: ProfileViewModel = viewModel(key = "profile") { ProfileViewModel(container.api, session::expire) }
                        ProfileScreen(s.user, profile, onBack = nav::pop, onFilms = { nav.push(Screen.Films) }, onSignOut = session::signOut)
                    }
                    Screen.Films -> {
                        val films: FilmsViewModel = viewModel(key = "films") { FilmsViewModel(container.api, session::expire) }
                        // Même piège que `LoginViewModel`/`SearchViewModel` ci-dessus (revues des
                        // tâches 4 et 5) : ce `ViewModel` est indexé sur l'Activité, la clé fixe ne
                        // lui donne pas de portée. Sans ce rechargement à chaque entrée, la liste
                        // resterait celle de la première visite après une correction ou une
                        // suppression faites depuis `Screen.Edit` (décision 1 de la tâche 7).
                        LaunchedEffect(Unit) { films.refresh() }
                        FilmsScreen(
                            films,
                            message = nav.pendingMessage,
                            onMessageShown = { nav.pendingMessage = null },
                            onBack = nav::pop,
                            onOpen = { nav.push(Screen.Edit(it)) },
                        )
                    }
                    is Screen.Edit -> {
                        // Indexé sur l'entrée corrigée (jumeau de `Screen.Form` ci-dessus) : une
                        // clé fixe rendrait le `FormViewModel` du premier visionnage corrigé à
                        // tous les suivants. Il ne reçoit jamais `nav` au constructeur (revue de
                        // la tâche 6, décision 1 de la tâche 7) : c'est `FormScreen`, reçu ici
                        // avec le `nav` du moment, qui consomme `ui.done` et referme la boucle
                        // par `nav.home(...)` — le retour à l'accueil après « Corrigé » ou
                        // « Supprimé » vient de là, pas d'ici ; « Mes films » se recharge à sa
                        // prochaine ouverture, par le `LaunchedEffect` ci-dessus.
                        val form: FormViewModel = viewModel(key = "edit:${screen.item.entry.id}") {
                            FormViewModel(container.api, FormMode.Edit(screen.item), session::expire)
                        }
                        FormScreen(form, nav = nav, onBack = nav::pop)
                    }
                }
            }
        }
    }
}
