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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.Navigator
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
import fr.mediatheque.journal.ui.theme.Perforations
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Demandé par le propriétaire le 10 septembre 2026, après le premier essai sur téléphone :
 * l'accueil montre les films vus, jaquettes seules, du plus récent au plus ancien, au-dessus
 * du bouton « Ajouter un film » qui descend en bas. Le `vm` est le même `FilmsViewModel` que
 * « Mes films » (clé `"films"` dans `Root.kt`) : une seule source, deux présentations.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    vm: FilmsViewModel,
    nav: Navigator,
    onAdd: () -> Unit,
    onOpen: (JournalItem) -> Unit,
    bottomBar: @Composable () -> Unit,
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
        snackbarHost = {
            // 68 dp : le bouton (52 dp) plus sa marge (16 dp), pour que la snackbar ne tombe pas
            // dessus (relecture, correction 6).
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 68.dp)) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Trois colonnes, 8 dp d'écart (grille du design §4) : `Cover` prend une largeur et
            // une hauteur fixes, pas un modificateur élastique, donc la taille d'une jaquette se
            // déduit ici de la largeur disponible plutôt que d'être posée dans `Cover` lui-même.
            val ecart = 8.dp
            val largeurJaquette = (maxWidth - ecart * 2) / 3
            val hauteurJaquette = largeurJaquette * 1.5f

            Column(Modifier.fillMaxSize()) {
                // Habillage « papier et pellicule » (23 septembre 2026, geste 4) : une bande de
                // perforations sous l'en-tête de l'écran, fixe (une décennie bouclée s'anime, pas
                // l'accueil à chaque frame).
                Perforations(modifier = Modifier.padding(bottom = 12.dp))
                // Le Voyage (brief du 16 septembre 2026) : « sous le bandeau » — juste après le
                // « Enregistré » de la snackbar — la carte du film qu'on vient de journaliser, tant
                // qu'elle existe (`carton` nul en dehors de cette fenêtre, `CartonCard` muette tant
                // que le chroniqueur n'est pas configuré côté back).
                carton?.let { vm ->
                    val cartonUi by vm.ui.collectAsState()
                    CartonCard(
                        cartonUi,
                        attente = true,
                        onDismiss = onCartonDismiss,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                // « Ce soir » (décision 4 du brief du 21 septembre 2026, « la séance ») : au-dessus
                // d'« Ensuite », même gabarit qu'elle (`LigneEnsuite`) — chargement non bloquant,
                // comme les trois lignes qui suivent.
                ceSoir?.let { seance ->
                    LigneEnsuite(
                        coverUrl = seance.longCoverUrl,
                        titreAffiche = seance.longTitre,
                        libelle = "Ce soir",
                        titre = texteCeSoir(seance),
                        onClick = { onOpenCeSoir(seance) },
                    )
                }
                // Pas de chargement bloquant (brief du 15 septembre 2026) : la ligne n'existe
                // simplement pas tant que `ensuite` est nul, que ce soit parce que
                // `/reference/plex` n'a pas encore répondu ou parce qu'il n'y a rien à voir.
                ensuite?.let { film ->
                    LigneEnsuite(
                        coverUrl = film.cover_url,
                        titreAffiche = film.title,
                        libelle = "Ensuite",
                        titre = film.year?.let { annee -> "${film.title} ($annee)" } ?: film.title,
                        onClick = { onOpenEnsuite(film) },
                    )
                }
                // La seconde ligne « Ensuite », celle du réalisateur en cours (brief du
                // 15 septembre 2026) : même composant que celle du Plex juste au-dessus, jamais
                // une copie — le nom vient sur la première ligne, après « Ensuite · », et le
                // titre du film prend la seconde, comme pour le Plex.
                ensuiteRealisateur?.let { encours ->
                    LigneEnsuite(
                        coverUrl = encours.prochain.cover_url,
                        titreAffiche = encours.prochain.title,
                        libelle = "Ensuite · ${encours.entite.nom}",
                        titre = titreEtAnnee(encours.prochain),
                        onClick = { onOpenEnsuiteRealisateur(encours) },
                    )
                }
                // La troisième ligne « Ensuite », celle de la saga en cours (brief du
                // 15 septembre 2026, généralisé le même jour) : même composant, même règle que
                // celle du réalisateur juste au-dessus.
                ensuiteSaga?.let { encours ->
                    LigneEnsuite(
                        coverUrl = encours.prochain.cover_url,
                        titreAffiche = encours.prochain.title,
                        libelle = "Ensuite · ${encours.entite.nom}",
                        titre = titreEtAnnee(encours.prochain),
                        onClick = { onOpenEnsuiteSaga(encours) },
                    )
                }
                ui.error?.let {
                    ErrorBlock(
                        it.message ?: "",
                        retryable = it.retryable,
                        onRetry = vm::loadMore,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                // Tirer pour rafraîchir (peaufinage du 23 septembre 2026, geste 12) : branché sur
                // le `refresh()` du même `FilmsViewModel` que « Mes films ». `ui.loading` porte
                // aussi bien ce rafraîchissement que la pagination et le premier chargement à
                // l'entrée sur l'écran (relecture du 23 septembre 2026) : l'indicateur de tirage
                // ne doit s'afficher que sur un tirage vraiment fait, pas à chaque fois que
                // `ui.loading` passe à vrai. `tire` ne monte que dans `onRefresh` et retombe dès
                // que `ui.loading` redescend, quelle qu'en soit la cause.
                var tire by remember { mutableStateOf(false) }
                LaunchedEffect(ui.loading) { if (!ui.loading) tire = false }
                PullToRefreshBox(
                    isRefreshing = tire && ui.loading,
                    onRefresh = { if (!ui.loading) { tire = true; vm.refresh() } },
                    modifier = Modifier.weight(1f),
                ) {
                if (ui.items.isEmpty() && ui.endReached) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "Aucun film pour l’instant.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        state = grille,
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(ecart),
                        verticalArrangement = Arrangement.spacedBy(ecart),
                    ) {
                        items(ui.items, key = { it.entry.id }) { item ->
                            // La grille se retasse (geste 3 du peaufinage du 23 septembre 2026) au
                            // lieu de sauter quand un film change de place ou disparaît.
                            Box(Modifier.animateItem().clickable { onOpen(item) }) {
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
                Spacer(Modifier.height(16.dp))
                // Le bouton est ici un enfant du `Column`, après la grille, plutôt qu'un enfant du
                // `Box` aligné en bas : hors du flux de défilement, aucune jaquette ne peut passer
                // dessous en défilant, contrairement à un `contentPadding` sur la grille, qui ne
                // fixe que sa position au repos (relecture, correction 2 — le commentaire qu'elle
                // remplace était faux).
                Button(
                    onClick = onAdd,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Ajouter un film") }
            }
        }
    }
}

/**
 * Une ligne « Ensuite » : une affiche 56×84 à gauche, un libellé discret au-dessus du titre.
 * Le même composant sert les trois lignes possibles de l'accueil — celle du Plex (« Ensuite »),
 * celle du réalisateur en cours et celle de la saga en cours (« Ensuite · *Nom* » pour les deux
 * dernières) — plutôt que des copies qui divergeraient à la première retouche (brief du
 * 15 septembre 2026, généralisé aux sagas le même jour).
 */
@Composable
private fun LigneEnsuite(
    coverUrl: String?,
    titreAffiche: String,
    libelle: String,
    titre: String,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp)
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
