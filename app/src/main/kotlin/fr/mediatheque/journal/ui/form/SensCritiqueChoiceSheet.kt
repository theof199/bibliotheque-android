package fr.mediatheque.journal.ui.form

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.senscritique.ExternalCandidate
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.subtitle

/**
 * « Lequel sur SensCritique ? » (brief du 14 septembre 2026) : la résolution a rendu zéro ou
 * plusieurs candidats, on demande. Pas de dismiss par le scrim ou le retour système — un geste
 * explicite (un candidat, ou « Aucun de ceux-là ») est le seul moyen d'en sortir, le geste local
 * attend cette réponse avant de finir (`FormViewModel.syncSensCritique`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensCritiqueChoiceSheet(candidates: List<ExternalCandidate>, onChoose: (productId: Long?) -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = {}, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                "Lequel sur SensCritique ?",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (candidates.isEmpty()) {
                Text(
                    "Rien trouvé chez eux pour ce titre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            } else {
                LazyColumn {
                    items(candidates, key = { it.productId }) { candidat ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onChoose(candidat.productId) }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Cover(candidat.pictureUrl, candidat.title, 56.dp, 84.dp)
                            Column(Modifier.align(Alignment.CenterVertically)) {
                                Text(candidat.title, style = MaterialTheme.typography.bodyLarge)
                                val sous = subtitle(candidat.director, candidat.year)
                                if (sous.isNotEmpty()) {
                                    Text(sous, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            TextButton(onClick = { onChoose(null) }, modifier = Modifier.padding(vertical = 8.dp)) {
                Text("Aucun de ceux-là")
            }
        }
    }
}
