package fr.mediatheque.journal.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.subtitle
import fr.mediatheque.journal.ui.theme.IconeTabler

@Composable
fun SearchScreen(vm: SearchViewModel, onBack: () -> Unit, onPick: (SearchResult) -> Unit) {
    val ui by vm.ui.collectAsState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() } // le clavier suit le focus

    Column(Modifier.fillMaxSize().safeDrawingPadding()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack) { IconeTabler("arrow-left", "Retour") }
            TextField(
                value = ui.query,
                onValueChange = vm::onQueryChange,
                placeholder = { Text("Un titre de film") },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                trailingIcon = {
                    if (ui.query.isNotEmpty()) {
                        IconButton(onClick = { vm.onQueryChange("") }) { IconeTabler("x", "Effacer") }
                    }
                },
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
        }
        if (ui.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp), color = MaterialTheme.colorScheme.primary)
        }
        ui.error?.let { e ->
            ErrorBlock(e.message ?: "", retryable = e.retryable, onRetry = vm::retry, modifier = Modifier.padding(16.dp))
        }
        if (ui.error == null && ui.searched.isNotEmpty() && ui.results.isEmpty() && !ui.loading) {
            Text(
                "Rien trouvé pour “${ui.searched}”.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(16.dp),
            )
        }
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ui.results, key = { it.source + it.external_id }) { result ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(result) },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(result.cover_url, result.title, 56.dp, 84.dp)
                    Column {
                        Text(result.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            subtitle(result.metadata.director, result.year),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
