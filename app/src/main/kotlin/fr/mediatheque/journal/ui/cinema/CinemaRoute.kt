package fr.mediatheque.journal.ui.cinema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.barreDuBas

/** `Screen.Cinema` : « Au ciné », mes séances et les sorties en salle (brief du 14 septembre 2026). */
@Composable
fun PorteeEcrans.routeCinema() {
    // Nouveau `ViewModel`, indexé sur l'Activité comme les autres (jumeau de
    // `films`/`search`, `FilmsRoute.kt` et `Root.kt`) : sans ce rechargement à chaque
    // entrée, « Tes séances » et les grilles de sorties resteraient celles de la première
    // visite après l'ajout d'une séance depuis ce même écran.
    val cinema: AuCineViewModel = viewModel(key = "cinema") { AuCineViewModel(container.api, session::expire) }
    LaunchedEffect(Unit) { cinema.refresh() }
    // Le sceau or des tuiles (« le guichet », 25 septembre 2026) lit les Suivis tels que l'appli
    // les tient déjà. Ils sont rechargés à chaque entrée sur l'accueil (`HomeRoute.kt`), toujours
    // visité avant cet onglet : pas de `refresh` ici, et pas de `RealisateurResolveur` non plus
    // (un appel par tuile, jusqu'à quarante — refusé).
    val suivisUi by suivis.ui.collectAsState()
    val reperes = remember(suivisUi) { reperesSuivis(suivisUi) }
    AuCineScreen(
        cinema,
        reperes = reperes,
        onOpenSortie = { nav.push(Screen.Form(it)) },
        onOpenSeance = { nav.push(Screen.Edit(it)) },
        bottomBar = { barreDuBas(Screen.Cinema) },
    )
}
