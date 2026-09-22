package fr.mediatheque.journal.ui.realisateur

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.api.dto.RealisateurPageResponse
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.frise.Monde
import fr.mediatheque.journal.ui.frise.TeinteSepia
import fr.mediatheque.journal.ui.frise.mondeDe
import fr.mediatheque.journal.ui.frise.mondeDeLaDecennie
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.suivis.Portrait

/**
 * La page d'un réalisateur (reprise du 21 septembre 2026, « la page réalisateur, reprise » —
 * jugée illisible en étagère horizontale) : le fond est celui du monde du Voyage de l'année du
 * premier film daté (`mondeDeLaPage`). Un seul défilement, `LazyVerticalGrid` (`GridCells.Fixed(3)`) :
 * l'en-tête (photo, nom, dates, présentation, bouton Suivre/Suivi, résumé) en est le tout premier
 * item, en pleine largeur, puis la filmographie groupée par décennie (`regrouperParDecennie`) —
 * les longs en grand trois par ligne, puis, sous une ligne « 6 courts · 1 série » qui replie ou
 * déplie, les courts et les séries plus petits quatre par ligne, dépliés d'emblée (retouche du
 * 22 septembre 2026 ; une décennie sans long n'a pas de ligne du tout). Les films marqués
 * introuvables sont absents tant que l'interrupteur « Masquer les introuvables » de l'en-tête est
 * activé (même retouche, jumeau de la fiche d'une saga ; `filmsAffiches`). Appui long sur un
 * non-vu marque ou démarque « introuvable ».
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealisateurScreen(
    vm: RealisateurViewModel,
    resolveur: RealisateurResolveur,
    onBack: () -> Unit,
    onOuvrirFilm: (FilmDeFilmographie) -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    var feuillePour by remember { mutableStateOf<FilmDeFilmographie?>(null) }

    feuillePour?.let { film ->
        IntrouvableSheetFilmographie(
            film = film,
            onMarquer = { feuillePour = null; vm.marquerIntrouvable(film.tmdb_id) },
            onRetirer = { feuillePour = null; vm.retirerIntrouvable(film.tmdb_id) },
            onDismiss = { feuillePour = null },
        )
    }

    // Le fond suit le monde de la page (décision 6) une fois la fiche chargée ; celui des origines
    // par défaut le temps du premier chargement, où il n'y a encore aucun film à dater.
    val monde = (ui.etat as? EtatPageRealisateur.Pret)?.let { mondeDeLaPage(it.page.films) } ?: mondeDe(1895)

    Scaffold(
        containerColor = monde.fond,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            }

            when (val etat = ui.etat) {
                EtatPageRealisateur.EnAttente -> Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                EtatPageRealisateur.Indisponible -> ErrorBlock(
                    "Cette page est indisponible.",
                    retryable = true,
                    onRetry = vm::charger,
                    modifier = Modifier.padding(16.dp),
                )

                is EtatPageRealisateur.Pret -> GrilleFilmographie(
                    page = etat.page,
                    monde = monde,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onSuivre = vm::suivre,
                    onRetirer = vm::retirer,
                    onOuvrirFilm = onOuvrirFilm,
                    onLongClickNonVu = { film -> feuillePour = film },
                )
            }
        }
    }
}

/**
 * La grille verticale (décision 1) : l'en-tête, puis chaque décennie — son en-tête, ses longs, et
 * ses courts/séries (dépliés d'emblée sous une ligne qui les replie, sauf décennie sans long, qui
 * n'a pas de ligne). `depliees` mémorise l'état de chaque ligne pour la session de cet écran (état
 * local par décennie, absent = déplié) ; `masquerIntrouvables` survit à une rotation.
 */
@Composable
private fun GrilleFilmographie(
    page: RealisateurPageResponse,
    monde: Monde,
    modifier: Modifier = Modifier,
    onSuivre: () -> Unit,
    onRetirer: () -> Unit,
    onOuvrirFilm: (FilmDeFilmographie) -> Unit,
    onLongClickNonVu: (FilmDeFilmographie) -> Unit,
) {
    var masquerIntrouvables by rememberSaveable { mutableStateOf(true) }
    val decennies = remember(page.films, masquerIntrouvables) {
        regrouperParDecennie(filmsAffiches(page.films, masquerIntrouvables))
    }
    val depliees = remember { mutableStateMapOf<Int?, Boolean>() }

    BoxWithConstraints(modifier) {
        val margeHorizontale = 16.dp
        val gouttiere = 12.dp
        val largeurContenu = maxWidth - margeHorizontale * 2
        val largeurLong = (largeurContenu - gouttiere * 2) / 3
        val hauteurLong = largeurLong * 1.5f
        val gouttierePetite = 8.dp
        val largeurPetite = (largeurContenu - gouttierePetite * 3) / 4
        val hauteurPetite = largeurPetite * 1.5f

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = margeHorizontale, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(gouttiere),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EnTeteRealisateur(
                    page = page,
                    monde = monde,
                    masquerIntrouvables = masquerIntrouvables,
                    onMasquerIntrouvables = { masquerIntrouvables = it },
                    onSuivre = onSuivre,
                    onRetirer = onRetirer,
                )
            }

            decennies.forEach { decennie ->
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val mondeDecennie = decennie.decennie?.let { mondeDeLaDecennie(it) } ?: monde
                    Text(
                        libelleDecennie(decennie.decennie),
                        style = MaterialTheme.typography.titleMedium,
                        color = mondeDecennie.accent,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }

                items(decennie.longs, key = { "long-${it.tmdb_id}" }) { film ->
                    AfficheFilmographie(
                        film = film,
                        largeur = largeurLong,
                        hauteur = hauteurLong,
                        onClick = { onOuvrirFilm(film) },
                        onLongClick = { onLongClickNonVu(film) },
                    )
                }

                if (decennie.courtsEtSeries.isNotEmpty()) {
                    if (decennie.longs.isEmpty()) {
                        // Décennie sans long (Lumière, 1895–1905) : rien à replier, donc pas de ligne.
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            GrilleCourtsEtSeries(
                                films = decennie.courtsEtSeries,
                                largeur = largeurPetite,
                                hauteur = hauteurPetite,
                                gouttiere = gouttierePetite,
                                onOuvrirFilm = onOuvrirFilm,
                                onLongClickNonVu = onLongClickNonVu,
                            )
                        }
                    } else {
                        val depliee = depliees[decennie.decennie] != false
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable { depliees[decennie.decennie] = !depliee }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(libelleCourtsEtSeries(decennie.courtsEtSeries), style = MaterialTheme.typography.labelMedium)
                                Icon(
                                    if (depliee) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (depliee) "Replier" else "Déplier",
                                )
                            }
                        }
                        if (depliee) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                GrilleCourtsEtSeries(
                                    films = decennie.courtsEtSeries,
                                    largeur = largeurPetite,
                                    hauteur = hauteurPetite,
                                    gouttiere = gouttierePetite,
                                    onOuvrirFilm = onOuvrirFilm,
                                    onLongClickNonVu = onLongClickNonVu,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** L'en-tête de la page (décision 1 et 6) : photo, nom, dates, présentation, bouton, résumé, et l'interrupteur des introuvables. */
@Composable
private fun EnTeteRealisateur(
    page: RealisateurPageResponse,
    monde: Monde,
    masquerIntrouvables: Boolean,
    onMasquerIntrouvables: (Boolean) -> Unit,
    onSuivre: () -> Unit,
    onRetirer: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Portrait(page.photo_url, page.name, 96.dp)
        Text(
            page.name,
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            color = monde.accent,
            textAlign = TextAlign.Center,
        )
        val dates = ligneDates(page.naissance, page.deces, page.genre)
        if (dates.isNotEmpty()) {
            Text(dates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (page.presentation.isNotEmpty()) {
            Text(
                page.presentation,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (page.suivi) {
            OutlinedButton(onClick = onRetirer) { Text("Suivi") }
        } else {
            FilledTonalButton(onClick = onSuivre) { Text("Suivre") }
        }
        Text(
            ligneResume(page.films),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Le résumé compte tout, introuvables compris : l'interrupteur cache des affiches, il ne
        // change pas la filmographie. Même interrupteur que la fiche d'une saga.
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Masquer les introuvables",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
            Switch(checked = masquerIntrouvables, onCheckedChange = onMasquerIntrouvables)
        }
    }
}

/** Les courts et séries d'une décennie, quatre par ligne, plus petits que les longs (décision 3). */
@Composable
private fun GrilleCourtsEtSeries(
    films: List<FilmDeFilmographie>,
    largeur: Dp,
    hauteur: Dp,
    gouttiere: Dp,
    onOuvrirFilm: (FilmDeFilmographie) -> Unit,
    onLongClickNonVu: (FilmDeFilmographie) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(gouttiere)) {
        films.chunked(4).forEach { ligne ->
            Row(horizontalArrangement = Arrangement.spacedBy(gouttiere)) {
                ligne.forEach { film ->
                    AfficheFilmographie(
                        film = film,
                        largeur = largeur,
                        hauteur = hauteur,
                        onClick = { onOuvrirFilm(film) },
                        onLongClick = { onLongClickNonVu(film) },
                    )
                }
            }
        }
    }
}

private val FiltreDesature = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Une affiche de la filmographie (inchangé : vu en couleur avec pastille de note, sépia sinon,
 * coin Plex, coin TV, liseré dans l'accent quand `annee_ouverte`), avec sous elle son titre (deux
 * lignes au plus) puis son année (décision 2) — lisibles même sans jaquette, `Cover` gardant alors
 * son rectangle sépia.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AfficheFilmographie(film: FilmDeFilmographie, largeur: Dp, hauteur: Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    val vu = film.vu != null
    val monde = mondeDe(film.year ?: 1895)
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = if (film.vu == null) onLongClick else null)
            .width(largeur),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            if (film.annee_ouverte) {
                Modifier.border(1.5.dp, monde.accent, MaterialTheme.shapes.small)
            } else {
                Modifier
            },
        ) {
            Cover(film.cover_url, film.title, largeur, hauteur, colorFilter = if (vu) null else FiltreDesature)
            if (!vu) {
                Box(Modifier.size(largeur, hauteur).background(TeinteSepia.copy(alpha = 0.35f)))
            }
            if (vu && film.vu?.rating != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clearAndSetSemantics { contentDescription = "Note ${film.vu?.rating} sur 10" },
                ) {
                    Text("${film.vu?.rating}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            // Coin discret « Plex » (décision 1 du brief) : une icône seule, sans fond, jamais
            // aussi appuyée que la pastille de note.
            if (film.sur_le_plex) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = "Sur le Plex",
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(3.dp)
                        .size(12.dp)
                        .alpha(0.9f),
                )
            }
            if (film.type == "tv") {
                Text(
                    "TV",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                        .padding(horizontal = 3.dp),
                )
            }
        }
        Text(
            film.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp).width(largeur),
        )
        Text(
            film.year?.toString() ?: "—",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** « Marquer introuvable » / « Le remettre à voir » (existants) sur un film de la filmographie — jumeau de `IntrouvableSheet`, `ui/suivis/FicheSuiviScreen.kt`. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntrouvableSheetFilmographie(film: FilmDeFilmographie, onMarquer: () -> Unit, onRetirer: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (film.introuvable) {
                TextButton(onClick = onRetirer) { Text("Le remettre à voir") }
            } else {
                TextButton(onClick = onMarquer) { Text("Marquer introuvable") }
            }
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    }
}
