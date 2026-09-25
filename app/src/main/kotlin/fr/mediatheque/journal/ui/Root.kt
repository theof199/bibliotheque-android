package fr.mediatheque.journal.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
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
import fr.mediatheque.journal.ui.cinema.AuCineScreen
import fr.mediatheque.journal.ui.cinema.AuCineViewModel
import fr.mediatheque.journal.ui.films.FilmsScreen
import fr.mediatheque.journal.ui.films.FilmsViewModel
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.form.FormMode
import fr.mediatheque.journal.ui.form.FormScreen
import fr.mediatheque.journal.ui.form.FormViewModel
import fr.mediatheque.journal.ui.frise.AnneeFrise
import fr.mediatheque.journal.ui.frise.AnneeScreen
import fr.mediatheque.journal.ui.frise.AnneeViewModel
import fr.mediatheque.journal.ui.frise.DecennieScreen
import fr.mediatheque.journal.ui.frise.doitRelireApresCreation
import fr.mediatheque.journal.ui.frise.FicheVoyageScreen
import fr.mediatheque.journal.ui.frise.FriseViewModel
import fr.mediatheque.journal.ui.frise.GeneriqueScreen
import fr.mediatheque.journal.ui.frise.VoyageScreen
import fr.mediatheque.journal.ui.frise.toSearchResult
import fr.mediatheque.journal.ui.frise.versSearchResult
import fr.mediatheque.journal.ui.home.HomeScreen
import fr.mediatheque.journal.ui.login.LoginScreen
import fr.mediatheque.journal.ui.login.LoginViewModel
import fr.mediatheque.journal.ui.frise.TicketAMontrerUi
import fr.mediatheque.journal.ui.celebrations.FilmEnregistreCalque
import fr.mediatheque.journal.ui.frise.TicketCalque
import fr.mediatheque.journal.ui.profile.BilanViewModel
import fr.mediatheque.journal.ui.profile.DepensesViewModel
import fr.mediatheque.journal.ui.profile.LetterboxdImportViewModel
import fr.mediatheque.journal.ui.profile.PasseportViewModel
import fr.mediatheque.journal.ui.profile.PortefeuilleViewModel
import fr.mediatheque.journal.ui.profile.ProfileScreen
import fr.mediatheque.journal.ui.profile.ProfileViewModel
import fr.mediatheque.journal.ui.profile.RapportImportScreen
import fr.mediatheque.journal.ui.profile.SensCritiqueScreen
import fr.mediatheque.journal.ui.profile.SensCritiqueViewModel
import fr.mediatheque.journal.ui.profile.toSearchResult
import fr.mediatheque.journal.ui.realisateur.DestinationFilm
import fr.mediatheque.journal.ui.realisateur.FicheFilmScreen
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.realisateur.RealisateurScreen
import fr.mediatheque.journal.ui.realisateur.RealisateurViewModel
import fr.mediatheque.journal.ui.realisateur.destinationFilm
import fr.mediatheque.journal.ui.suivis.ChercherSuiviScreen
import fr.mediatheque.journal.ui.suivis.ChercherSuiviViewModel
import fr.mediatheque.journal.ui.suivis.FicheSuiviScreen
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import fr.mediatheque.journal.ui.suivis.SuivisScreen
import fr.mediatheque.journal.ui.suivis.SuivisViewModel
import fr.mediatheque.journal.ui.suivis.entiteEnCours
import fr.mediatheque.journal.ui.suivis.formulaire
import fr.mediatheque.journal.ui.search.SearchScreen
import fr.mediatheque.journal.ui.search.SearchViewModel

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
            // Le jumeau `Screen.Films` plus bas exploite l'inverse volontairement : son
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
            // `null` dès consommé (dans la branche `Screen.Annee`) pour ne pas relancer la veille
            // à chaque réouverture ultérieure de la même année.
            var anneeAVerifierVerdict by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(Unit) {
                nav.ticketRelectures.collect { annee ->
                    frise.relireApresCreation(annee)
                    anneeAVerifierVerdict = annee
                }
            }
            // Le journal ne se rechargeait qu'à l'entrée sur la Frise ou l'accueil (`LaunchedEffect(Unit)`
            // plus bas), jamais en y revenant depuis le formulaire (correctif du 22 septembre 2026,
            // « la fiche du Voyage se relit après un enregistrement ») : `nav.enregistrements` porte
            // tout succès du formulaire, `refreshApresEnregistrement()` recharge le journal comme
            // `refresh()` et incrémente `frise.ui.enregistrements`, que `Screen.Annee` et
            // `Screen.FicheVoyage` collectent plus bas pour relire leurs salles.
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
            // Le calque du ticket (décision 2 du brief du 21 septembre 2026, « le ticket ») se pose
            // au-dessus de l'`AnimatedContent`, dans ce `Box` : il doit pouvoir s'afficher par-
            // dessus n'importe quel écran (la Frise à son ouverture, ou l'accueil juste après un
            // enregistrement), pas seulement l'un d'eux.
            //
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
                val animatedVisibilityScope = this
                val screen = pile.last()
                stateHolder.SaveableStateProvider(saveableKey(pile.lastIndex, screen)) {
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
                        // Jumeau de `films.refresh()` ci-dessus, pour la ligne « Ensuite » (brief du
                        // 15 septembre 2026) : pas de chargement bloquant, l'accueil s'affiche tout de
                        // suite et la ligne apparaît quand `/reference/plex` a répondu.
                        LaunchedEffect(Unit) { frise.refresh() }
                        // Jumeau du précédent, pour les deux secondes lignes « Ensuite ». Il ne
                        // charge que les listes et les filmographies des deux sources : le
                        // journal complet (`entrees`) ne sert qu'aux fiches, et l'accueil n'a pas
                        // à le payer.
                        LaunchedEffect(Unit) { suivis.refresh(SourceSuivi.REALISATEURS) }
                        LaunchedEffect(Unit) { suivis.refresh(SourceSuivi.SAGAS) }
                        val friseUi by frise.ui.collectAsState()
                        val suivisUi by suivis.ui.collectAsState()
                        HomeScreen(
                            vm = films,
                            nav = nav,
                            // L'affiche partagée (geste 8) : la grille de l'accueil est un des deux
                            // bouts de la paire vers « la fiche d'entrée » (`Screen.Edit` ci-dessous).
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            ensuite = friseUi.ensuite,
                            ensuiteRealisateur = entiteEnCours(SourceSuivi.REALISATEURS, suivisUi.realisateurs.entites, suivisUi.realisateurs.filmographies),
                            ensuiteSaga = entiteEnCours(SourceSuivi.SAGAS, suivisUi.sagas.entites, suivisUi.sagas.filmographies),
                            // « Ce soir » (décision 4 du brief du 21 septembre 2026, « la séance ») :
                            // la même année que `Screen.Decennie` retrouve, avec le même repli sans
                            // vus ni à-voir si `FriseViewModel` ne l'a pas (encore) dans `annees`.
                            ceSoir = friseUi.voyage.seancePrise,
                            onOpenCeSoir = { seance ->
                                val groupe = friseUi.annees.firstOrNull { it.annee == seance.annee }
                                    ?: AnneeFrise(seance.annee, emptyList(), emptyList())
                                nav.push(Screen.Annee(groupe, friseUi.voyage.parAnnee[seance.annee]))
                            },
                            onAdd = { nav.push(Screen.Search) },
                            onFilms = { nav.push(Screen.Films) },
                            onOpen = { nav.push(Screen.Edit(it)) },
                            onOpenEnsuite = { nav.push(Screen.Form(it.toSearchResult())) },
                            onOpenEnsuiteRealisateur = { nav.push(Screen.Form(it.formulaire())) },
                            onOpenEnsuiteSaga = { nav.push(Screen.Form(it.formulaire())) },
                            bottomBar = {
                                JournalBottomBar(
                                    screen,
                                    onHome = { nav.home() },
                                    onFrise = { nav.push(Screen.Frise) },
                                    onSuivis = { nav.push(Screen.Suivis) },
                                    onCinema = { nav.push(Screen.Cinema) },
                                    onProfile = { nav.push(Screen.Profile) },
                                )
                            },
                        )
                    }
                    Screen.Search -> {
                        // Les trois sections d'avant-saisie (point 6 de la revue du 24 septembre
                        // 2026) : « tes Ensuite » relit `frise`/`suivis`, déjà chargés par l'accueil
                        // (mêmes instances, mêmes clés) ; les films non vus des salles de l'année en
                        // cours relisent la même instance d'`AnneeViewModel` que `Screen.Annee`
                        // (même formule de clé), rechargée ici si elle ne l'était pas déjà.
                        val friseUiPourRecherche by frise.ui.collectAsState()
                        val suivisUiPourRecherche by suivis.ui.collectAsState()
                        val ensuitePourRecherche = remember(friseUiPourRecherche.ensuite, suivisUiPourRecherche) {
                            listOfNotNull(
                                friseUiPourRecherche.ensuite?.toSearchResult(),
                                entiteEnCours(
                                    SourceSuivi.REALISATEURS,
                                    suivisUiPourRecherche.realisateurs.entites,
                                    suivisUiPourRecherche.realisateurs.filmographies,
                                )?.formulaire(),
                                entiteEnCours(
                                    SourceSuivi.SAGAS,
                                    suivisUiPourRecherche.sagas.entites,
                                    suivisUiPourRecherche.sagas.filmographies,
                                )?.formulaire(),
                            )
                        }
                        // « vu · 7 » (point 6) : `tmdb_id` → ma note, sur tout le journal déjà
                        // chargé par la Frise.
                        val dejaAuJournalPourRecherche = remember(friseUiPourRecherche.annees) {
                            friseUiPourRecherche.annees.flatMap { it.vus }
                                .mapNotNull { item -> item.media.external_id.toIntOrNull()?.let { it to item.entry.rating } }
                                .toMap()
                        }
                        val anneeEnCoursPourRecherche = friseUiPourRecherche.anneeEnCours
                        val anneeVmPourRecherche: AnneeViewModel? = anneeEnCoursPourRecherche?.let { annee ->
                            viewModel(key = "annee-$annee") { AnneeViewModel(container.api, annee, null, session::expire) }
                        }
                        LaunchedEffect(anneeVmPourRecherche) { anneeVmPourRecherche?.relire() }
                        val anneeUiPourRecherche = anneeVmPourRecherche?.ui?.collectAsState()?.value
                        val aVoirCetteAnneePourRecherche = remember(anneeUiPourRecherche, anneeEnCoursPourRecherche) {
                            if (anneeEnCoursPourRecherche == null || anneeUiPourRecherche == null) {
                                emptyList()
                            } else {
                                anneeUiPourRecherche.salles.flatMap { it.films }
                                    .filter { it.etat != "vu" }
                                    .map { it.versSearchResult(anneeEnCoursPourRecherche) }
                            }
                        }
                        SearchScreen(
                            search,
                            onBack = nav::pop,
                            onPick = { nav.push(Screen.Form(it)) },
                            ensuite = ensuitePourRecherche,
                            aVoirCetteAnnee = aVoirCetteAnneePourRecherche,
                            dejaAuJournal = dejaAuJournalPourRecherche,
                        )
                    }
                    is Screen.Form -> {
                        // Ce `ViewModel` est indexé sur l'Activité (jumeau du piège réglé sur
                        // `SearchViewModel.reset()` ci-dessus) : la clé ne donne pas de portée,
                        // elle nomme une case dans son magasin. Une clé fixe (`"form"`) rendrait
                        // le `FormViewModel` du premier film à tous les suivants ; l'identité du
                        // film dans la clé ouvre une case par film.
                        val form: FormViewModel = viewModel(key = "form:${screen.result.source}:${screen.result.external_id}") {
                            FormViewModel(
                                container.api,
                                FormMode.Create(screen.result, screen.date, screen.rating),
                                container.sensCritiqueSync,
                                session::expire,
                            )
                        }
                        FormScreen(
                            form,
                            nav = nav,
                            realisateurResolveur = realisateurResolveur,
                            onBack = nav::pop,
                            reactionsFavorites = reactionsFavorites,
                        )
                    }
                    Screen.Profile -> {
                        val profile: ProfileViewModel = viewModel(key = "profile") { ProfileViewModel(container.api, session::expire) }
                        // Le Bilan (brief du 15 septembre 2026) : son propre journal complet, et
                        // les deux sources suivies relues sur l'instance partagée — un
                        // rafraîchissement de plus ne coûte qu'un appel réseau chacune, comme
                        // `senscritique.refresh()` juste au-dessus.
                        val bilan: BilanViewModel = viewModel(key = "bilan") { BilanViewModel(container.api, session::expire) }
                        LaunchedEffect(Unit) { bilan.refresh() }
                        LaunchedEffect(Unit) { suivis.refresh(SourceSuivi.REALISATEURS) }
                        LaunchedEffect(Unit) { suivis.refresh(SourceSuivi.SAGAS) }
                        // Le portefeuille (brief du 21 septembre 2026, « le ticket ») charge ses
                        // données lui-même (`GET /me/voyage/tickets`), pas depuis `FriseViewModel`.
                        val portefeuille: PortefeuilleViewModel = viewModel(key = "portefeuille") {
                            PortefeuilleViewModel(container.api, session::expire)
                        }
                        LaunchedEffect(Unit) { portefeuille.refresh() }
                        // Les dépenses (décision 2 du brief du 21 septembre 2026, « les dépenses »),
                        // sous le portefeuille : même mécanique, son propre appel.
                        val depenses: DepensesViewModel = viewModel(key = "depenses") {
                            DepensesViewModel(container.api, session::expire)
                        }
                        LaunchedEffect(Unit) { depenses.refresh() }
                        // Le passeport (décision 3 du brief du 21 septembre 2026, « les
                        // récompenses ») charge ses données lui-même (`GET /me/voyage`), pas depuis
                        // `FriseViewModel` : plus jamais vide quand Profil s'ouvre en premier.
                        val passeport: PasseportViewModel = viewModel(key = "passeport") {
                            PasseportViewModel(container.api, session::expire)
                        }
                        LaunchedEffect(Unit) { passeport.refresh() }
                        val passeportUi by passeport.ui.collectAsState()
                        ProfileScreen(
                            s.user,
                            profile,
                            senscritique,
                            bilan,
                            suivis,
                            passeport = passeportUi.tampons ?: emptyList(),
                            portefeuille = portefeuille,
                            depenses = depenses,
                            onBack = nav::pop,
                            onFilms = { nav.push(Screen.Films) },
                            onSensCritique = { nav.push(Screen.SensCritique) },
                            onSignOut = session::signOut,
                            // Le tampon complet (films, dates) se construit depuis le journal déjà
                            // chargé par la Frise, ou le charge lui-même s'il manque (décision 3).
                            onOuvrirGenerique = { tampon ->
                                passeport.ouvrirGenerique(tampon.decennie, frise.ui.value.annees.flatMap { it.vus }) {
                                    nav.push(Screen.Generique(it))
                                }
                            },
                            // L'action de l'état vide du passeport (point 16 de la revue du
                            // 24 septembre 2026) : même geste que la barre du bas.
                            onOuvrirVoyage = { nav.push(Screen.Frise) },
                            onImportLetterboxd = { bytes -> letterboxd.start(bytes); nav.push(Screen.RapportImport) },
                            // « Utiliser » sur un ticket du portefeuille (décision 3) : même appel
                            // que le calque, puis `frise.refresh()` met la carte à jour — la même
                            // mécanique que `onPodiumChange`/`onTicketChange` d'`AnneeScreen`.
                            onUtiliserTicket = { annee -> portefeuille.utiliser(annee) { frise.refresh() } },
                            bottomBar = {
                                JournalBottomBar(
                                    screen,
                                    onHome = { nav.home() },
                                    onFrise = { nav.push(Screen.Frise) },
                                    onSuivis = { nav.push(Screen.Suivis) },
                                    onCinema = { nav.push(Screen.Cinema) },
                                    onProfile = { nav.push(Screen.Profile) },
                                )
                            },
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
                            bottomBar = {
                                JournalBottomBar(
                                    screen,
                                    onHome = { nav.home() },
                                    onFrise = { nav.push(Screen.Frise) },
                                    onSuivis = { nav.push(Screen.Suivis) },
                                    onCinema = { nav.push(Screen.Cinema) },
                                    onProfile = nav::pop,
                                )
                            },
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
                        // « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») :
                        // le carton en pop-in, `poll = false` — éditer un film ne doit jamais en
                        // déclencher l'écriture.
                        val cartonTmdbId = screen.item.media.external_id.toIntOrNull()
                        val carton: CartonViewModel? = cartonTmdbId?.let { id ->
                            viewModel(key = "carton-edit-$id") { CartonViewModel(container.api, id, poll = false, session::expire) }
                        }
                        FormScreen(
                            form,
                            nav = nav,
                            realisateurResolveur = realisateurResolveur,
                            onBack = nav::pop,
                            carton = carton,
                            // L'affiche partagée (geste 8) : l'autre bout de la paire ouverte
                            // depuis l'accueil ou « Mes films » — même clé qu'elles.
                            volante = AfficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-journal-${screen.item.entry.id}"),
                            reactionsFavorites = reactionsFavorites,
                        )
                    }
                    Screen.SensCritique -> SensCritiqueScreen(senscritique, onBack = nav::pop)
                    Screen.Cinema -> {
                        // Nouveau `ViewModel`, indexé sur l'Activité comme les autres (jumeau de
                        // `films`/`search` ci-dessus) : sans ce rechargement à chaque entrée, « Tes
                        // séances » et les grilles de sorties resteraient celles de la première
                        // visite après l'ajout d'une séance depuis ce même écran.
                        val cinema: AuCineViewModel = viewModel(key = "cinema") { AuCineViewModel(container.api, session::expire) }
                        LaunchedEffect(Unit) { cinema.refresh() }
                        AuCineScreen(
                            cinema,
                            onOpenSortie = { nav.push(Screen.Form(it)) },
                            onOpenSeance = { nav.push(Screen.Edit(it)) },
                            bottomBar = {
                                JournalBottomBar(
                                    screen,
                                    onHome = { nav.home() },
                                    onFrise = { nav.push(Screen.Frise) },
                                    onSuivis = { nav.push(Screen.Suivis) },
                                    onCinema = { nav.push(Screen.Cinema) },
                                    onProfile = { nav.push(Screen.Profile) },
                                )
                            },
                        )
                    }
                    Screen.Frise -> {
                        // Nouveau `ViewModel` partagé avec l'accueil (même clé `"frise"` ci-dessus) :
                        // sans ce rechargement à chaque entrée, la Frise resterait celle de la
                        // première visite après l'ajout d'un visionnage depuis l'un de ses écrans.
                        LaunchedEffect(Unit) { frise.refresh() }
                        VoyageScreen(
                            frise,
                            onOpenAnnee = { af ->
                                nav.push(Screen.Annee(af, af.annee?.let { frise.ui.value.voyage.parAnnee[it] }))
                            },
                            onOpenDecennie = { nav.push(Screen.Decennie(it)) },
                            onOpenGenerique = { nav.push(Screen.Generique(it)) },
                            bottomBar = {
                                JournalBottomBar(
                                    screen,
                                    onHome = { nav.home() },
                                    onFrise = { nav.push(Screen.Frise) },
                                    onSuivis = { nav.push(Screen.Suivis) },
                                    onCinema = { nav.push(Screen.Cinema) },
                                    onProfile = { nav.push(Screen.Profile) },
                                )
                            },
                        )
                    }
                    is Screen.Annee -> {
                        // Indexé sur le seul millésime (brief du 21 septembre 2026) : `Screen.FicheVoyage`
                        // doit retrouver la même instance depuis la même formule de clé, pour lire
                        // les salles déjà chargées plutôt que d'en tirer une copie.
                        val anneeVm: AnneeViewModel = viewModel(key = "annee-${screen.annee.annee}") {
                            AnneeViewModel(container.api, screen.annee.annee ?: 0, screen.voyage, session::expire)
                        }
                        // Relit les salles au retour du formulaire (correctif du 22 septembre 2026,
                        // « la fiche du Voyage se relit après un enregistrement ») : `relireApresEnregistrement`
                        // relit tout de suite, sans attendre la première relecture de `relire()` (qui,
                        // depuis le 25 septembre 2026, relit elle aussi toujours le back à l'ouverture,
                        // mais seulement une fois) — jumeau du `LaunchedEffect` de `Screen.FicheVoyage`
                        // plus bas.
                        val friseUiPourAnnee by frise.ui.collectAsState()
                        LaunchedEffect(friseUiPourAnnee.enregistrements) {
                            if (friseUiPourAnnee.enregistrements > 0) anneeVm.relireApresEnregistrement()
                        }
                        // Le verdict de maturité (brief du 25 septembre 2026, « le verdict de maturité
                        // se relit ») : ne guette que si le film qui vient d'être journalisé est bien
                        // celui de l'année en cours (`doitRelireApresCreation`, jumeau de la relecture
                        // du ticket sur la carte) et que c'est bien cette année-ci qu'on ouvre — sans
                        // quoi la veille tournerait pour rien à chaque ouverture de n'importe quelle
                        // année. Consommé tout de suite (`anneeAVerifierVerdict = null`) pour ne
                        // relancer la veille qu'une fois par enregistrement, jamais à chaque réouverture
                        // suivante de la même année.
                        LaunchedEffect(Unit) {
                            if (anneeAVerifierVerdict == screen.annee.annee &&
                                doitRelireApresCreation(anneeAVerifierVerdict, friseUiPourAnnee.voyage.anneeEnCours)
                            ) {
                                anneeAVerifierVerdict = null
                                anneeVm.guetterVerdict()
                            }
                        }
                        AnneeScreen(
                            screen.annee,
                            anneeVm,
                            realisateurResolveur = realisateurResolveur,
                            onBack = nav::pop,
                            // L'affiche partagée (geste 8) : la salle du Voyage est un des deux
                            // bouts de la paire vers `Screen.FicheVoyage` plus bas — même clé.
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onOpenFilm = { salleId, filmId -> nav.push(Screen.FicheVoyage(screen.annee.annee ?: 0, salleId, filmId)) },
                            // Le ticket (brief du 21 septembre 2026) : encaisser le ticket de la
                            // ligne du bas avance l'année en cours côté back sans toucher
                            // `/me/voyage`, on relit donc la carte nous-mêmes pour qu'elle soit à
                            // jour au prochain passage dessus — jumeau du podium juste en dessous.
                            onTicketChange = { frise.refresh() },
                            // Idem pour le podium (brief du 21 septembre 2026) : `frise.refresh()`
                            // relit `affiche_url`, seule chose que la carte en tire.
                            onPodiumChange = { frise.refresh() },
                            // « Je l'ai vu » sur la carte de soirée (décision 2 du brief du
                            // 21 septembre 2026, « la séance ») : le même formulaire pré-rempli que
                            // partout ailleurs dans le Voyage.
                            onOpenForm = { nav.push(Screen.Form(it)) },
                            // « Prendre » (décision 2) : `frise.refresh()` relit `seance_prise`, que
                            // la ligne « Ce soir » de l'accueil porte.
                            onSeanceChange = { frise.refresh() },
                            onOuvrirRealisateur = { id -> nav.push(Screen.Realisateur(id)) },
                        )
                    }
                    is Screen.FicheVoyage -> {
                        // Même clé que `Screen.Annee` juste au-dessus : cette instance existe déjà
                        // (la fiche ne s'ouvre que depuis une affiche de cet écran-là), le
                        // constructeur ci-dessous ne sert donc qu'à la signature de `viewModel`.
                        val anneeVm: AnneeViewModel = viewModel(key = "annee-${screen.annee}") {
                            AnneeViewModel(container.api, screen.annee, null, session::expire)
                        }
                        val anneeUi by anneeVm.ui.collectAsState()
                        val film = anneeUi.salles.firstOrNull { it.id == screen.salleId }?.films?.firstOrNull { it.id == screen.filmId }
                        // Ma note et mes réactions si je l'ai déjà vu (spec §3) : cherchées dans le
                        // journal déjà chargé par `FriseViewModel`, relu après tout enregistrement
                        // réussi (`nav.enregistrements`, plus haut) — jamais rechargées *ici*, sur
                        // cet écran lui-même.
                        val friseUi by frise.ui.collectAsState()
                        val journalItem = film?.let { f ->
                            friseUi.annees.flatMap { it.vus }.firstOrNull { it.media.external_id.toIntOrNull() == f.tmdbId }
                        }
                        // Relit la salle au retour du formulaire (correctif du 22 septembre 2026, « la
                        // fiche du Voyage se relit après un enregistrement ») — jumeau du
                        // `LaunchedEffect` de `Screen.Annee` plus haut.
                        LaunchedEffect(friseUi.enregistrements) {
                            if (friseUi.enregistrements > 0) anneeVm.relireApresEnregistrement()
                        }
                        val carton: CartonViewModel = viewModel(key = "carton-voyage-${screen.filmId}") {
                            CartonViewModel(container.api, film?.tmdbId ?: 0, poll = false, session::expire)
                        }
                        // Atteinte directement depuis une filmographie (décision 2 du brief du
                        // 21 septembre 2026, « la page réalisateur »), sans être jamais passé par
                        // `Screen.Annee`, `anneeVm` peut être une instance neuve — `relire()` la
                        // charge dans ce cas ; sur une instance déjà prête (venue d'`Screen.Annee`),
                        // elle rend la main tout de suite (jumeau du `LaunchedEffect` d'`AnneeScreen`).
                        LaunchedEffect(Unit) { anneeVm.relire() }
                        FicheVoyageScreen(
                            annee = screen.annee,
                            vm = anneeVm,
                            salleId = screen.salleId,
                            filmId = screen.filmId,
                            journalItem = journalItem,
                            carton = carton,
                            realisateurResolveur = realisateurResolveur,
                            // L'affiche partagée (geste 8) : même clé que la salle d'où on vient —
                            // absente (donc sans vol) quand on arrive directement d'une filmographie.
                            volante = AfficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-voyage-${screen.salleId}-${screen.filmId}"),
                            onBack = nav::pop,
                            onOpenForm = { nav.push(Screen.Form(it)) },
                            onPodiumChange = { frise.refresh() },
                            onOuvrirRealisateur = { id -> nav.push(Screen.Realisateur(id)) },
                        )
                    }
                    is Screen.Decennie -> {
                        // Snapshot déjà calculé par `FriseViewModel` (jumeau de `Screen.Annee`
                        // ci-dessus) : les puces année reconstruisent leur `AnneeFrise` depuis
                        // `frise.ui`, seule source qui garde encore les listes de vus et d'à-voir
                        // par année (`DecennieFrise.annees` n'en porte que les comptes).
                        val friseUi by frise.ui.collectAsState()
                        DecennieScreen(
                            screen.decennie,
                            onBack = nav::pop,
                            onOuvrirVu = { nav.push(Screen.Edit(it)) },
                            onOuvrirAVoir = { nav.push(Screen.Form(it.toSearchResult())) },
                            onOuvrirAnnee = { annee ->
                                val groupe = friseUi.annees.firstOrNull { it.annee == annee } ?: AnneeFrise(annee, emptyList(), emptyList())
                                nav.push(Screen.Annee(groupe, friseUi.voyage.parAnnee[annee]))
                            },
                            voyage = friseUi.voyage,
                        )
                    }
                    is Screen.Generique -> GeneriqueScreen(screen.tampon, s.user.pseudo, onFermer = nav::pop)

                    Screen.Suivis -> {
                        // Jumeau de `Screen.Frise` : le `ViewModel` est partagé avec l'accueil, et
                        // sans ce rechargement à chaque entrée, les deux segments resteraient ceux
                        // de la première visite après un ajout ou un visionnage — les deux sources,
                        // pas seulement celle affichée, pour que changer de segment ne montre
                        // jamais une liste jamais chargée.
                        LaunchedEffect(Unit) { suivis.refresh(SourceSuivi.REALISATEURS) }
                        LaunchedEffect(Unit) { suivis.refresh(SourceSuivi.SAGAS) }
                        // Le journal complet se tire ici et pas sur l'accueil : il ne sert qu'à
                        // ouvrir la correction d'un film vu depuis une fiche, et la fiche ne
                        // s'empile que depuis cet écran.
                        LaunchedEffect(Unit) { suivis.chargerEntrees() }
                        SuivisScreen(
                            suivis,
                            onAjouter = { nav.push(Screen.ChercherSuivi) },
                            // Un réalisateur ouvre sa page (décision 4 du brief du 21 septembre
                            // 2026, « la page réalisateur ») ; une saga garde sa fiche existante.
                            onOuvrir = { source, tmdbId ->
                                if (source == SourceSuivi.REALISATEURS) {
                                    nav.push(Screen.Realisateur(tmdbId))
                                } else {
                                    nav.push(Screen.FicheSuivi(source, tmdbId))
                                }
                            },
                            bottomBar = {
                                JournalBottomBar(
                                    screen,
                                    onHome = { nav.home() },
                                    onFrise = { nav.push(Screen.Frise) },
                                    onSuivis = { nav.push(Screen.Suivis) },
                                    onCinema = { nav.push(Screen.Cinema) },
                                    onProfile = { nav.push(Screen.Profile) },
                                )
                            },
                        )
                    }
                    Screen.ChercherSuivi -> {
                        val source = suivis.ui.collectAsState().value.source
                        // Clé qui porte la source (jumeau de `Screen.Form` plus haut) : changer de
                        // segment puis rouvrir la recherche doit ouvrir une instance neuve, sur la
                        // bonne source — une clé fixe rendrait celle de « Réalisateurs » à qui
                        // cherche une saga.
                        val chercher: ChercherSuiviViewModel = viewModel(key = "chercher-suivi:${source.name}") {
                            ChercherSuiviViewModel(container.api, source, session::expire)
                        }
                        // Ici le `LaunchedEffect` reste dans la branche, à l'inverse de `search`
                        // plus haut : on ne revient jamais *dans* cet écran depuis un écran plus
                        // profond — choisir une entité referme la recherche par un `pop` vers la
                        // liste. Chaque entrée est donc une entrée depuis la liste, et doit
                        // repartir d'un champ vide.
                        LaunchedEffect(Unit) { chercher.reset() }
                        ChercherSuiviScreen(
                            chercher,
                            source,
                            onBack = nav::pop,
                            // L'ajout part sur le `ViewModel` de la liste, pas sur celui de la
                            // recherche : c'est lui qui tient la liste à rafraîchir et le
                            // « Ajouté » à montrer. Son `viewModelScope` est celui de l'Activité,
                            // donc le `pop` immédiat ne coupe pas la requête en vol.
                            onPick = { suivis.ajouter(source, it.tmdbId); nav.pop() },
                        )
                    }
                    is Screen.FicheSuivi -> FicheSuiviScreen(
                        suivis,
                        source = screen.source,
                        tmdbId = screen.tmdbId,
                        onBack = nav::pop,
                        onOuvrirVu = { nav.push(Screen.Edit(it)) },
                        onOuvrirAVoir = { nav.push(Screen.Form(it)) },
                        onSupprimer = { suivis.retirer(screen.source, screen.tmdbId); nav.pop() },
                        // Le bouton n'est rendu que sur une saga (`FicheSuiviScreen`) : passer le
                        // même `onAjouterFilm` sur une fiche de réalisateur ne fait donc jamais rien.
                        onAjouterFilm = { nav.push(Screen.ChoisirFilmDeSaga(screen.tmdbId)) },
                    )
                    is Screen.Realisateur -> {
                        val realisateurVm: RealisateurViewModel = viewModel(key = "realisateur-${screen.tmdbId}") {
                            RealisateurViewModel(screen.tmdbId, container.api, session::expire)
                        }
                        LaunchedEffect(Unit) { realisateurVm.charger() }
                        RealisateurScreen(
                            realisateurVm,
                            realisateurResolveur,
                            onBack = nav::pop,
                            // L'affiche partagée (geste 8) : la grille de la filmographie est un des
                            // deux bouts de la paire vers `Screen.FicheFilm` plus bas — même clé.
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            // Le tap sur une affiche (décision 2 du brief du 21 septembre 2026, «
                            // la page réalisateur ») : la fiche du Voyage si le film y a une ligne,
                            // sinon la fiche simple de ce même écran.
                            onOuvrirFilm = { film ->
                                when (val destination = destinationFilm(film)) {
                                    is DestinationFilm.Voyage ->
                                        nav.push(Screen.FicheVoyage(destination.annee, destination.salleId, destination.filmId))
                                    is DestinationFilm.Simple ->
                                        nav.push(Screen.FicheFilm(screen.tmdbId, destination.film.tmdb_id))
                                }
                            },
                        )
                    }
                    is Screen.FicheFilm -> {
                        // Même clé que `Screen.Realisateur` juste au-dessus : cette instance existe
                        // déjà (la fiche ne s'ouvre que depuis une affiche de cet écran-là), le
                        // constructeur ci-dessous ne sert donc qu'à la signature de `viewModel`.
                        val realisateurVm: RealisateurViewModel = viewModel(key = "realisateur-${screen.realisateurTmdbId}") {
                            RealisateurViewModel(screen.realisateurTmdbId, container.api, session::expire)
                        }
                        // « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») :
                        // le carton en pop-in, `poll = false` — jumeau de `Screen.Edit` ci-dessus.
                        val cartonFicheFilm: CartonViewModel = viewModel(key = "carton-fichefilm-${screen.filmTmdbId}") {
                            CartonViewModel(container.api, screen.filmTmdbId, poll = false, session::expire)
                        }
                        FicheFilmScreen(
                            realisateurVm,
                            realisateurResolveur,
                            filmTmdbId = screen.filmTmdbId,
                            onBack = nav::pop,
                            // L'affiche partagée (geste 8) : même clé que la grille d'où on vient.
                            volante = AfficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-realisateur-${screen.filmTmdbId}"),
                            onOuvrirForm = { nav.push(Screen.Form(it)) },
                            onOuvrirRealisateur = { id -> nav.push(Screen.Realisateur(id)) },
                            carton = cartonFicheFilm,
                        )
                    }
                    is Screen.ChoisirFilmDeSaga -> {
                        // Instance propre à cet écran (jumeau de `chercher` sur
                        // `Screen.ChercherSuivi` juste au-dessus), pas la `search` hoistée
                        // plus haut : celle-ci sert `Screen.Search`, remise à zéro par
                        // `nav.searchVisits`, un mécanisme qu'il aurait fallu étendre à cet
                        // écran pour la partager sans risquer une requête ou un texte résiduel
                        // d'une autre visite.
                        val choisir: SearchViewModel = viewModel(key = "choisir-film-saga") {
                            SearchViewModel(container.api, session::expire)
                        }
                        // Comme `chercher` ci-dessus : on ne revient jamais *dans* cet écran
                        // depuis un écran plus profond, chaque entrée repart donc d'un champ vide.
                        LaunchedEffect(Unit) { choisir.reset() }
                        SearchScreen(
                            choisir,
                            onBack = nav::pop,
                            // L'ajout part sur `suivis`, pas sur `choisir` : c'est lui qui tient
                            // la filmographie à rafraîchir et le bandeau à montrer, et son
                            // `viewModelScope` (celui de l'Activité) survit au `pop` immédiat.
                            onPick = { result ->
                                result.external_id.toIntOrNull()?.let { suivis.ajouterFilm(screen.tmdbId, it) }
                                nav.pop()
                            },
                        )
                    }
                    Screen.RapportImport -> {
                        val letterboxdUi by letterboxd.ui.collectAsState()
                        RapportImportScreen(
                            letterboxdUi,
                            onBack = nav::pop,
                            onCandidat = { candidat, ligne ->
                                val (date, rating) = letterboxd.prefillFor(ligne)
                                nav.push(Screen.Form(candidat.toSearchResult(), date, rating))
                            },
                            onTermine = nav::pop,
                        )
                    }
                }
                }
            }
            // Nourri par `FriseViewModel` (décision 2) : dès que `ticketAMontrer` est non nul, le
            // calque s'affiche par-dessus l'écran courant, quel qu'il soit. « Garder » et
            // « Utiliser maintenant » ferment tous deux le calque (`ticketAMontrer` retombe à
            // `null` côté `FriseViewModel`, jamais ici) — le back ne le renvoie plus ensuite.
            val friseUiPourTicket by frise.ui.collectAsState()
            // Le calque est un dialogue maison, pas un `AlertDialog` (peaufinage du 23 septembre
            // 2026, geste 11) : `AnimatedVisibility` (fondu + échelle) lui donne une entrée et une
            // sortie, plutôt que d'apparaître net. `dernierTicket` garde le dernier ticket connu
            // pendant que `ticketAMontrer` est déjà retombé à `null` : sans lui, le contenu
            // disparaîtrait d'un coup au milieu de la sortie animée.
            var dernierTicket by remember { mutableStateOf<TicketAMontrerUi?>(null) }
            LaunchedEffect(friseUiPourTicket.voyage.ticketAMontrer) {
                friseUiPourTicket.voyage.ticketAMontrer?.let { dernierTicket = it }
            }
            AnimatedVisibility(
                visible = friseUiPourTicket.voyage.ticketAMontrer != null,
                enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
                exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200)),
            ) {
                dernierTicket?.let { ticket ->
                    TicketCalque(
                        ticket = ticket,
                        onUtiliser = { frise.utiliserTicketAMontrer(ticket.annee) },
                        onGarder = { frise.garderTicket(ticket.annee) },
                    )
                }
            }
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
