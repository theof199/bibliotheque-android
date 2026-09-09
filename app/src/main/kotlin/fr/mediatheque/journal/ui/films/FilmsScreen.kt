package fr.mediatheque.journal.ui.films

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.formatDate
import kotlinx.coroutines.flow.distinctUntilChanged

@Composable
fun FilmsScreen(vm: FilmsViewModel, message: String?, onMessageShown: () -> Unit, onBack: () -> Unit, onOpen: (JournalItem) -> Unit) {
    val ui by vm.ui.collectAsState()
    val liste = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) { if (message != null) { snackbar.showSnackbar(message); onMessageShown() } }

    // Charger la suite quand la dernière ligne visible approche de la fin.
    LaunchedEffect(liste) {
        snapshotFlow { liste.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { index -> if (index != null && index >= ui.items.size - 5) vm.loadMore() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(snackbarData = data, containerColor = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = MaterialTheme.colorScheme.onSurface)
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).safeDrawingPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
                Text("Mes films", style = MaterialTheme.typography.titleLarge)
            }
            ui.error?.let { ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = vm::loadMore, modifier = Modifier.padding(16.dp)) }
            if (ui.items.isEmpty() && ui.endReached) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Aucun film pour l’instant.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(state = liste, contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(ui.items, key = { it.entry.id }) { item ->
                        Row(
                            Modifier.fillMaxWidth().clickable { onOpen(item) },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(item.media.cover_url, item.media.title, 56.dp, 84.dp)
                            Column(Modifier.weight(1f)) {
                                Text(item.media.title, style = MaterialTheme.typography.titleMedium)
                                Text(formatDate(item.entry.finished_at), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                if (item.carnet.reactions.isNotEmpty()) {
                                    Text(item.carnet.reactions.joinToString(" ") { Reactions.emoji(it) }, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                            item.entry.rating?.let { Text("$it", style = MaterialTheme.typography.titleMedium) }
                        }
                    }
                    if (ui.loading || !ui.endReached) {
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
