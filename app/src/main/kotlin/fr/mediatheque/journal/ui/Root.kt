package fr.mediatheque.journal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.AppContainer
import fr.mediatheque.journal.ui.home.HomeScreen
import fr.mediatheque.journal.ui.login.LoginScreen
import fr.mediatheque.journal.ui.login.LoginViewModel
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
                    is Screen.Form -> Placeholder("Formulaire — tâche 6", nav::pop)
                    Screen.Profile -> Placeholder("Profil — tâche 7", nav::pop)
                    Screen.Films -> Placeholder("Mes films — tâche 7", nav::pop)
                    is Screen.Edit -> Placeholder("Correction — tâche 7", nav::pop)
                }
            }
        }
    }
}

/** Un écran non écrit encore : les tâches 6 et 7 le remplacent. */
@Composable
private fun Placeholder(text: String, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {
        Text(text, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onBack) { Text("Retour") }
    }
}
