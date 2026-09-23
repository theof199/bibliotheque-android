package fr.mediatheque.journal.ui.suivis

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.barreDuBas
import fr.mediatheque.journal.ui.search.SearchScreen
import fr.mediatheque.journal.ui.search.SearchViewModel

/** `Screen.Suivis` : ce que je suis, réalisateurs ou sagas, les deux segments dans un seul écran. */
@Composable
fun PorteeEcrans.routeSuivis() {
    // Jumeau de `Screen.Frise` (`FriseRoutes.kt`) : le `ViewModel` est partagé avec l'accueil, et
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
        bottomBar = { barreDuBas(Screen.Suivis) },
    )
}

/** `Screen.ChercherSuivi` : la recherche d'un réalisateur ou d'une saga à suivre, sur le segment affiché. */
@Composable
fun PorteeEcrans.routeChercherSuivi() {
    val source = suivis.ui.collectAsState().value.source
    // Clé qui porte la source (jumeau de `Screen.Form`, `FormRoutes.kt`) : changer de
    // segment puis rouvrir la recherche doit ouvrir une instance neuve, sur la
    // bonne source — une clé fixe rendrait celle de « Réalisateurs » à qui
    // cherche une saga.
    val chercher: ChercherSuiviViewModel = viewModel(key = "chercher-suivi:${source.name}") {
        ChercherSuiviViewModel(container.api, source, session::expire)
    }
    // Ici le `LaunchedEffect` reste dans la branche, à l'inverse de `search`
    // (`Root.kt`) : on ne revient jamais *dans* cet écran depuis un écran plus
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

/** `Screen.FicheSuivi` : la fiche d'une saga suivie (un réalisateur ouvre sa page, `RealisateurRoutes.kt`). */
@Composable
fun PorteeEcrans.routeFicheSuivi(screen: Screen.FicheSuivi) {
    FicheSuiviScreen(
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
}

/** `Screen.ChoisirFilmDeSaga` : choisir un film à ajouter à la main à une saga suivie. */
@Composable
fun PorteeEcrans.routeChoisirFilmDeSaga(screen: Screen.ChoisirFilmDeSaga) {
    // Instance propre à cet écran (jumeau de `chercher` sur
    // `Screen.ChercherSuivi` juste au-dessus), pas la `search` hoistée
    // dans `Root.kt` : celle-ci sert `Screen.Search`, remise à zéro par
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
