package fr.mediatheque.journal.ui.home

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.barreDuBas
import fr.mediatheque.journal.ui.films.FilmsViewModel
import fr.mediatheque.journal.ui.frise.AnneeFrise
import fr.mediatheque.journal.ui.frise.toSearchResult
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import fr.mediatheque.journal.ui.suivis.entiteEnCours
import fr.mediatheque.journal.ui.suivis.formulaire

/** `Screen.Home` : la grille des jaquettes, les lignes « Ce soir » et « Ensuite ». */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeHome() {
    // Même `FilmsViewModel` que `Screen.Films` (même clé `"films"`, `FilmsRoute.kt`) :
    // l'accueil et « Mes films » sont deux présentations d'une seule source
    // (brief du 10 septembre 2026). Même piège, même remède que `Screen.Films` :
    // ce `ViewModel` est indexé sur l'Activité, donc sans ce
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
        // bouts de la paire vers « la fiche d'entrée » (`Screen.Edit`, `FormRoutes.kt`).
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
        bottomBar = { barreDuBas(Screen.Home) },
    )
}
