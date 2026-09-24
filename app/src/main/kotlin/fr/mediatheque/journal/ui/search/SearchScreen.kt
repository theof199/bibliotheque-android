package fr.mediatheque.journal.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
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
import fr.mediatheque.journal.ui.EtatVide
import fr.mediatheque.journal.ui.subtitle
import fr.mediatheque.journal.ui.theme.IconeTabler

@Composable
fun SearchScreen(
    vm: SearchViewModel,
    onBack: () -> Unit,
    onPick: (SearchResult) -> Unit,
    /** « Tes Ensuite » (point 6 de la revue du 24 septembre 2026) : les mêmes films que le carrousel de l'accueil (`cartesEnsuite`), déjà convertis en `SearchResult`. */
    ensuite: List<SearchResult> = emptyList(),
    /** Les films non vus de tes salles du Voyage de l'année en cours (point 6), déjà convertis en `SearchResult`. */
    aVoirCetteAnnee: List<SearchResult> = emptyList(),
    /** `tmdb_id` → ma note (nulle si je l'ai vu sans noter) pour le repère « vu · 7 » sur un résultat (point 6). */
    dejaAuJournal: Map<Int, Int?> = emptyMap(),
) {
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
                // Une icône loupe dans le champ (point 6) : jamais posée avant cette revue.
                leadingIcon = { IconeTabler("search", null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
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
            // État vide (point 16 de la revue du 24 septembre 2026) : une icône, la phrase, une
            // action — « Effacer » vide le champ pour recommencer, plutôt qu'un texte seul.
            EtatVide(
                icone = "search",
                phrase = "Rien trouvé pour “${ui.searched}”.",
                libelleAction = "Effacer",
                onAction = { vm.onQueryChange("") },
                modifier = Modifier.padding(16.dp),
            )
        }
        if (ui.query.isBlank()) {
            // Avant la saisie (point 6) : trois sections, chacune absente si elle n'a rien à
            // proposer — jamais un titre de section vide.
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
                if (ui.recentes.isNotEmpty()) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text("Tes dernières recherches", style = MaterialTheme.typography.titleMedium)
                                IconButton(onClick = vm::effacerRecherchesRecentes) { IconeTabler("trash", "Effacer les dernières recherches") }
                            }
                            RowDeRecherchesRecentes(ui.recentes, onChoisir = vm::onQueryChange)
                        }
                    }
                }
                if (ensuite.isNotEmpty()) {
                    item { Text("Tes « Ensuite »", style = MaterialTheme.typography.titleMedium) }
                    items(ensuite, key = { "ensuite:" + it.source + it.external_id }) { result ->
                        LigneResultat(result, dejaAuJournal, onClick = { onPick(result) })
                    }
                }
                if (aVoirCetteAnnee.isNotEmpty()) {
                    item { Text("Pas encore vus, cette année du Voyage", style = MaterialTheme.typography.titleMedium) }
                    items(aVoirCetteAnnee, key = { "voyage:" + it.source + it.external_id }) { result ->
                        LigneResultat(result, dejaAuJournal, onClick = { onPick(result) })
                    }
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(ui.results, key = { it.source + it.external_id }) { result ->
                    LigneResultat(result, dejaAuJournal, onClick = { onPick(result) })
                }
            }
        }
    }
}

/**
 * Une ligne de résultat, réutilisée pour les résultats de recherche et les deux sections
 * d'avant-saisie (point 6) : affiche, titre, réalisateur si le résultat le porte (`subtitle`
 * l'omet sinon plutôt que de laisser une virgule seule), et « vu · 7 » (ou « vu » sans note) si le
 * film est déjà au journal, retrouvé par `tmdb_id`.
 */
@Composable
private fun LigneResultat(result: SearchResult, dejaAuJournal: Map<Int, Int?>, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(result.cover_url, result.title, 56.dp, 84.dp)
        Column(Modifier.weight(1f)) {
            Text(result.title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle(result.metadata.director, result.year),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val tmdbId = result.external_id.toIntOrNull()
        if (tmdbId != null && dejaAuJournal.containsKey(tmdbId)) {
            val note = dejaAuJournal[tmdbId]
            Text(
                if (note != null) "vu · $note" else "vu",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Les dernières recherches, en puces qu'un tap relance (point 6). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RowDeRecherchesRecentes(recentes: List<String>, onChoisir: (String) -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        for (requete in recentes) {
            FilterChip(
                selected = false,
                onClick = { onChoisir(requete) },
                label = { Text(requete, style = MaterialTheme.typography.bodyMedium) },
                shape = CircleShape,
            )
        }
    }
}
