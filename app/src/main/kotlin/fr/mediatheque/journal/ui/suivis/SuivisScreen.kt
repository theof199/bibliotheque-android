package fr.mediatheque.journal.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.showBriefly

/**
 * Ce que je suis — réalisateurs ou sagas (brief du 15 septembre 2026,
 * l'onglet « Réalisateurs » devient « Suivis ») : une ligne par entité, sa
 * photo ou son affiche ronde, son nom, et ce qu'il me reste à en voir. Deux
 * segments en tête (`SingleChoiceSegmentedButtonRow`) choisissent la source ;
 * le « + » ouvre la recherche de la source affichée ; le retour de celle-ci
 * fait apparaître « Ajouté » ici, en snackbar.
 */
@Composable
fun SuivisScreen(
    vm: SuivisViewModel,
    onAjouter: () -> Unit,
    onOuvrir: (SourceSuivi, Int) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val etat = if (ui.source == SourceSuivi.REALISATEURS) ui.realisateurs else ui.sagas
    val snackbar = remember { SnackbarHostState() }
    // Clé fixe, même raison que sur l'accueil : `vm.messages` est un événement à un coup, et une
    // clé qui bougerait à chaque message couperait la snackbar avant ses deux secondes.
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
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
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SingleChoiceSegmentedButtonRow(Modifier.weight(1f)) {
                    SourceSuivi.entries.forEachIndexed { index, source ->
                        SegmentedButton(
                            selected = ui.source == source,
                            onClick = { vm.selectionnerSource(source) },
                            shape = SegmentedButtonDefaults.itemShape(index, SourceSuivi.entries.size),
                            label = { Text(source.titre) },
                        )
                    }
                }
                IconButton(onClick = onAjouter) {
                    Icon(Icons.Filled.Add, contentDescription = ui.source.libelleAjouter)
                }
            }

            etat.error?.let { e ->
                ErrorBlock(
                    e.message ?: "",
                    retryable = e.retryable,
                    onRetry = { vm.refresh(ui.source) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (etat.entites.isEmpty() && etat.error == null && !etat.loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        ui.source.libelleVide,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(etat.entites, key = { it.tmdbId }) { entite ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOuvrir(ui.source, entite.tmdbId) },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Portrait(entite.imageUrl, entite.nom, 40.dp)
                            Column {
                                Text(entite.nom, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    libelleLigne(etat.filmographies[entite.tmdbId] ?: EtatFilmographie.EnAttente),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * La photo d'une personne, ou l'affiche d'une saga : ronde, ou l'initiale sur
 * la même pastille quand TMDB n'en a pas. Jumeau de `Cover` (`ui/Cover.kt`)
 * pour les affiches rectangulaires — un `contentDescription` toujours posé,
 * photo ou non (design §8) ; rien *pendant* le chargement (aucun indicateur,
 * aucun repli tant que la requête est en vol — le commentaire d'ici disait
 * « rien pendant le chargement » sans plus de précision, corrigé par le geste
 * 9 du peaufinage du 23 septembre 2026, qui ajoute le fondu ci-dessous),
 * l'initiale restant le geste de l'absence d'image, pas celui d'une attente.
 */
@Composable
fun Portrait(url: String?, name: String, taille: Dp, modifier: Modifier = Modifier) {
    val description = "Photo de $name"
    val initiale: @Composable () -> Unit = {
        Box(
            Modifier.size(taille).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (url == null) {
        Box(modifier.clearAndSetSemantics { contentDescription = description }) { initiale() }
    } else {
        val contexte = LocalContext.current
        SubcomposeAsyncImage(
            // `crossfade(200)` (geste 9) : jumeau de `Cover`, la photo apparaît en fondu.
            model = ImageRequest.Builder(contexte).data(url).crossfade(200).build(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            loading = {},
            error = { initiale() },
            modifier = modifier.size(taille).clip(CircleShape),
        )
    }
}
