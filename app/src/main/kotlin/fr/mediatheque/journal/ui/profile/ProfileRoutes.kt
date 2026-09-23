package fr.mediatheque.journal.ui.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.barreDuBas
import fr.mediatheque.journal.ui.suivis.SourceSuivi

/** `Screen.Profile` : le pseudo, les deux chiffres, et les cartes Bilan, Passeport, Portefeuille, Dépenses. */
@Composable
fun PorteeEcrans.routeProfile() {
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
        user,
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
        bottomBar = { barreDuBas(Screen.Profile) },
    )
}

/** `Screen.SensCritique` : la connexion SensCritique, depuis le profil (brief du 14 septembre 2026). */
@Composable
fun PorteeEcrans.routeSensCritique() {
    SensCritiqueScreen(senscritique, onBack = nav::pop)
}

/** `Screen.RapportImport` : « Import en cours… » puis le rapport de l'import Letterboxd. */
@Composable
fun PorteeEcrans.routeRapportImport() {
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
