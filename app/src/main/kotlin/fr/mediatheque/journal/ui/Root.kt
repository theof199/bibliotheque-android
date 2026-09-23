package fr.mediatheque.journal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.AppContainer
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.celebrations.FilmEnregistreCalque
import fr.mediatheque.journal.ui.cinema.routeCinema
import fr.mediatheque.journal.ui.films.routeFilms
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.form.routeEdit
import fr.mediatheque.journal.ui.form.routeForm
import fr.mediatheque.journal.ui.frise.FriseViewModel
import fr.mediatheque.journal.ui.frise.TicketHote
import fr.mediatheque.journal.ui.frise.routeAnnee
import fr.mediatheque.journal.ui.frise.routeDecennie
import fr.mediatheque.journal.ui.frise.routeFicheVoyage
import fr.mediatheque.journal.ui.frise.routeFrise
import fr.mediatheque.journal.ui.frise.routeGenerique
import fr.mediatheque.journal.ui.home.routeHome
import fr.mediatheque.journal.ui.login.LoginScreen
import fr.mediatheque.journal.ui.login.LoginViewModel
import fr.mediatheque.journal.ui.profile.LetterboxdImportViewModel
import fr.mediatheque.journal.ui.profile.SensCritiqueViewModel
import fr.mediatheque.journal.ui.profile.routeProfile
import fr.mediatheque.journal.ui.profile.routeRapportImport
import fr.mediatheque.journal.ui.profile.routeSensCritique
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.realisateur.routeFicheFilm
import fr.mediatheque.journal.ui.realisateur.routeRealisateur
import fr.mediatheque.journal.ui.search.SearchViewModel
import fr.mediatheque.journal.ui.search.routeSearch
import fr.mediatheque.journal.ui.suivis.SuivisViewModel
import fr.mediatheque.journal.ui.suivis.routeChercherSuivi
import fr.mediatheque.journal.ui.suivis.routeChoisirFilmDeSaga
import fr.mediatheque.journal.ui.suivis.routeFicheSuivi
import fr.mediatheque.journal.ui.suivis.routeSuivis

@OptIn(ExperimentalSharedTransitionApi::class)
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
            // Le jumeau `Screen.Films` (`FilmsRoute.kt`) exploite l'inverse volontairement : son
            // `LaunchedEffect(Unit)` reste dans le `Crossfade` pour recharger à chaque entrée.
            val search: SearchViewModel = viewModel(key = "search") {
                SearchViewModel(container.api, session::expire, container.recentSearches)
            }
            LaunchedEffect(nav.searchVisits) { if (nav.searchVisits > 0) search.reset() }
            // Même instance dans les deux branches (`Screen.Profile` affiche le pseudo, `Screen.SensCritique`
            // porte le formulaire) — un aller-retour doit revenir sur le pseudo qu'on vient d'y lire, pas en
            // repartir à zéro. Hoisté ici pour la même raison que `search` juste au-dessus (mineur a de la
            // revue du 14 septembre 2026) : l'email et le mot de passe saisis doivent s'effacer à la *sortie*
            // de `Screen.SensCritique`, pas à l'entrée (l'écran affiche encore un état utile — le pseudo —
            // hors visite, à la différence de `LoginScreen`) ; un `LaunchedEffect(Unit)` posé dans la branche
            // du `Crossfade` ne verrait que les entrées, jamais les sorties.
            val senscritique: SensCritiqueViewModel = viewModel(key = "senscritique") {
                SensCritiqueViewModel(container.sensCritiqueStore, container.sensCritiqueAuthClient, container.sensCritiqueSync)
            }
            // Une seule instance pour la Frise et pour la ligne « Ensuite » de l'accueil (brief du
            // 15 septembre 2026) : les deux doivent viser le même film « à voir », et ne charger le
            // journal complet qu'une fois. Indexé sur l'Activité comme `search`/`senscritique` :
            // sans clé fixe, chaque entrée sur l'accueil ou la Frise recréerait l'instance.
            val frise: FriseViewModel = viewModel(key = "frise") { FriseViewModel(container.api, session::expire) }
            // Le ticket (brief du 21 septembre 2026) : l'année de sortie d'un film tout juste
            // journalisé déclenche la relecture (`frise.relireApresCreation`), qui décide elle-même
            // si elle vaut la peine (l'année en cours seulement) — hoisté hors du `Crossfade` comme
            // `cartonTmdbId` plus bas, sans quoi un événement à un coup pourrait arriver avant que
            // la branche qui le collecte ne soit recomposée.
            //
            // Le verdict de maturité (brief du 25 septembre 2026, « le verdict de maturité se
            // relit ») partage le même signal — jumeau de `filmEnregistre`/`cartonTmdbId` plus
            // bas : un état plutôt qu'un second événement à un coup, pour que `Screen.Annee` le
            // retrouve même s'il n'était pas encore ouvert au moment de l'enregistrement. Remis à
            // `null` dès consommé (dans `routeAnnee`, `FriseRoutes.kt`, via
            // `PorteeEcrans.anneeAVerifierVerdict`) pour ne pas relancer la veille à chaque
            // réouverture ultérieure de la même année.
            val anneeAVerifierVerdictState = remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(Unit) {
                nav.ticketRelectures.collect { annee ->
                    frise.relireApresCreation(annee)
                    anneeAVerifierVerdictState.value = annee
                }
            }
            // Le journal ne se rechargeait qu'à l'entrée sur la Frise ou l'accueil (`LaunchedEffect(Unit)`
            // de `FriseRoutes.kt` et `HomeRoute.kt`), jamais en y revenant depuis le formulaire
            // (correctif du 22 septembre 2026,
            // « la fiche du Voyage se relit après un enregistrement ») : `nav.enregistrements` porte
            // tout succès du formulaire, `refreshApresEnregistrement()` recharge le journal comme
            // `refresh()` et incrémente `frise.ui.enregistrements`, que `Screen.Annee` et
            // `Screen.FicheVoyage` collectent (`FriseRoutes.kt`) pour relire leurs salles.
            LaunchedEffect(Unit) { nav.enregistrements.collect { frise.refreshApresEnregistrement() } }
            // Une seule instance pour l'écran Suivis (ses deux segments), ses fiches, les deux
            // secondes lignes « Ensuite » de l'accueil et les deux dernières lignes du Bilan du
            // profil (brief du 15 septembre 2026, généralisé aux sagas le même jour) : toutes
            // doivent viser les mêmes entités « en cours », et les filmographies ne se tirent
            // qu'une fois. La fiche, en particulier, ne recharge rien — elle lit ce que la liste a
            // déjà.
            val suivis: SuivisViewModel = viewModel(key = "suivis") {
                SuivisViewModel(container.api, session::expire)
            }
            // Un nom de réalisateur touchable partout où il s'affiche (décision 3 du brief du
            // 21 septembre 2026, « la page réalisateur ») : une seule instance, indexée sur
            // l'Activité comme `suivis` ci-dessus — son cache par film ne doit pas se vider en
            // passant d'un écran à l'autre.
            val realisateurResolveur: RealisateurResolveur = viewModel(key = "realisateur-resolveur") {
                RealisateurResolveur(container.api)
            }
            // Indexé sur l'Activité comme les autres ci-dessus (brief « importer Letterboxd »,
            // 16 septembre 2026) : `Screen.RapportImport` ne porte aucune donnée, elle relit cette
            // instance — c'est elle qui garde la requête en vol si le retour système dépile l'écran
            // pendant l'attente.
            val letterboxd: LetterboxdImportViewModel = viewModel(key = "letterboxd-import") {
                LetterboxdImportViewModel(container.api, session::expire)
            }
            var etaitSurSensCritique by remember { mutableStateOf(false) }
            LaunchedEffect(nav.current) {
                if (etaitSurSensCritique && nav.current != Screen.SensCritique) senscritique.clearCredentials()
                etaitSurSensCritique = nav.current == Screen.SensCritique
            }
            // Le Voyage (brief du 16 septembre 2026) : le `tmdb_id` posé par `nav.home(message,
            // cartonTmdbId)` après une création, pour la feuille du carton que le calque de
            // célébration ouvre (décision 4 du brief du 24 septembre 2026, « le voyage revu » — plus
            // sur l'accueil depuis ce même brief) — hoisté hors du `Crossfade` comme
            // `etaitSurSensCritique` ci-dessus, sans quoi l'événement à un coup de
            // `nav.cartonRequests` pourrait arriver avant que le calque ne soit là pour le collecter.
            var cartonTmdbId by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(Unit) { nav.cartonRequests.collect { cartonTmdbId = it } }
            // Hoisté ici : le calque de célébration (geste 9 ci-dessous) en a besoin par-dessus
            // l'écran courant — `viewModel(key = ...)` rend la même instance à chaque recomposition,
            // indexée sur l'Activité comme les autres ci-dessus.
            val cartonHome: CartonViewModel? = cartonTmdbId?.let { id ->
                viewModel(key = "carton-home-$id") { CartonViewModel(container.api, id, poll = true, session::expire) }
            }
            // La célébration d'un enregistrement (habillage du 23 septembre 2026, geste 9) : même
            // raisonnement que `cartonTmdbId` ci-dessus, hoisté hors du `Crossfade` — l'événement à
            // un coup de `nav.filmsEnregistres` doit trouver quelqu'un déjà là pour le collecter.
            var filmEnregistre by remember { mutableStateOf<FilmEnregistre?>(null) }
            LaunchedEffect(Unit) { nav.filmsEnregistres.collect { filmEnregistre = it } }
            // Les réactions favorites (point 7 de la revue du 24 septembre 2026) : calculées une
            // fois ici sur le journal déjà chargé par la Frise, pour le formulaire (création et
            // correction) — jamais une requête réseau de plus, jamais recalculées à chaque écran.
            val friseUiPourReactions by frise.ui.collectAsState()
            val reactionsFavorites = remember(friseUiPourReactions.annees) {
                Reactions.reactionsFavorites(friseUiPourReactions.annees.flatMap { it.vus }.flatMap { it.carnet.reactions })
            }
            // Le défilement survit au retour (peaufinage du 23 septembre 2026) : `stateHolder`,
            // hoisté ici comme `nav` plus haut, garde l'état sauvegardable (`rememberLazyListState`
            // et consorts) de chaque entrée de la pile pendant qu'elle est disposée par
            // l'`AnimatedContent` — sans lui, revenir en arrière rouvrirait toujours en haut de la
            // page. `pileConnue` retient l'ancienne pile pour libérer les clés des entrées qui l'ont
            // quittée (`clesLibereesParChangementDePile`, `Navigation.kt`) : un écran rouvert plus
            // tard repart donc en haut plutôt que de fuiter l'état d'une visite oubliée.
            val stateHolder = rememberSaveableStateHolder()
            var pileConnue by remember { mutableStateOf(nav.stack) }
            LaunchedEffect(nav.stack) {
                clesLibereesParChangementDePile(pileConnue, nav.stack).forEach { stateHolder.removeState(it) }
                pileConnue = nav.stack
            }
            // L'affiche partagée (geste 8) : `SharedTransitionLayout` remplace le `Box` — il se
            // comporte comme lui pour la superposition du calque du ticket plus bas — et fournit
            // la portée que `Cover` a besoin de connaître (`AfficheVolante`, `ui/Cover.kt`) pour
            // faire voler une affiche entre une grille et sa fiche.
            SharedTransitionLayout(Modifier.fillMaxSize()) {
            val sharedTransitionScope = this
            // `targetState` porte la pile entière, pas seulement `nav.current` : la lambda a ainsi
            // toujours la position exacte de l'écran qu'elle rend (`pile.lastIndex`), y compris
            // pour la branche encore affichée pendant la transition, plutôt que de relire
            // `nav.stack` au moment de la composition, déjà avancé sur la pile suivante. Le
            // commentaire plus haut sur « `Crossfade` dispose la branche quittée » reste vrai ici :
            // `AnimatedContent` en fait autant, une fois la transition finie — tout ce qui doit
            // survivre à un `pop` reste hoisté hors de cette lambda, comme avant.
            //
            // Transitions avec profondeur (peaufinage du 23 septembre 2026, geste 7) : un `push`
            // fait entrer le nouvel écran par la droite et l'ancien recule légèrement, un `pop`
            // fait l'inverse, `home()` (pile vidée, `SensTransition.Remplace`) garde le fondu seul
            // du réglage précédent. `sensDeTransition` (`Navigation.kt`) est une fonction pure,
            // testée en JVM, qui ne regarde que la taille de la pile avant et après.
            AnimatedContent(
                targetState = nav.stack,
                label = "ecran",
                transitionSpec = {
                    when (sensDeTransition(initialState, targetState)) {
                        SensTransition.Push ->
                            (slideInHorizontally(tween(250)) { largeur -> largeur / 4 } + fadeIn(tween(250)))
                                .togetherWith(slideOutHorizontally(tween(250)) { largeur -> -largeur / 8 } + fadeOut(tween(250)))
                        SensTransition.Pop ->
                            (slideInHorizontally(tween(250)) { largeur -> -largeur / 4 } + fadeIn(tween(250)))
                                .togetherWith(slideOutHorizontally(tween(250)) { largeur -> largeur / 8 } + fadeOut(tween(250)))
                        SensTransition.Remplace -> fadeIn(tween(200)).togetherWith(fadeOut(tween(200)))
                    }
                },
            ) { pile ->
                // Le contexte que chaque route reçoit (`PorteeEcrans.kt`), construit ici et pas
                // au-dessus : `this` est la portée de visibilité de *cette* branche, différente
                // pour celle qui sort et celle qui entre pendant une transition.
                val portee = PorteeEcrans(
                    container, nav, session, s.user, search, senscritique, frise, suivis,
                    realisateurResolveur, letterboxd, reactionsFavorites, anneeAVerifierVerdictState,
                    sharedTransitionScope, this,
                )
                val screen = pile.last()
                stateHolder.SaveableStateProvider(saveableKey(pile.lastIndex, screen)) {
                when (screen) {
                    Screen.Home -> portee.routeHome()
                    Screen.Search -> portee.routeSearch()
                    is Screen.Form -> portee.routeForm(screen)
                    Screen.Profile -> portee.routeProfile()
                    Screen.Films -> portee.routeFilms()
                    is Screen.Edit -> portee.routeEdit(screen)
                    Screen.SensCritique -> portee.routeSensCritique()
                    Screen.Cinema -> portee.routeCinema()
                    Screen.Frise -> portee.routeFrise()
                    is Screen.Annee -> portee.routeAnnee(screen)
                    is Screen.FicheVoyage -> portee.routeFicheVoyage(screen)
                    is Screen.Decennie -> portee.routeDecennie(screen)
                    is Screen.Generique -> portee.routeGenerique(screen)

                    Screen.Suivis -> portee.routeSuivis()
                    Screen.ChercherSuivi -> portee.routeChercherSuivi()
                    is Screen.FicheSuivi -> portee.routeFicheSuivi(screen)
                    is Screen.Realisateur -> portee.routeRealisateur(screen)
                    is Screen.FicheFilm -> portee.routeFicheFilm(screen)
                    is Screen.ChoisirFilmDeSaga -> portee.routeChoisirFilmDeSaga(screen)
                    Screen.RapportImport -> portee.routeRapportImport()
                }
                }
            }
            // Le calque du ticket (décision 2 du brief du 21 septembre 2026, « le ticket ») se pose
            // au-dessus de l'`AnimatedContent`, dans ce `Box` : il doit pouvoir s'afficher par-
            // dessus n'importe quel écran (la Frise à son ouverture, ou l'accueil juste après un
            // enregistrement), pas seulement l'un d'eux.
            TicketHote(frise)
            // La célébration d'un enregistrement (habillage du 23 septembre 2026, geste 9) : le
            // même genre de calque maison, au-dessus de tout — `nav.home(...)` a déjà vidé la pile
            // sur `Screen.Home` au moment où l'événement arrive, donc `cartonHome` (hoisté plus
            // haut) est déjà la bonne instance, la même que celle que l'accueil affiche derrière.
            filmEnregistre?.let { film ->
                FilmEnregistreCalque(
                    film = film,
                    carton = cartonHome,
                    onFermer = { filmEnregistre = null },
                )
            }
            }
        }
    }
}
