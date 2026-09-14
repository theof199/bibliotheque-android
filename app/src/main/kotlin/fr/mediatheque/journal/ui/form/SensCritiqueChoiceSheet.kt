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
 * plusieurs candidats, on demande. Le retour système ferme la feuille comme n'importe quelle autre
 * (Material 3 ne peut pas l'en empêcher) : `onDismiss` couvre ce cas au même titre qu'un choix —
 * la poussée part en file, sans décision mémorisée, et le geste se termine avec le message
 * « réessai au prochain lancement » (revue du 14 septembre 2026, critique 2 : `onDismissRequest =
 * {}` ne bloquait rien, la feuille disparaissait quand même et le geste restait bloqué, la note
 * déjà écrite au back).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SensCritiqueChoiceSheet(candidates: List<ExternalCandidate>, onChoose: (productId: Long?) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
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
                // `weight(1f, fill = false)` (mineur c de la revue du 14 septembre 2026) : la liste
                // prend l'espace disponible sans jamais le forcer — sans lui, une dizaine de
                // candidats poussait « Aucun de ceux-là » hors de l'écran, la Column n'étant pas
                // défilante (seule la LazyColumn l'est).
                LazyColumn(Modifier.weight(1f, fill = false)) {
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
