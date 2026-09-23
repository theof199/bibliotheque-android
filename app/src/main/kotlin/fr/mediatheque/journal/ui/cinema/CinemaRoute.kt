package fr.mediatheque.journal.ui.cinema

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
    AuCineScreen(
        cinema,
        onOpenSortie = { nav.push(Screen.Form(it)) },
        onOpenSeance = { nav.push(Screen.Edit(it)) },
        bottomBar = { barreDuBas(Screen.Cinema) },
    )
}
