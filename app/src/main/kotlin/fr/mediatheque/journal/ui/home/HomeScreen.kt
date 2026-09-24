package fr.mediatheque.journal.ui.home

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.EntreeEnCascade
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.Navigator
import fr.mediatheque.journal.ui.rememberPorteCascade
import fr.mediatheque.journal.ui.afficheVolante
import fr.mediatheque.journal.ui.voler
import fr.mediatheque.journal.ui.films.FilmsViewModel
import fr.mediatheque.journal.ui.form.CartonCard
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.frise.SeancePriseUi
import fr.mediatheque.journal.ui.frise.texteCeSoir
import fr.mediatheque.journal.ui.suivis.EnCours
import fr.mediatheque.journal.ui.suivis.titreEtAnnee
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.BobineIndicateur
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.theme.Perforations
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Demandé par le propriétaire le 10 septembre 2026, après le premier essai sur téléphone :
 * l'accueil montre les films vus, jaquettes seules, du plus récent au plus ancien, au-dessus
 * du bouton « Ajouter un film » qui descend en bas. Le `vm` est le même `FilmsViewModel` que
 * « Mes films » (clé `"films"` dans `Root.kt`) : une seule source, deux présentations.
 *
 * Revu le 24 septembre 2026 (point 5) : un unique `LazyVerticalGrid` porte tout l'écran, l'en-tête
 * et le carrousel « Ensuite » compris (des items en pleine largeur, `GridItemSpan(maxLineSpan)`,
 * avant les cellules de la grille elle-même) — avant cette revue, seule la grille défilait, dans
 * une zone à elle, sous un bloc fixe (en-tête, cartes « Ensuite » empilées) qui ne bougeait jamais.
 * « Ajouter un film » devient un bouton rond flottant du `Scaffold` plutôt qu'un enfant du
 * `Column` : il n'a donc plus besoin d'être hors du flux de défilement pour rester visible.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FilmsViewModel,
    nav: Navigator,
    onAdd: () -> Unit,
    onOpen: (JournalItem) -> Unit,
    bottomBar: @Composable () -> Unit,
    /** « Tout voir », au-dessus de la grille (point 5) : l'entrée de « Mes films » depuis l'accueil, jusque-là perdue. */
    onFilms: () -> Unit = {},
    /** Le plus ancien film à voir sur le Plex (brief du 15 septembre 2026) — nul tant qu'il n'y a rien à voir, ou que `/reference/plex` n'a pas encore répondu : pas de chargement bloquant, la ligne apparaît seule. */
    ensuite: PlexFilm? = null,
    onOpenEnsuite: (PlexFilm) -> Unit = {},
    /** Le réalisateur en cours et son prochain film (brief du 15 septembre 2026) — même règle : nul tant qu'aucun réalisateur suivi n'a de film à voir, ou que les filmographies n'ont pas répondu. */
    ensuiteRealisateur: EnCours? = null,
    onOpenEnsuiteRealisateur: (EnCours) -> Unit = {},
    /** La saga en cours et son prochain film (brief du 15 septembre 2026, généralisé le même jour) — même règle, même composant que le réalisateur en cours ci-dessus. */
    ensuiteSaga: EnCours? = null,
    onOpenEnsuiteSaga: (EnCours) -> Unit = {},
    /** La séance prise (décision 4 du brief du 21 septembre 2026, « la séance ») — même règle que les lignes ci-dessus : nulle tant que `/me/voyage` n'a pas répondu, ou que rien n'est pris. Disparaît d'elle-même quand le back la rend nulle. */
    ceSoir: SeancePriseUi? = null,
    onOpenCeSoir: (SeancePriseUi) -> Unit = {},
    /** Le Voyage (brief du 16 septembre 2026) : la carte « Et pendant ce temps… » sous le bandeau, après une création. Nulle hors de cette fenêtre. */
    carton: CartonViewModel? = null,
    onCartonDismiss: () -> Unit = {},
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : l'accueil est un des deux
    // bouts de la paire vers « la fiche d'entrée » (`Screen.Edit`, `Root.kt`).
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    // Clé fixe : `nav.messages` est un événement à un coup (revue de la vague finale,
    // Critique 1). Une clé qui bougerait à chaque message annulerait la snackbar en cours
    // avant ses deux secondes, comme le faisait `LaunchedEffect(message)` avant elle.
    LaunchedEffect(Unit) {
        nav.messages.collect { message ->
            // Deux secondes (design §6), pas la durée Material par défaut : `showBriefly`
            // (décision 3 de la tâche 5) referme elle-même la snackbar après le délai.
            snackbar.showBriefly(message)
        }
    }
    // La cascade d'entrée (habillage du 23 septembre 2026, geste 8) : posée une fois ici, au
    // sommet de l'écran — jamais au défilement ni à un retour sur cet écran resté dans la pile.
    val porteCascade = rememberPorteCascade()
    val grille = rememberLazyGridState()
    // Jumeau de `FilmsScreen` : charger la suite quand la dernière ligne visible approche de la fin.
    LaunchedEffect(grille) {
        snapshotFlow { grille.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { index -> if (index != null && index >= ui.items.size - 5) vm.loadMore() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        // Le bouton rond flottant (point 5) : corail, icône `plus`, en bas à droite — remplace le
        // bouton pleine largeur qui vivait au bas de la colonne, hors du flux de défilement.
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { IconeTabler("plus", "Ajouter un film") }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = {
            // 84 dp : le bouton rond (56 dp) plus sa marge (16 dp) plus un peu d'air, pour que la
            // snackbar ne tombe pas dessus.
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 84.dp)) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding)) {
            // Trois colonnes, 8 dp d'écart (grille du design §4) : `Cover` prend une largeur et
            // une hauteur fixes, pas un modificateur élastique, donc la taille d'une jaquette se
            // déduit ici de la largeur disponible (moins les 16 dp de marge de chaque bord du
            // `contentPadding` posé sur la grille plus bas) plutôt que d'être posée dans `Cover`
            // lui-même.
            val ecart = 8.dp
            val largeurJaquette = (maxWidth - 32.dp - ecart * 2) / 3
            val hauteurJaquette = largeurJaquette * 1.5f

            var tire by remember { mutableStateOf(false) }
            LaunchedEffect(ui.loading) { if (!ui.loading) tire = false }
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
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = grille,
                    contentPadding = PaddingValues(16.dp),
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(ecart),
                    verticalArrangement = Arrangement.spacedBy(ecart),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Column {
                            // L'en-tête (point 5) : le titre de l'appli en Fraunces (`titleLarge`,
                            // design §3), au-dessus des perforations — absent avant cette revue.
                            Text(
                                "Journal",
                                style = MaterialTheme.typography.titleLarge,
                                modifier = Modifier.padding(bottom = 8.dp),
                            )
                            Perforations(modifier = Modifier.padding(bottom = 12.dp))
                        }
                    }
                    // Le Voyage (brief du 16 septembre 2026) : « sous le bandeau » — juste après le
                    // « Enregistré » de la snackbar — la carte du film qu'on vient de journaliser, tant
                    // qu'elle existe (`carton` nul en dehors de cette fenêtre, `CartonCard` muette tant
                    // que le chroniqueur n'est pas configuré côté back).
                    carton?.let { vm ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            val cartonUi by vm.ui.collectAsState()
                            CartonCard(
                                cartonUi,
                                attente = true,
                                onDismiss = onCartonDismiss,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    // « Ce soir » (décision 4 du brief du 21 septembre 2026, « la séance ») : au-dessus
                    // d'« Ensuite », même gabarit que ses cartes — chargement non bloquant, comme le
                    // carrousel qui suit.
                    ceSoir?.let { seance ->
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LigneEnsuite(
                                coverUrl = seance.longCoverUrl,
                                titreAffiche = seance.longTitre,
                                libelle = "Ce soir",
                                titre = texteCeSoir(seance),
                                onClick = { onOpenCeSoir(seance) },
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    // Le carrousel « Ensuite » (point 5) : une carte de haut, jusqu'à trois pages
                    // (Plex, réalisateur en cours, saga en cours) qui s'enclenchent au défilement,
                    // avec des points de position — remplace les trois cartes empilées d'avant cette
                    // revue. Pas de chargement bloquant : chaque source apparaît quand elle répond,
                    // ou jamais si elle n'a rien à proposer (`cartesEnsuite`, fonction pure).
                    val cartes = cartesEnsuite(ensuite, ensuiteRealisateur, ensuiteSaga)
                    if (cartes.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            CarrouselEnsuite(
                                cartes = cartes,
                                onOpenEnsuite = onOpenEnsuite,
                                onOpenEnsuiteRealisateur = onOpenEnsuiteRealisateur,
                                onOpenEnsuiteSaga = onOpenEnsuiteSaga,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    ui.error?.let {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            ErrorBlock(
                                it.message ?: "",
                                retryable = it.retryable,
                                onRetry = vm::loadMore,
                                modifier = Modifier.padding(bottom = 12.dp),
                            )
                        }
                    }
                    // « Derniers vus » et « Tout voir » (point 5) : le lien vers « Mes films »,
                    // aujourd'hui perdu (aucune entrée vers cet écran depuis l'accueil) — `onFilms`
                    // pousse `Screen.Films` (`Root.kt`), comme le fait déjà le profil.
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Derniers vus", style = MaterialTheme.typography.titleMedium)
                            TextButton(onClick = onFilms) { Text("Tout voir") }
                        }
                    }
                    if (ui.items.isEmpty() && ui.endReached) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(Modifier.fillMaxWidth().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                                Text(
                                    "Aucun film pour l’instant.",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    } else {
                        itemsIndexed(ui.items, key = { _, item -> item.entry.id }) { index, item ->
                            // La grille se retasse (geste 3 du peaufinage du 23 septembre 2026) au
                            // lieu de sauter quand un film change de place ou disparaît. La cascade
                            // d'entrée (geste 8 de l'habillage du 23 septembre 2026) l'habille en
                            // plus, à la première composition de l'écran seulement.
                            EntreeEnCascade(index, porteCascade, Modifier.animateItem().clickable { onOpen(item) }) { modifier ->
                            Box(modifier) {
                                Cover(
                                    item.media.cover_url,
                                    item.media.title,
                                    largeurJaquette,
                                    hauteurJaquette,
                                    // L'affiche partagée (geste 8) : même clé que la fiche d'entrée.
                                    // Le cadre or 35 % (habillage du 23 septembre 2026, geste 4) se
                                    // pose sur ce même modificateur, avant la taille et la découpe
                                    // posées par `Cover` lui-même.
                                    modifier = Modifier
                                        .voler(
                                            afficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-journal-${item.entry.id}"),
                                        )
                                        .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), MaterialTheme.shapes.small),
                                )
                                item.entry.rating?.let { note ->
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .size(22.dp)
                                            .background(MaterialTheme.colorScheme.background.copy(alpha = 0.85f), CircleShape)
                                            .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                                            // Design §8 : une pastille dit « Note {n} sur 10 », pas
                                            // le chiffre nu que `Text` donnerait seul à TalkBack
                                            // (relecture, correction 3).
                                            .clearAndSetSemantics { contentDescription = "Note $note sur 10" },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(
                                            "$note",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.secondary,
                                        )
                                    }
                                }
                            }
                            }
                        }
                        if (ui.loading) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Une carte du carrousel « Ensuite » (point 5) : laquelle des trois sources — Plex, réalisateur en
 * cours, saga en cours — jamais mêlées entre elles, `cartesEnsuite` (fonction pure, testée) décide
 * lesquelles existent et dans quel ordre.
 */
sealed interface CarteEnsuite {
    data class Plex(val film: PlexFilm) : CarteEnsuite
    data class Realisateur(val encours: EnCours) : CarteEnsuite
    data class Saga(val encours: EnCours) : CarteEnsuite
}

/**
 * Les pages du carrousel « Ensuite », dans l'ordre Plex puis réalisateur puis saga — fonction pure,
 * testée en JVM (`HomeScreenTest.kt`) : chaque source n'y figure que si elle a quelque chose à
 * proposer, jamais un `null` glissé dans la liste.
 */
fun cartesEnsuite(plex: PlexFilm?, realisateur: EnCours?, saga: EnCours?): List<CarteEnsuite> =
    listOfNotNull(
        plex?.let { CarteEnsuite.Plex(it) },
        realisateur?.let { CarteEnsuite.Realisateur(it) },
        saga?.let { CarteEnsuite.Saga(it) },
    )

/** Le carrousel : une carte de haut, qui s'enclenche au défilement, avec des points de position quand il y a plus d'une page. */
@Composable
private fun CarrouselEnsuite(
    cartes: List<CarteEnsuite>,
    onOpenEnsuite: (PlexFilm) -> Unit,
    onOpenEnsuiteRealisateur: (EnCours) -> Unit,
    onOpenEnsuiteSaga: (EnCours) -> Unit,
    modifier: Modifier = Modifier,
) {
    val etat = rememberPagerState(pageCount = { cartes.size })
    Column(modifier) {
        HorizontalPager(state = etat, modifier = Modifier.fillMaxWidth().height(112.dp)) { page ->
            when (val carte = cartes[page]) {
                is CarteEnsuite.Plex -> LigneEnsuite(
                    coverUrl = carte.film.cover_url,
                    titreAffiche = carte.film.title,
                    libelle = "Ensuite",
                    titre = carte.film.year?.let { annee -> "${carte.film.title} ($annee)" } ?: carte.film.title,
                    onClick = { onOpenEnsuite(carte.film) },
                )
                is CarteEnsuite.Realisateur -> LigneEnsuite(
                    coverUrl = carte.encours.prochain.cover_url,
                    titreAffiche = carte.encours.prochain.title,
                    libelle = "Ensuite · ${carte.encours.entite.nom}",
                    titre = titreEtAnnee(carte.encours.prochain),
                    onClick = { onOpenEnsuiteRealisateur(carte.encours) },
                )
                is CarteEnsuite.Saga -> LigneEnsuite(
                    coverUrl = carte.encours.prochain.cover_url,
                    titreAffiche = carte.encours.prochain.title,
                    libelle = "Ensuite · ${carte.encours.entite.nom}",
                    titre = titreEtAnnee(carte.encours.prochain),
                    onClick = { onOpenEnsuiteSaga(carte.encours) },
                )
            }
        }
        if (cartes.size > 1) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.Center) {
                repeat(cartes.size) { index ->
                    val actif = index == etat.currentPage
                    Box(
                        Modifier
                            .padding(horizontal = 3.dp)
                            .size(if (actif) 8.dp else 6.dp)
                            .background(
                                if (actif) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                                CircleShape,
                            ),
                    )
                }
            }
        }
    }
}

/**
 * Une ligne « Ensuite » : une affiche 56×84 à gauche, un libellé discret au-dessus du titre.
 * Le même composant sert « Ce soir » et les pages du carrousel « Ensuite » (Plex, réalisateur en
 * cours, saga en cours) — plutôt que des copies qui divergeraient à la première retouche (brief du
 * 15 septembre 2026, généralisé aux sagas le même jour ; devenu les pages du carrousel le 24
 * septembre 2026, point 5).
 */
@Composable
private fun LigneEnsuite(
    coverUrl: String?,
    titreAffiche: String,
    libelle: String,
    titre: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            // Une carte à cadre fin (habillage du 23 septembre 2026, geste 4) : un simple filet or,
            // pas le double filet de `CadreOrne` — réservé aux cartouches.
            .border(1.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.04f), MaterialTheme.shapes.small)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(coverUrl, titreAffiche, 56.dp, 84.dp)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                libelle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(titre, style = MaterialTheme.typography.titleMedium)
        }
    }
}
