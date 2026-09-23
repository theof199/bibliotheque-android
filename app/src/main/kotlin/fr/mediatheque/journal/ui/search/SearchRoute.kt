package fr.mediatheque.journal.ui.search

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.frise.AnneeViewModel
import fr.mediatheque.journal.ui.frise.toSearchResult
import fr.mediatheque.journal.ui.frise.versSearchResult
import fr.mediatheque.journal.ui.suivis.SourceSuivi
import fr.mediatheque.journal.ui.suivis.entiteEnCours
import fr.mediatheque.journal.ui.suivis.formulaire

/**
 * `Screen.Search` : la recherche d'un film à journaliser. Le `SearchViewModel` est hoisté dans
 * `Root.kt` (clé `"search"`), remis à zéro par `nav.searchVisits` à chaque entrée depuis l'accueil —
 * pas ici, où un `LaunchedEffect` serait rejoué au retour du formulaire par `pop`.
 */
@Composable
fun PorteeEcrans.routeSearch() {
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
