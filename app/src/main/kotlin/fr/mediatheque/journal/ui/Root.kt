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
import fr.mediatheque.journal.ui.profile.SensCritiqueScreen
import fr.mediatheque.journal.ui.profile.SensCritiqueViewModel
import fr.mediatheque.journal.ui.search.SearchScreen
import fr.mediatheque.journal.ui.search.SearchViewModel

@Composable
fun Root(container: AppContainer) {
    val session: SessionViewModel = viewModel {
        SessionViewModel(container.api, container.cookieJar, container.sensCritiqueSync)
    }
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
            // Ce `ViewModel` est indexé sur l'Activité (la clé ci-dessous ne change rien à sa
            // portée) : sans remise à zéro, la même instance revient à chaque ouverture de
            // l'écran, requête et résultats de la visite précédente compris — jumeau du piège
            // réglé sur `LoginViewModel` ci-dessus (revue de la tâche 5).
            //
            // Obtenu ici, hors de la lambda du `Crossfade`, et pas dans la branche `Screen.Search`
            // plus bas : `Crossfade` *dispose* la branche quittée et la recompose à neuf à chaque
            // retour, donc un `LaunchedEffect` posé dans cette branche serait une instance neuve à
            // chaque entrée et se rejouerait quelle que soit sa clé — y compris au retour du
            // formulaire par `pop`, ce qu'on veut justement éviter (mineur 8 de la vague finale).
            // Le jumeau `Screen.Films` plus bas exploite l'inverse volontairement : son
            // `LaunchedEffect(Unit)` reste dans le `Crossfade` pour recharger à chaque entrée.
            val search: SearchViewModel = viewModel(key = "search") { SearchViewModel(container.api, session::expire) }
            LaunchedEffect(nav.searchVisits) { if (nav.searchVisits > 0) search.reset() }
            Crossfade(targetState = nav.current, animationSpec = tween(200), label = "ecran") { screen ->
                when (screen) {
                    Screen.Home -> {
                        // Même `FilmsViewModel` que `Screen.Films` plus bas (même clé `"films"`) :
                        // l'accueil et « Mes films » sont deux présentations d'une seule source
                        // (brief du 10 septembre 2026). Même piège, même remède que `Screen.Films`
                        // juste en dessous : ce `ViewModel` est indexé sur l'Activité, donc sans ce
                        // rechargement à chaque entrée, la grille resterait celle de la première
                        // visite après l'ajout d'un film depuis `Screen.Search`.
                        //
                        // `Screen.Home` et `Screen.Films` ne sont jamais voisins dans la pile : on
                        // n'empile `Screen.Films` que depuis `Screen.Profile`, et seul `FormScreen`
                        // appelle `nav.home(...)`, qui vide la pile plutôt que de faire un `pop` vers
                        // `Screen.Home`. Leurs deux `refresh()` sur la même instance ne se croisent
                        // donc jamais dans un même `Crossfade` — mais rien dans la pile ne l'interdit
                        // si un futur chemin de navigation les rapproche.
                        val films: FilmsViewModel = viewModel(key = "films") { FilmsViewModel(container.api, session::expire) }
                        LaunchedEffect(Unit) { films.refresh() }
                        HomeScreen(
                            vm = films,
                            nav = nav,
                            onAdd = { nav.push(Screen.Search) },
                            onProfile = { nav.push(Screen.Profile) },
                            onOpen = { nav.push(Screen.Edit(it)) },
                        )
                    }
                    Screen.Search -> SearchScreen(search, onBack = nav::pop, onPick = { nav.push(Screen.Form(it)) })
                    is Screen.Form -> {
                        // Ce `ViewModel` est indexé sur l'Activité (jumeau du piège réglé sur
                        // `SearchViewModel.reset()` ci-dessus) : la clé ne donne pas de portée,
                        // elle nomme une case dans son magasin. Une clé fixe (`"form"`) rendrait
                        // le `FormViewModel` du premier film à tous les suivants ; l'identité du
                        // film dans la clé ouvre une case par film.
                        val form: FormViewModel = viewModel(key = "form:${screen.result.source}:${screen.result.external_id}") {
                            FormViewModel(container.api, FormMode.Create(screen.result), container.sensCritiqueSync, session::expire)
                        }
                        FormScreen(form, nav = nav, onBack = nav::pop)
                    }
                    Screen.Profile -> {
                        val profile: ProfileViewModel = viewModel(key = "profile") { ProfileViewModel(container.api, session::expire) }
                        // Même instance (même clé) que `Screen.SensCritique` plus bas : un aller-retour vers cet
                        // écran doit revenir sur le pseudo qu'on vient d'y lire, pas en repartir à zéro.
                        val senscritique: SensCritiqueViewModel = viewModel(key = "senscritique") {
                            SensCritiqueViewModel(container.sensCritiqueStore, container.sensCritiqueAuthClient)
                        }
                        ProfileScreen(
                            s.user,
                            profile,
                            senscritique,
                            onBack = nav::pop,
                            onFilms = { nav.push(Screen.Films) },
                            onSensCritique = { nav.push(Screen.SensCritique) },
                            onSignOut = session::signOut,
                        )
                    }
                    Screen.Films -> {
                        val films: FilmsViewModel = viewModel(key = "films") { FilmsViewModel(container.api, session::expire) }
                        // Même piège que `LoginViewModel`/`SearchViewModel` ci-dessus (revues des
                        // tâches 4 et 5) : ce `ViewModel` est indexé sur l'Activité, la clé fixe ne
                        // lui donne pas de portée. Sans ce rechargement à chaque entrée, la liste
                        // resterait celle de la première visite après une correction ou une
                        // suppression faites depuis `Screen.Edit` (décision 1 de la tâche 7).
                        LaunchedEffect(Unit) { films.refresh() }
                        FilmsScreen(films, onBack = nav::pop, onOpen = { nav.push(Screen.Edit(it)) })
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
                        //
                        // La clé porte aussi `screen.item.hashCode()` (revue du tour de
                        // correction 1) : `JournalItem` est une `data class`, son hash change
                        // avec la note, les réactions, le commentaire ou la date. Sans lui, la
                        // clé ne dépendait que de l'identifiant de l'entrée — rouvrir une fiche
                        // déjà corrigée retombait sur l'ancien `FormViewModel`, encore dans le
                        // magasin de l'Activité avec le brouillon d'avant la correction, et
                        // ignorait le `screen.item` frais que « Mes films » vient de fournir.
                        // Les anciennes instances (une par version corrigée) restent dans ce
                        // magasin pour la vie de l'Activité : négligeable pour un usage
                        // personnel.
                        val form: FormViewModel = viewModel(key = "edit:${screen.item.entry.id}:${screen.item.hashCode()}") {
                            FormViewModel(container.api, FormMode.Edit(screen.item), container.sensCritiqueSync, session::expire)
                        }
                        FormScreen(form, nav = nav, onBack = nav::pop)
                    }
                    Screen.SensCritique -> {
                        val senscritique: SensCritiqueViewModel = viewModel(key = "senscritique") {
                            SensCritiqueViewModel(container.sensCritiqueStore, container.sensCritiqueAuthClient)
                        }
                        SensCritiqueScreen(senscritique, onBack = nav::pop)
                    }
                }
            }
        }
    }
}
