package fr.mediatheque.journal.ui.realisateurs

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.FilmDeRealisateur
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.showBriefly

/**
 * La fiche d'un réalisateur suivi (brief du 15 septembre 2026) : sa filmographie dans l'ordre,
 * de la plus ancienne sortie à la plus récente, avec sa note à droite quand je l'ai vu. Le
 * premier film non vu et non introuvable porte une pastille corail « à voir » — c'est celui que
 * la liste annonce sous son nom, et celui que l'accueil met dans « Ensuite » quand c'est lui le
 * réalisateur en cours.
 *
 * Un film peut aussi être marqué introuvable (décision du propriétaire du 15 septembre 2026) :
 * un appui long sur un film non vu ouvre la feuille qui marque ou démarque. L'interrupteur en
 * tête masque les films marqués, ou les grise avec la mention « introuvable » à droite — jamais
 * hors d'atteinte d'un appui long, dans un cas comme dans l'autre.
 *
 * Empilée depuis `Screen.Realisateurs`, sans barre du bas. Elle lit le `ViewModel` partagé
 * plutôt que de recharger : la liste a déjà tiré les filmographies.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RealisateurScreen(
    vm: RealisateursViewModel,
    tmdbId: Int,
    onBack: () -> Unit,
    onOuvrirVu: (JournalItem) -> Unit,
    onOuvrirAVoir: (SearchResult) -> Unit,
    onSupprimer: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val realisateur = ui.realisateurs.firstOrNull { it.tmdb_id == tmdbId }
    val etat = ui.filmographies[tmdbId] ?: EtatFilmographie.EnAttente
    var confirmation by remember { mutableStateOf(false) }
    var feuillePour by remember { mutableStateOf<FilmDeRealisateur?>(null) }

    val snackbar = remember { SnackbarHostState() }
    // Même canal que la liste (`RealisateursScreen`) : un `ViewModel` partagé, un seul
    // `messages`. Les deux écrans ne sont jamais composés ensemble, donc jamais collecté deux
    // fois pour un même message.
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Ne plus suivre ${realisateur?.name ?: "ce réalisateur"} ?") },
            text = { Text("Sa filmographie disparaîtra de la liste. Tes films vus, eux, restent au journal.") },
            confirmButton = {
                TextButton(onClick = { confirmation = false; onSupprimer() }) { Text("Ne plus suivre") }
            },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Annuler") } },
        )
    }

    feuillePour?.let { film ->
        IntrouvableSheet(
            film = film,
            onMarquer = { feuillePour = null; vm.marquerIntrouvable(tmdbId, film.tmdb_id) },
            onRetirer = { feuillePour = null; vm.retirerIntrouvable(tmdbId, film.tmdb_id) },
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
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    realisateur?.name ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmation = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Ne plus suivre")
                }
            }

            when (etat) {
                EtatFilmographie.EnAttente -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                EtatFilmographie.Indisponible -> ErrorBlock(
                    "Sa filmographie est indisponible.",
                    retryable = true,
                    onRetry = { vm.rechargerFilmographie(tmdbId) },
                    modifier = Modifier.padding(16.dp),
                )

                is EtatFilmographie.Pret -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Masquer les introuvables",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = ui.masquerIntrouvables, onCheckedChange = { vm.basculerMasquerIntrouvables() })
                    }

                    // Comparé par identifiant et non par place dans la liste affichée : masquer
                    // les introuvables retire des lignes, et un index calculé sur la liste entière
                    // pointerait alors sur la mauvaise ligne. `prochainAVoir` ignore déjà les
                    // introuvables, donc jamais désigné ici.
                    val prochain = prochainAVoir(etat.films)
                    val filmsAffiches = if (ui.masquerIntrouvables) etat.films.filterNot { it.introuvable } else etat.films

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filmsAffiches, key = { it.tmdb_id }) { film ->
                            LigneFilm(
                                film = film,
                                aVoir = film.tmdb_id == prochain?.tmdb_id,
                                onClick = {
                                    val entree = film.vu?.let { ui.entrees[it.entry_id] }
                                    // Un film vu ouvre *son* entrée de journal, jamais un
                                    // formulaire de création : la rouvrir en création ajouterait
                                    // un second visionnage au lieu de corriger celui-ci. Tant que
                                    // le journal n'est pas relu (`chargerEntrees`), l'entrée
                                    // manque et la ligne ne fait rien — mieux que d'ouvrir le
                                    // mauvais écran.
                                    when {
                                        entree != null -> onOuvrirVu(entree)
                                        film.vu == null -> onOuvrirAVoir(film.toSearchResult(realisateur?.name))
                                        else -> Unit
                                    }
                                },
                                // Un film déjà vu n'a pas de marque à poser : rien à lui ouvrir.
                                onLongClick = { if (film.vu == null) feuillePour = film },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * « Marquer introuvable » / « Annuler » sur un film non encore marqué, « Le remettre à voir »
 * sur un film qui l'est déjà — jumeau de `SensCritiqueChoiceSheet` (`ui/form/`). Le retour
 * système la referme comme `onDismiss`, sans rien poser : ce n'est pas une décision, juste une
 * sortie.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntrouvableSheet(film: FilmDeRealisateur, onMarquer: () -> Unit, onRetirer: () -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (film.introuvable) {
                TextButton(onClick = onRetirer) { Text("Le remettre à voir") }
            } else {
                TextButton(onClick = onMarquer) { Text("Marquer introuvable") }
                TextButton(onClick = onDismiss) { Text("Annuler") }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneFilm(film: FilmDeRealisateur, aVoir: Boolean, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            // Grisée plutôt que cachée : c'est l'interrupteur « Masquer les introuvables », pas
            // cette ligne, qui décide si un film introuvable apparaît. Un appui long reste
            // possible dessus, grisée ou non — « la remettre à voir » ne doit pas être plus dur à
            // atteindre que « la marquer » ne l'a été.
            .alpha(if (film.introuvable) 0.5f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            film.year?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Cover(film.cover_url, film.title, 30.dp, 45.dp)
        Text(film.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        val note = film.vu?.rating
        when {
            note != null -> Text(
                "$note",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Note $note sur 10" },
            )

            film.introuvable -> Text(
                "introuvable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            aVoir -> Text(
                "à voir",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
