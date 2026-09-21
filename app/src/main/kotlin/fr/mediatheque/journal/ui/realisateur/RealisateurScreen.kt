package fr.mediatheque.journal.ui.realisateur

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.frise.TeinteSepia
import fr.mediatheque.journal.ui.frise.mondeDe
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.suivis.Portrait

/**
 * La page d'un réalisateur (décision 1 du brief du 21 septembre 2026, « la page réalisateur ») :
 * une seule page, suivi ou non — la fiche (photo, nom, dates, présentation), le bouton Suivre/Suivi,
 * puis sa filmographie complète en étagère d'affiches, chronologique, vus en couleur (pastille de
 * note) et le reste en sépia, comme l'étagère d'une salle du Voyage (`AnneeScreen.kt`, réutilisée
 * ici pour le même geste). Appui long sur un non-vu marque ou démarque « introuvable ».
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
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

                is EtatPageRealisateur.Pret -> {
                    val page = etat.page
                    Column(
                        Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Portrait(page.photo_url, page.name, 96.dp)
                            Text(page.name, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                            val dates = ligneDates(page.naissance, page.deces)
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
                                OutlinedButton(onClick = vm::retirer) { Text("Suivi") }
                            } else {
                                FilledTonalButton(onClick = vm::suivre) { Text("Suivre") }
                            }
                        }

                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        ) {
                            items(page.films, key = { it.tmdb_id }) { film ->
                                AfficheFilmographie(
                                    film = film,
                                    onClick = { onOuvrirFilm(film) },
                                    onLongClick = { if (film.vu == null) feuillePour = film },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val LARGEUR_AFFICHE = 72.dp
private val HAUTEUR_AFFICHE = 108.dp
private val FiltreDesature = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AfficheFilmographie(film: FilmDeFilmographie, onClick: () -> Unit, onLongClick: () -> Unit) {
    val vu = film.vu != null
    val monde = mondeDe(film.year ?: 1895)
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = if (film.vu == null) onLongClick else null)
            .width(LARGEUR_AFFICHE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            if (film.annee_ouverte) {
                Modifier.border(1.5.dp, monde.accent, MaterialTheme.shapes.small)
            } else {
                Modifier
            },
        ) {
            Cover(film.cover_url, film.title, LARGEUR_AFFICHE, HAUTEUR_AFFICHE, colorFilter = if (vu) null else FiltreDesature)
            if (!vu) {
                Box(Modifier.size(LARGEUR_AFFICHE, HAUTEUR_AFFICHE).background(TeinteSepia.copy(alpha = 0.35f)))
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
