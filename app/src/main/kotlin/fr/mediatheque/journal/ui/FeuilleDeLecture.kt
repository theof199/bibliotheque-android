package fr.mediatheque.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieConstants
import fr.mediatheque.journal.ui.theme.Animation
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier

/**
 * Ce qu'une feuille de lecture montre (décision 6 du brief du 24 septembre 2026, « le voyage
 * revu ») : le texte une fois là, un chargement tant qu'il n'y est pas encore, ou le message du
 * back avec son bouton « Réessayer » quand il en a un.
 */
sealed interface EtatFeuilleDeLecture {
    data object Chargement : EtatFeuilleDeLecture
    data class Texte(val texte: String) : EtatFeuilleDeLecture
    data class Erreur(val message: String, val retryable: Boolean) : EtatFeuilleDeLecture
}

/**
 * Le composant unique des quatre pop-in du Voyage (décision 6 du brief du 24 septembre 2026, « le
 * voyage revu ») : l'ouverture d'une année, le contexte d'une salle, le carton d'un film et le
 * générique de fin — une feuille modale plein écran (Material 3), fond papier jauni, défilable,
 * fermable par geste ou par la croix. `titre` reste affiché pendant le chargement (le titre d'un
 * film, connu avant que son carton ne le soit) ; `onRetry` n'est appelé que sur une erreur
 * `retryable`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleDeLecture(titre: String, etat: EtatFeuilleDeLecture, onDismiss: () -> Unit, onRetry: () -> Unit = {}) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PapierJauni,
        modifier = Modifier.fillMaxHeight(0.92f),
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    titre,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextePapier,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss) { IconeTabler("x", "Fermer", tint = TextePapier) }
            }
            when (etat) {
                EtatFeuilleDeLecture.Chargement -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Animation(nom = "bobine-1", iterations = LottieConstants.IterateForever, modifier = Modifier.size(64.dp))
                }
                is EtatFeuilleDeLecture.Texte -> Text(
                    etat.texte,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextePapier,
                )
                is EtatFeuilleDeLecture.Erreur -> Column(
                    Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
                        .padding(16.dp),
                ) {
                    Text(etat.message, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
                    if (etat.retryable) {
                        androidx.compose.material3.TextButton(
                            onClick = onRetry,
                            modifier = Modifier.align(Alignment.End),
                        ) { Text("Réessayer") }
                    }
                }
            }
        }
    }
}
