package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.celebrations.AnneeDansLaBoiteCalque
import fr.mediatheque.journal.ui.theme.BobineIndicateur
import java.time.LocalDate

/**
 * Le Voyage, la carte — l'écran de l'onglet « Frise ».
 *
 * Réécrit pour le delta de Léon du 25 septembre 2026 (« pavillon par pavillon », livraison 1) :
 * la pellicule serpentine et le carton-titre plein écran laissent place à une route de studio par
 * monde (`SectionMonde.kt`) — un carton-titre en tête, dix pavillons sur ses virages, une
 * marquise en pied. En tête de l'écran, le HUD : le chapitre, l'étendue de la décennie en cours,
 * les années visitées, les récompenses de festival. Le clap de l'icône marque l'année en cours et
 * claque quand elle avance.
 *
 * Livraison 1 : aucune image de fond, aucune ambiance, aucun grain — `SectionMonde` les pose déjà
 * en clair dans sa propre documentation. Le carton-titre se dessine toujours dans son état final
 * (`EtatEntree.Jouee`) : aucune entrée animée avant la livraison 3 du delta, mais le magasin des
 * mondes visités (`MondesVisitesStore`, tenu par `FriseRoutes.routeFrise`) s'écrit déjà, dès
 * qu'une section entre dans le viewport — pour que la livraison 3 n'ait qu'à brancher l'animation
 * dessus, sans reprendre la détection de visibilité.
 *
 * Livraison 3 (28 septembre 2026, §D, §G) : `decenniesVisibles` (déjà posé en livraison 1) ne
 * marque plus le magasin lui-même — il ne fait plus que dire à chaque `SectionMonde` si elle est
 * `visible`, avec `dejaEntre` (sa décennie est déjà dans `mondesVisites`) ; c'est `SectionMonde`
 * qui joue l'entrée une fois avant d'appeler `onEntreeJouee` (= `onMondeVisite`), pour que le
 * magasin se marque après l'entrée, pas avant.
 *
 * L'écran ne charge rien lui-même : `FriseViewModel` tient déjà le journal, le Plex et
 * `GET /me/voyage` pour l'accueil comme pour ici (`Root.kt`, clé « frise »).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VoyageScreen(
    vm: FriseViewModel,
    mondesVisites: Set<Int>,
    onMondeVisite: (Int) -> Unit,
    onOpenAnnee: (AnneeFrise) -> Unit,
    onOpenDecennie: (DecennieFrise) -> Unit,
    onOpenGenerique: (TamponDecennie) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val anneeActuelle = remember { LocalDate.now().year }
    val liste = rememberLazyListState()
    val density = LocalDensity.current
    val snackbar = remember { SnackbarHostState() }
    var claques by remember { mutableIntStateOf(0) }
    var decennieAllumee by remember { mutableIntStateOf(0) }
    // « Année dans la boîte » (habillage du 23 septembre 2026, geste 11) : le calque qui remplace
    // l'ancienne snackbar — nul hors célébration.
    var celebrationAnnee by remember { mutableStateOf<FrontiereAvancee?>(null) }

    val sections = remember(ui.voyage, ui.annees, ui.decennies, ui.passeport, anneeActuelle) {
        sectionsDuVoyage(ui, anneeActuelle)
    }
    val anneeEnCours = ui.voyage.anneeEnCours

    // Le magasin des mondes visités (§D du delta) : une section devient `visible` dès qu'elle
    // entre dans le viewport ; c'est `SectionMonde` qui décide, désormais (livraison 3,
    // `etatEntreeCarton`), de jouer l'entrée avant d'appeler `onMondeVisite` — plus dès la
    // visibilité elle-même, pour que le carton ait le temps de jouer son entrée avant d'être
    // marqué « déjà vu ».
    val decenniesVisibles by remember(sections) {
        derivedStateOf {
            liste.layoutInfo.visibleItemsInfo.mapNotNull { info -> (info.key as? String)?.removePrefix("section-")?.toIntOrNull() }
        }
    }

    // À l'ouverture, la liste défile jusqu'à l'année en cours, d'un coup ; un enregistrement qui
    // la fait avancer (via `vm.avancees` ci-dessous) anime la marche du clap jusqu'à sa nouvelle
    // place, sur la route de sa section.
    var anneePrecedente by remember { mutableStateOf<Int?>(null) }
    LaunchedEffect(anneeEnCours, sections) {
        val index = sections.indexOfFirst { section -> section.annees.any { it.annee == anneeEnCours } }
        if (index < 0) return@LaunchedEffect
        val offsetPx = with(density) { (76.dp + emplacement(sections[index].annees.first { it.annee == anneeEnCours }.rang).y.dp - 72.dp).toPx() }
            .coerceAtLeast(0f)
            .toInt()
        if (anneePrecedente != null && anneePrecedente != anneeEnCours) {
            // La marche du clap (geste 14) : un glissement animé de 800 ms sur une distance fixe
            // (le pas d'une rangée de la route, 120 dp) plutôt que la distance réelle jusqu'à
            // l'index — la durée du geste ne dépend donc pas de combien d'années le Voyage vient
            // de franchir. Aucune coloration des perforations ici (la route est un tracé peint,
            // plus une pellicule segment par segment) : déviation notée dans le rapport.
            val distance = with(density) { 120.dp.toPx() }
            liste.scrollToItem(index, scrollOffset = offsetPx + distance.toInt())
            liste.animateScrollBy(distance, tween(800, easing = FastOutSlowInEasing))
        } else {
            liste.scrollToItem(index, scrollOffset = offsetPx)
        }
        anneePrecedente = anneeEnCours
    }

    // L'année en cours qui avance : le clap claque, le calque « *1898* dans la boîte » joue sa
    // séquence (habillage du 23 septembre 2026, geste 11 — remplace l'ancienne snackbar).
    LaunchedEffect(Unit) {
        vm.avancees.collect { avancee ->
            claques += 1
            celebrationAnnee = avancee
        }
    }

    // Une décennie bouclée (étape 5, « les récompenses ») : allume sa marquise puis ouvre son
    // générique — découplé de la frontière ci-dessus, une décennie pouvant se boucler sans que
    // l'année en cours ne la quitte au même moment (spec du 19 septembre 2026, §6).
    LaunchedEffect(Unit) {
        vm.nouveauxTampons.collect { tampon ->
            decennieAllumee = tampon.decennie
            onOpenGenerique(tampon)
        }
    }

    Box(Modifier.fillMaxSize()) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Hud(ui.voyage, anneeActuelle)
            // Tirer pour rafraîchir (peaufinage du 23 septembre 2026, geste 12) : branché sur le
            // `refresh()` déjà appelé à l'entrée sur cet écran (`Root.kt`). `ui.loading` porte ce
            // premier chargement aussi (relecture du 23 septembre 2026) : sans `tire`,
            // l'indicateur de tirage s'afficherait à chaque entrée sur la Frise, pas seulement
            // quand on tire vraiment — il ne monte que dans `onRefresh` et retombe dès que
            // `ui.loading` redescend, quelle qu'en soit la cause.
            var tire by remember { mutableStateOf(false) }
            LaunchedEffect(ui.loading) { if (!ui.loading) tire = false }
            // La bobine qui tourne (geste 21 du complément du 23 septembre 2026 à l'habillage) : à
            // la place du rond Material par défaut, `BobineIndicateur` suit le même `state`.
            val etatTirage = rememberPullToRefreshState()
            PullToRefreshBox(
                isRefreshing = tire && ui.loading,
                onRefresh = { if (!ui.loading) { tire = true; vm.refresh() } },
                state = etatTirage,
                indicator = {
                    BobineIndicateur(
                        etatTirage,
                        isRefreshing = tire && ui.loading,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 12.dp),
                    )
                },
                modifier = Modifier.weight(1f),
            ) {
                LazyColumn(state = liste, modifier = Modifier.fillMaxSize()) {
                    items(sections, key = { section -> "section-${section.monde.decennie}" }) { section ->
                        val decennie = section.monde.decennie
                        SectionMonde(
                            section = section,
                            anneeEnCours = anneeEnCours,
                            visible = decennie in decenniesVisibles,
                            dejaEntre = decennie in mondesVisites,
                            onEntreeJouee = { onMondeVisite(decennie) },
                            claques = claques,
                            decennieAllumee = decennieAllumee,
                            onOpenAnnee = onOpenAnnee,
                            onOpenDecennie = onOpenDecennie,
                        )
                    }
                }
            }
        }
    }
    celebrationAnnee?.let { avancee ->
        AnneeDansLaBoiteCalque(
            avancee = avancee,
            recompense = recompenseDe(ui.voyage.parAnnee[avancee.anneeBouclee]?.recompense),
            onTermine = { celebrationAnnee = null },
            // Le générique de fin (décision 5 du brief du 24 septembre 2026, « le voyage revu ») :
            // simple lecture, silencieuse en cas d'échec — la ligne ne s'affiche simplement pas.
            chargerGenerique = { vm.chargerGeneriqueAnnee(avancee.anneeBouclee) },
        )
    }
    }
}

/** Le HUD : « Chapitre I · Les origines », l'étendue de la décennie, « 3 années visitées », les récompenses. */
@Composable
private fun Hud(voyage: VoyageUi, anneeActuelle: Int) {
    val visitees = voyage.parAnnee.values.count { it.visitee }
    // Le compteur du HUD s'incrémente chiffre par chiffre plutôt que de sauter d'un coup (geste
    // 14) : la même valeur cible, juste animée — un enregistrement qui fait franchir plusieurs
    // années d'un coup (rattrapage) le compte tout aussi bien, seul le trajet visuel change.
    val visiteesAnimees by animateIntAsState(targetValue = visitees, label = "visitees")
    // Vide tant qu'aucune année n'a de récompense : c'est `phraseRecompenses` elle-même qui rend
    // "" alors, sans qu'il faille un `if` de plus ici (étape 5, « les récompenses »).
    val recompenses = phraseRecompenses(voyage.parAnnee.values.mapNotNull { recompenseDe(it.recompense) })
    // L'étendue de la décennie en cours (§A du delta de Léon) : « 1930 → 1939 · » devant le compte
    // d'années visitées.
    val etendue = etendueHud(mondeDe(voyage.anneeEnCours).decennie, anneeActuelle)
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
        Text(
            chapitreDe(voyage.anneeEnCours),
            style = MaterialTheme.typography.titleLarge,
            color = mondeDe(voyage.anneeEnCours).accent,
        )
        Text(
            "$etendue · $visiteesAnimees ${if (visitees <= 1) "année visitée" else "années visitées"} · ${voyage.anneeEnCours} en cours",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (recompenses.isNotEmpty()) {
            Text(recompenses, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
