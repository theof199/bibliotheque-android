package fr.mediatheque.journal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.Navigator
import fr.mediatheque.journal.ui.films.FilmsViewModel
import fr.mediatheque.journal.ui.showBriefly
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Demandé par le propriétaire le 10 septembre 2026, après le premier essai sur téléphone :
 * l'accueil montre les films vus, jaquettes seules, du plus récent au plus ancien, au-dessus
 * du bouton « Ajouter un film » qui descend en bas. Le `vm` est le même `FilmsViewModel` que
 * « Mes films » (clé `"films"` dans `Root.kt`) : une seule source, deux présentations.
 */
@Composable
fun HomeScreen(vm: FilmsViewModel, nav: Navigator, onAdd: () -> Unit, onProfile: () -> Unit, onOpen: (JournalItem) -> Unit) {
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
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            // Trois colonnes, 8 dp d'écart (grille du design §4) : `Cover` prend une largeur et
            // une hauteur fixes, pas un modificateur élastique, donc la taille d'une jaquette se
            // déduit ici de la largeur disponible plutôt que d'être posée dans `Cover` lui-même.
            val ecart = 8.dp
            val largeurJaquette = (maxWidth - ecart * 2) / 3
            val hauteurJaquette = largeurJaquette * 1.5f

            Column(Modifier.fillMaxSize()) {
                ui.error?.let {
                    ErrorBlock(
                        it.message ?: "",
                        retryable = it.retryable,
                        onRetry = vm::loadMore,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
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
                        horizontalArrangement = Arrangement.spacedBy(ecart),
                        verticalArrangement = Arrangement.spacedBy(ecart),
                        // 52 dp de bouton, 16 dp d'écart au-dessus : la grille ne défile jamais dessous.
                        contentPadding = PaddingValues(bottom = 68.dp),
                    ) {
                        items(ui.items, key = { it.entry.id }) { item ->
                            Box(Modifier.clickable { onOpen(item) }) {
                                Cover(item.media.cover_url, item.media.title, largeurJaquette, hauteurJaquette)
                                item.entry.rating?.let { note ->
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 2.dp),
                                    ) {
                                        Text(
                                            "$note",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
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

            IconButton(onClick = onProfile, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Person, contentDescription = "Profil", tint = MaterialTheme.colorScheme.onSurface)
            }
            Button(
                onClick = onAdd,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(52.dp),
            ) { Text("Ajouter un film") }
        }
    }
}
