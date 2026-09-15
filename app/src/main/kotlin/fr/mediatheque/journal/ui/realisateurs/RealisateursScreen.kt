package fr.mediatheque.journal.ui.realisateurs

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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.SubcomposeAsyncImage
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.showBriefly

/**
 * Les réalisateurs que je suis (brief du 15 septembre 2026) : une ligne par personne, sa photo
 * ronde, son nom, et ce qu'il me reste à voir d'elle. Le « + » en haut à droite ouvre la
 * recherche ; le retour de celle-ci fait apparaître « Ajouté » ici, en snackbar.
 */
@Composable
fun RealisateursScreen(
    vm: RealisateursViewModel,
    onAjouter: () -> Unit,
    onOuvrir: (Int) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
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
                Text("Réalisateurs", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onAjouter) {
                    Icon(Icons.Filled.Add, contentDescription = "Ajouter un réalisateur")
                }
            }

            ui.error?.let { e ->
                ErrorBlock(
                    e.message ?: "",
                    retryable = e.retryable,
                    onRetry = vm::refresh,
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (ui.realisateurs.isEmpty() && ui.error == null && !ui.loading) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        "Ajoute un réalisateur avec +",
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
                    items(ui.realisateurs, key = { it.tmdb_id }) { realisateur ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOuvrir(realisateur.tmdb_id) },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Portrait(realisateur.profile_url, realisateur.name, 40.dp)
                            Column {
                                Text(realisateur.name, style = MaterialTheme.typography.titleMedium)
                                Text(
                                    libelleLigne(ui.filmographies[realisateur.tmdb_id] ?: EtatFilmographie.EnAttente),
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
 * La photo d'une personne : ronde, ou son initiale sur la même pastille quand TMDB n'en a pas.
 * Jumeau de `Cover` (`ui/Cover.kt`) pour les affiches — un `contentDescription` toujours posé,
 * photo ou non (design §8), et rien pendant le chargement, pour que l'initiale reste le geste de
 * l'absence de photo et pas celui d'une attente.
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
        SubcomposeAsyncImage(
            model = url,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            loading = {},
            error = { initiale() },
            modifier = modifier.size(taille).clip(CircleShape),
        )
    }
}
