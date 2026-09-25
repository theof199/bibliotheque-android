package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.PorteeEcrans
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.barreDuBas
import fr.mediatheque.journal.ui.form.CartonViewModel

/** `Screen.Frise` : la carte du Voyage, année par année depuis 1895. */
@Composable
fun PorteeEcrans.routeFrise() {
    // Nouveau `ViewModel` partagé avec l'accueil (même clé `"frise"`, hoisté dans `Root.kt`) :
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
        bottomBar = { barreDuBas(Screen.Frise) },
    )
}

/** `Screen.Annee` : l'année en étages — cartouche, podium, séance, salles, ticket, carnet. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeAnnee(screen: Screen.Annee) {
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

/** `Screen.FicheVoyage` : la fiche d'un film d'une salle, lue sur le même `AnneeViewModel` que l'année. */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun PorteeEcrans.routeFicheVoyage(screen: Screen.FicheVoyage) {
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
    // réussi (`nav.enregistrements`, `Root.kt`) — jamais rechargées *ici*, sur
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
        // « Corriger » (« la fiche · trois visages », 25 septembre 2026) : l'entrée
        // déjà retrouvée plus haut dans le journal de `FriseViewModel` ; le bouton
        // n'est rendu que si elle existe (`boutonsFicheVoyage`).
        onCorriger = { journalItem?.let { nav.push(Screen.Edit(it)) } },
    )
}

/** `Screen.Decennie` : le rayon d'une décennie, relu sur l'instantané de la carte. */
@Composable
fun PorteeEcrans.routeDecennie(screen: Screen.Decennie) {
    // Snapshot déjà calculé par `FriseViewModel` (jumeau de `Screen.Annee`
    // ci-dessus) : les puces année reconstruisent leur `AnneeFrise` depuis
    // `frise.ui`, seule source qui garde encore les listes de vus et d'à-voir
    // par année (`DecennieFrise.annees` n'en porte que les comptes).
    val friseUi by frise.ui.collectAsState()
    DecennieScreen(
        screen.decennie,
        onBack = nav::pop,
        onOuvrirVu = { nav.push(Screen.FicheEntree(it)) },
        onOuvrirAVoir = { nav.push(Screen.Form(it.toSearchResult())) },
        onOuvrirAnnee = { annee ->
            val groupe = friseUi.annees.firstOrNull { it.annee == annee } ?: AnneeFrise(annee, emptyList(), emptyList())
            nav.push(Screen.Annee(groupe, friseUi.voyage.parAnnee[annee]))
        },
        voyage = friseUi.voyage,
    )
}

/** `Screen.Generique` : le générique de fin d'une décennie bouclée, plein écran. */
@Composable
fun PorteeEcrans.routeGenerique(screen: Screen.Generique) {
    GeneriqueScreen(screen.tampon, user.pseudo, onFermer = nav::pop)
}
