package fr.mediatheque.journal.ui.realisateur

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.api.dto.RealisateurPageResponse
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.TamponPerdu
import fr.mediatheque.journal.ui.EntreeEnCascade
import fr.mediatheque.journal.ui.afficheVolante
import fr.mediatheque.journal.ui.rememberPorteCascade
import fr.mediatheque.journal.ui.voler
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.frise.Monde
import fr.mediatheque.journal.ui.frise.TeinteSepia
import fr.mediatheque.journal.ui.theme.Fraunces
import fr.mediatheque.journal.ui.frise.mondeDe
import fr.mediatheque.journal.ui.frise.mondeDeLaDecennie
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.suivis.Portrait

/**
 * La page d'un réalisateur (reprise du 21 septembre 2026, « la page réalisateur, reprise » —
 * jugée illisible en étagère horizontale ; retouche du 22 septembre 2026, « la filmographie dans
 * l'ordre »). Les séries sont retirées d'emblée (`filmsSansSeries`), avant que quoi que ce soit
 * d'autre ne lise la filmographie : le fond est celui du monde du Voyage de l'année du premier
 * film daté (`mondeDeLaPage`), calculé sur cette même liste. Un seul défilement, `LazyVerticalGrid`
 * (`GridCells.Fixed(3)`) : l'en-tête (photo, nom, dates, présentation, bouton Suivre/Suivi, résumé)
 * en est le tout premier item, en pleine largeur, puis la filmographie groupée par décennie
 * (`regrouperParDecennie`) — une seule grille par décennie, trois affiches par ligne, longs et
 * courts mêlés dans l'ordre du back, sans rien réordonner. Les films marqués introuvables sont
 * absents tant que l'interrupteur « Masquer les introuvables » de l'en-tête est activé (retouche
 * du 22 septembre 2026, jumeau de la fiche d'une saga ; `filmsAffiches`). Appui long sur un non-vu
 * marque ou démarque « introuvable ».
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RealisateurScreen(
    vm: RealisateurViewModel,
    resolveur: RealisateurResolveur,
    onBack: () -> Unit,
    onOuvrirFilm: (FilmDeFilmographie) -> Unit,
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : la grille est un des deux
    // bouts de la paire vers `Screen.FicheFilm` (`Root.kt`).
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
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
    // par défaut le temps du premier chargement, où il n'y a encore aucun film à dater. Les séries
    // en sont déjà sorties (décision 3 de la retouche du 22 septembre 2026) : un film sans année ne
    // doit rien à une série écartée avant lui.
    val monde = (ui.etat as? EtatPageRealisateur.Pret)?.let { mondeDeLaPage(filmsSansSeries(it.page.films)) } ?: mondeDe(1895)

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
                    // Les séries sortent de la page ici, avant que `GrilleFilmographie` ou son
                    // en-tête ne touchent à `page.films` : `regrouperParDecennie` et `ligneResume`
                    // n'en voient donc plus aucune trace (décision 3 de la retouche du 22 septembre
                    // 2026).
                    page = etat.page.copy(films = filmsSansSeries(etat.page.films)),
                    monde = monde,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    onSuivre = vm::suivre,
                    onRetirer = vm::retirer,
                    onOuvrirFilm = onOuvrirFilm,
                    onLongClickNonVu = { film -> feuillePour = film },
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                )
            }
        }
    }
}

/**
 * La grille verticale (décision 1 de la retouche du 22 septembre 2026, « la filmographie dans
 * l'ordre ») : l'en-tête, puis chaque décennie — son en-tête, puis une seule grille de trois
 * affiches par ligne pour tous ses films, longs et courts mêlés dans l'ordre du back. Plus d'état
 * déplié/replié à mémoriser ; `masquerIntrouvables` survit lui à une rotation.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GrilleFilmographie(
    page: RealisateurPageResponse,
    monde: Monde,
    modifier: Modifier = Modifier,
    onSuivre: () -> Unit,
    onRetirer: () -> Unit,
    onOuvrirFilm: (FilmDeFilmographie) -> Unit,
    onLongClickNonVu: (FilmDeFilmographie) -> Unit,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    var masquerIntrouvables by rememberSaveable { mutableStateOf(true) }
    val decennies = remember(page.films, masquerIntrouvables) {
        regrouperParDecennie(filmsAffiches(page.films, masquerIntrouvables))
    }
    // La cascade d'entrée (habillage du 23 septembre 2026, geste 8) : posée une fois ici, pour
    // toute la filmographie.
    val porteCascade = rememberPorteCascade()

    BoxWithConstraints(modifier) {
        val margeHorizontale = 16.dp
        val gouttiere = 12.dp
        val largeurContenu = maxWidth - margeHorizontale * 2
        val largeurAffiche = (largeurContenu - gouttiere * 2) / 3
        val hauteurAffiche = largeurAffiche * 1.5f

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

                itemsIndexed(decennie.films, key = { _, film -> "film-${film.tmdb_id}" }) { index, film ->
                    // La cascade d'entrée (habillage du 23 septembre 2026, geste 8), un index par
                    // décennie plutôt qu'un compte continu sur toute la page.
                    EntreeEnCascade(index, porteCascade, Modifier.animateItem()) { m ->
                        AfficheFilmographie(
                            film = film,
                            largeur = largeurAffiche,
                            hauteur = hauteurAffiche,
                            onClick = { onOuvrirFilm(film) },
                            onLongClick = { onLongClickNonVu(film) },
                            // « Masquer les introuvables » (geste 3 du peaufinage du 23 septembre
                            // 2026) : la grille se retasse au lieu de sauter quand l'interrupteur en
                            // retire des affiches.
                            modifier = m,
                            // L'affiche partagée (geste 8) : même clé que `Screen.FicheFilm`.
                            volante = afficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-realisateur-${film.tmdb_id}"),
                        )
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
        Box {
            Portrait(page.photo_url, page.name, 96.dp)
            // Le sceau de la rétrospective complète (geste 20 du complément du 23 septembre 2026
            // à l'habillage) : posé sur le portrait, seulement quand `retrospectiveComplete` le dit
            // — `page.films` est déjà sans ses séries (décision 3 de la retouche du 22 septembre
            // 2026, appliquée avant `EnTeteRealisateur`).
            if (retrospectiveComplete(page.films)) {
                SceauRetrospective(monde, modifier = Modifier.align(Alignment.BottomEnd).size(30.dp))
            }
        }
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

private val FiltreDesature = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/**
 * Le sceau or « rétrospective complète » (geste 20 du complément du 23 septembre 2026 à
 * l'habillage) : un cercle plein avec `✦` en Fraunces, posé sur le portrait. À l'échelle avec
 * dépassement à la première composition (`LaunchedEffect(Unit)`, jamais rejouée à une simple
 * recomposition de l'en-tête — l'interrupteur des introuvables juste en dessous, par exemple) ;
 * statique ensuite, comme le sceau (plus petit) d'une année ouverte (`Photogramme`, `AnneeScreen.kt`).
 */
@Composable
private fun SceauRetrospective(monde: Monde, modifier: Modifier = Modifier) {
    val echelle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        echelle.animateTo(1f, tween(400, easing = CubicBezierEasing(0.3f, 1.6f, 0.4f, 1f)))
    }
    Box(
        modifier
            .graphicsLayer { scaleX = echelle.value; scaleY = echelle.value }
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
            .border(1.dp, monde.fond, CircleShape)
            .semantics { contentDescription = "Rétrospective complète" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "✦",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Fraunces),
            color = monde.fond,
        )
    }
}

/**
 * Une affiche de la filmographie (inchangé : vu en couleur avec pastille de note, sépia sinon,
 * coin Plex, liseré dans l'accent quand `annee_ouverte`), avec sous elle son titre (deux lignes au
 * plus) puis son année — lisibles même sans jaquette, `Cover` gardant alors son rectangle sépia.
 * Le coin « Court » (retouche du 22 septembre 2026, décision 2 : jumeau du coin « TV » qu'il
 * remplace — même place, même style — les séries ayant quitté la page) marque un court métrage.
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun AfficheFilmographie(
    film: FilmDeFilmographie,
    largeur: Dp,
    hauteur: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    volante: AfficheVolante? = null,
) {
    val vu = film.vu != null
    val monde = mondeDe(film.year ?: 1895)
    // Le tampon « PERDU » qui s'abat à la marque (geste 17 du complément du 23 septembre 2026) :
    // observe la transition locale de `film.introuvable`, jamais simplement sa valeur au premier
    // affichage — une filmographie ouverte sur un film déjà introuvable de longue date ne rejoue
    // pas la chute, seule une marque posée sous nos yeux le fait.
    val haptique = LocalHapticFeedback.current
    var introuvablePrecedent by remember(film.tmdb_id) { mutableStateOf(film.introuvable) }
    val echelleTampon = remember(film.tmdb_id) { Animatable(1f) }
    LaunchedEffect(film.introuvable) {
        if (!introuvablePrecedent && film.introuvable) {
            echelleTampon.snapTo(3f)
            echelleTampon.animateTo(1f, tween(300, easing = LinearOutSlowInEasing))
            haptique.performHapticFeedback(HapticFeedbackType.Confirm)
        }
        introuvablePrecedent = film.introuvable
    }
    Column(
        modifier
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
            Cover(
                film.cover_url,
                film.title,
                largeur,
                hauteur,
                modifier = Modifier.voler(volante),
                colorFilter = if (vu) null else FiltreDesature,
            )
            if (film.introuvable) {
                TamponPerdu(
                    modifier = Modifier.align(Alignment.Center).size(minOf(largeur, hauteur) * 0.62f),
                    echelle = echelleTampon.value,
                )
            } else if (!vu) {
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
            // Nom qualifié en entier : à l'intersection d'un `Column` et d'un `Box` implicites,
            // le compilateur hésite sinon entre les surcharges `ColumnScope`/`BoxScope`.
            androidx.compose.animation.AnimatedVisibility(
                visible = film.sur_le_plex,
                modifier = Modifier.align(Alignment.TopEnd),
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)),
                exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.6f, animationSpec = tween(150)),
            ) {
                Icon(
                    Icons.Filled.Cloud,
                    contentDescription = "Sur le Plex",
                    // `Color.White` ignorait le thème (peaufinage du 23 septembre 2026, geste 5) ;
                    // `4.dp` rejoint la grille 4/8/12/16 (design §4), au lieu du `3.dp` isolé.
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .padding(4.dp)
                        .size(12.dp)
                        .alpha(0.9f),
                )
            }
            if (film.court) {
                Text(
                    "Court",
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
    // Retour haptique (peaufinage du 23 septembre 2026, geste 10) : jumeau d'`IntrouvableSheet`.
    val haptique = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (film.introuvable) {
                TextButton(onClick = {
                    haptique.performHapticFeedback(HapticFeedbackType.ToggleOff)
                    onRetirer()
                }) { Text("Le remettre à voir") }
            } else {
                TextButton(onClick = {
                    haptique.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    onMarquer()
                }) { Text("Marquer introuvable") }
            }
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    }
}
