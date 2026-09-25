package fr.mediatheque.journal.ui.realisateur

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.form.CartonViewModel

/** `Screen.Realisateur` : la page d'un réalisateur, sa fiche et sa filmographie complète, suivi ou non. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeRealisateur(screen: Screen.Realisateur) {
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

/** `Screen.FicheFilm` : la fiche simple d'un film de la filmographie, hors Voyage. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeFicheFilm(screen: Screen.FicheFilm) {
    // Même clé que `Screen.Realisateur` juste au-dessus : cette instance existe
    // déjà (la fiche ne s'ouvre que depuis une affiche de cet écran-là), le
    // constructeur ci-dessous ne sert donc qu'à la signature de `viewModel`.
    val realisateurVm: RealisateurViewModel = viewModel(key = "realisateur-${screen.realisateurTmdbId}") {
        RealisateurViewModel(screen.realisateurTmdbId, container.api, session::expire)
    }
    // « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») :
    // le carton en pop-in, `poll = false` — jumeau de `Screen.Edit`.
    val cartonFicheFilm: CartonViewModel = viewModel(key = "carton-fichefilm-${screen.filmTmdbId}") {
        CartonViewModel(container.api, screen.filmTmdbId, poll = false, session::expire)
    }
    // Le journal complet se relit ici aussi (« la fiche · trois visages », 25
    // septembre 2026) : la ligne de filmographie ne porte que `vu.entry_id`, et
    // le contrat n'a pas de `GET /me/journal/{id}` — l'entrée entière, pour ses
    // réactions et « Corriger », se retrouve dans `SuivisUi.entrees`. Jumeau de
    // `routeSuivis`, d'où la page d'un réalisateur ne s'ouvre pas toujours.
    LaunchedEffect(Unit) { suivis.chargerEntrees() }
    val suivisUi by suivis.ui.collectAsState()
    FicheFilmScreen(
        realisateurVm,
        realisateurResolveur,
        filmTmdbId = screen.filmTmdbId,
        entrees = suivisUi.entrees,
        onBack = nav::pop,
        // L'affiche partagée (geste 8) : même clé que la grille d'où on vient.
        volante = AfficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-realisateur-${screen.filmTmdbId}"),
        onOuvrirForm = { nav.push(Screen.Form(it)) },
        onOuvrirRealisateur = { id -> nav.push(Screen.Realisateur(id)) },
        onCorriger = { entree -> nav.push(Screen.Edit(entree)) },
        carton = cartonFicheFilm,
    )
}
