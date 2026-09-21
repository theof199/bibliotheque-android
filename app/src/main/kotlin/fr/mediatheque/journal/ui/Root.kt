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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.AppContainer
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
import fr.mediatheque.journal.ui.frise.FicheVoyageScreen
import fr.mediatheque.journal.ui.frise.FriseViewModel
import fr.mediatheque.journal.ui.frise.GeneriqueScreen
import fr.mediatheque.journal.ui.frise.VoyageScreen
import fr.mediatheque.journal.ui.frise.toSearchResult
import fr.mediatheque.journal.ui.home.HomeScreen
import fr.mediatheque.journal.ui.login.LoginScreen
import fr.mediatheque.journal.ui.login.LoginViewModel
import fr.mediatheque.journal.ui.profile.BilanViewModel
import fr.mediatheque.journal.ui.profile.LetterboxdImportViewModel
import fr.mediatheque.journal.ui.profile.ProfileScreen
import fr.mediatheque.journal.ui.profile.ProfileViewModel
import fr.mediatheque.journal.ui.profile.RapportImportScreen
import fr.mediatheque.journal.ui.profile.SensCritiqueScreen
import fr.mediatheque.journal.ui.profile.SensCritiqueViewModel
import fr.mediatheque.journal.ui.profile.toSearchResult
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
            // Une seule instance pour l'écran Suivis (ses deux segments), ses fiches, les deux
            // secondes lignes « Ensuite » de l'accueil et les deux dernières lignes du Bilan du
            // profil (brief du 15 septembre 2026, généralisé aux sagas le même jour) : toutes
            // doivent viser les mêmes entités « en cours », et les filmographies ne se tirent
            // qu'une fois. La fiche, en particulier, ne recharge rien — elle lit ce que la liste a
            // déjà.
            val suivis: SuivisViewModel = viewModel(key = "suivis") {
                SuivisViewModel(container.api, session::expire)
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
            // cartonTmdbId)` après une création, pour la carte « Et pendant ce temps… » de
            // l'accueil — hoisté hors du `Crossfade` comme `etaitSurSensCritique` ci-dessus, sans
            // quoi l'événement à un coup de `nav.cartonRequests` pourrait arriver avant que
            // `Screen.Home` ne soit recomposé pour le collecter.
            var cartonTmdbId by remember { mutableStateOf<Int?>(null) }
            LaunchedEffect(Unit) { nav.cartonRequests.collect { cartonTmdbId = it } }
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
                        // Le Voyage : une instance par film demandé, jamais réutilisée pour un
                        // suivant (clé sur le `tmdb_id`) — `cartonTmdbId` retombe à `null` quand la
                        // carte se ferme, ce qui la démonte plutôt que de la garder en mémoire.
                        val carton: CartonViewModel? = cartonTmdbId?.let { id ->
                            viewModel(key = "carton-home-$id") { CartonViewModel(container.api, id, poll = true, session::expire) }
                        }
                        HomeScreen(
                            vm = films,
                            nav = nav,
                            ensuite = friseUi.ensuite,
                            ensuiteRealisateur = entiteEnCours(SourceSuivi.REALISATEURS, suivisUi.realisateurs.entites, suivisUi.realisateurs.filmographies),
                            ensuiteSaga = entiteEnCours(SourceSuivi.SAGAS, suivisUi.sagas.entites, suivisUi.sagas.filmographies),
                            carton = carton,
                            onCartonDismiss = { cartonTmdbId = null },
                            onAdd = { nav.push(Screen.Search) },
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
                    Screen.Search -> SearchScreen(search, onBack = nav::pop, onPick = { nav.push(Screen.Form(it)) })
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
                        FormScreen(form, nav = nav, onBack = nav::pop)
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
                        // Le passeport (brief du 16 septembre 2026, phase 2) se lit sur l'instance
                        // partagée de `FriseViewModel`, déjà chargée par l'accueil : aucun appel
                        // réseau de plus pour le profil, `BilanViewModel` tirant déjà le journal
                        // complet de son côté.
                        val friseUi by frise.ui.collectAsState()
                        ProfileScreen(
                            s.user,
                            profile,
                            senscritique,
                            bilan,
                            suivis,
                            passeport = friseUi.passeport,
                            onBack = nav::pop,
                            onFilms = { nav.push(Screen.Films) },
                            onSensCritique = { nav.push(Screen.SensCritique) },
                            onSignOut = session::signOut,
                            onOuvrirGenerique = { nav.push(Screen.Generique(it)) },
                            onImportLetterboxd = { bytes -> letterboxd.start(bytes); nav.push(Screen.RapportImport) },
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
                        // Le Voyage (brief du 16 septembre 2026) : la carte « Et pendant ce
                        // temps… » en bas de la correction, silencieuse tant que le carton n'est
                        // pas prêt (`poll = false` — éditer un film ne doit jamais en déclencher
                        // l'écriture, ni afficher « le chroniqueur arrive »).
                        val cartonTmdbId = screen.item.media.external_id.toIntOrNull()
                        val carton: CartonViewModel? = cartonTmdbId?.let { id ->
                            viewModel(key = "carton-edit-$id") { CartonViewModel(container.api, id, poll = false, session::expire) }
                        }
                        FormScreen(form, nav = nav, onBack = nav::pop, carton = carton)
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
                        AnneeScreen(
                            screen.annee,
                            anneeVm,
                            onBack = nav::pop,
                            onOpenFilm = { salleId, filmId -> nav.push(Screen.FicheVoyage(screen.annee.annee ?: 0, salleId, filmId)) },
                            // Provisoire (spec du 19 septembre 2026, §5, §8) : la route qui avance
                            // l'année en cours ne touche pas `/me/voyage`, on relit donc la carte
                            // nous-mêmes pour qu'elle soit à jour au prochain passage dessus.
                            onAnneeSuivante = { frise.refresh() },
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
                        // journal déjà chargé par `FriseViewModel`, jamais rechargées ici.
                        val friseUi by frise.ui.collectAsState()
                        val journalItem = film?.let { f ->
                            friseUi.annees.flatMap { it.vus }.firstOrNull { it.media.external_id.toIntOrNull() == f.tmdbId }
                        }
                        val carton: CartonViewModel = viewModel(key = "carton-voyage-${screen.filmId}") {
                            CartonViewModel(container.api, film?.tmdbId ?: 0, poll = false, session::expire)
                        }
                        FicheVoyageScreen(
                            annee = screen.annee,
                            vm = anneeVm,
                            salleId = screen.salleId,
                            filmId = screen.filmId,
                            journalItem = journalItem,
                            carton = carton,
                            onBack = nav::pop,
                            onOpenForm = { nav.push(Screen.Form(it)) },
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
                            onOuvrir = { source, tmdbId -> nav.push(Screen.FicheSuivi(source, tmdbId)) },
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
    }
}
