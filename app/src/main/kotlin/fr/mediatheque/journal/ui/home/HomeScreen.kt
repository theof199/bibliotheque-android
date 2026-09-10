package fr.mediatheque.journal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
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

            Column(
                // 48 dp en tête : la place de l'`IconButton` profil, dessiné par-dessus (aligné
                // `TopEnd` plus bas). Sur la `Column` elle-même, pas sur la seule grille : sans
                // cette marge ici, l'`ErrorBlock` et le texte « Aucun film pour l'instant. », qui
                // partagent la `Column` avec la grille, démarraient eux aussi à y = 0 et
                // passaient sous l'icône, qui les recouvre — elle est déclarée après dans le
                // `Box`, donc dessinée et touchée en premier (relecture, correction 1 puis 3).
                Modifier.fillMaxSize().padding(top = 48.dp),
            ) {
                ui.error?.let {
                    ErrorBlock(
                        it.message ?: "",
                        retryable = it.retryable,
                        onRetry = vm::loadMore,
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
                if (ui.items.isEmpty() && ui.endReached) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
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
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ecart),
                        verticalArrangement = Arrangement.spacedBy(ecart),
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
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            // Design §8 : une pastille dit « Note {n} sur 10 », pas
                                            // le chiffre nu que `Text` donnerait seul à TalkBack
                                            // (relecture, correction 3).
                                            .clearAndSetSemantics { contentDescription = "Note $note sur 10" },
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

            IconButton(onClick = onProfile, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Filled.Person, contentDescription = "Profil", tint = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}
