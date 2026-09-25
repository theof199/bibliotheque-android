package fr.mediatheque.journal.ui.fiche

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.form.CartonViewModel

/** `Screen.FicheEntree` : la fiche d'une entrée du journal, avant sa correction. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeFicheEntree(screen: Screen.FicheEntree) {
    // « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») :
    // le carton en pop-in, `poll = false` — ouvrir la fiche d'un film ne doit jamais
    // en déclencher l'écriture. Jumeau de `routeEdit` (`FormRoutes.kt`), sous sa propre
    // clé : les deux écrans peuvent être dans la pile ensemble.
    val tmdbId = screen.item.media.external_id.toIntOrNull()
    val carton: CartonViewModel? = tmdbId?.let { id ->
        viewModel(key = "carton-entree-$id") { CartonViewModel(container.api, id, poll = false, session::expire) }
    }
    FicheEntreeScreen(
        item = screen.item,
        carton = carton,
        resolveur = realisateurResolveur,
        onBack = nav::pop,
        onCorriger = { nav.push(Screen.Edit(screen.item)) },
        onOuvrirRealisateur = { nav.push(Screen.Realisateur(it)) },
        // L'affiche partagée (geste 8) : la cible du vol depuis l'accueil et Mes films,
        // et le départ de celui vers la correction (`routeEdit`) — même clé partout.
        volante = AfficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-journal-${screen.item.entry.id}"),
    )
}
