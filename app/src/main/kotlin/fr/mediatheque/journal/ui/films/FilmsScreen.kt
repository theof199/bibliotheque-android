package fr.mediatheque.journal.ui.films

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.JournalRow
import fr.mediatheque.journal.ui.afficheVolante
import fr.mediatheque.journal.ui.voler
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.theme.Perforations
import kotlinx.coroutines.flow.distinctUntilChanged

// Pas de snackbar ici : cet écran n'a jamais reçu `nav`, rien ne pousse de message vers
// « Mes films ». Seul l'accueil collecte `nav.messages` (revue du tour de correction 1, et
// Critique 1 de la vague finale pour le mécanisme lui-même).
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FilmsScreen(
    vm: FilmsViewModel,
    onBack: () -> Unit,
    onOpen: (JournalItem) -> Unit,
    bottomBar: @Composable () -> Unit,
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : « Mes films » est un des
    // deux bouts de la paire vers « la fiche d'entrée » (`Screen.Edit`, `Root.kt`).
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val ui by vm.ui.collectAsState()
    val liste = rememberLazyListState()

    // Charger la suite quand la dernière ligne visible approche de la fin.
    LaunchedEffect(liste) {
        snapshotFlow { liste.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { index -> if (index != null && index >= ui.items.size - 5) vm.loadMore() }
    }

    // `Scaffold` plutôt que `safeDrawingPadding()` : son `bottomBar` (la barre du 14 septembre
    // 2026, « Profil » sélectionnée puisque cet écran ne s'ouvre que depuis lui) réserve sa
    // propre place dans le `padding` reçu ci-dessous, comme les insets système que
    // `safeDrawingPadding()` réservait seul avant elle.
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { IconeTabler("arrow-left", "Retour") }
                Text("Mes films", style = MaterialTheme.typography.titleLarge)
            }
            // Habillage « papier et pellicule » (23 septembre 2026, geste 4) : la bande de
            // perforations sous l'en-tête, comme sur l'accueil.
            Perforations(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            ui.error?.let { ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = vm::loadMore, modifier = Modifier.padding(16.dp)) }
            if (ui.items.isEmpty() && ui.endReached) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun film pour l’instant.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(state = liste, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ui.items, key = { it.entry.id }) { item ->
                        // La liste se retasse (geste 3 du peaufinage du 23 septembre 2026) au lieu
                        // de sauter quand un film change de place ou disparaît.
                        val volante = afficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-journal-${item.entry.id}")
                        JournalRow(
                            item,
                            onClick = { onOpen(item) },
                            modifier = Modifier.animateItem(),
                            coverModifier = Modifier.voler(volante),
                        )
                    }
                    if (ui.loading) {
                        item {
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
