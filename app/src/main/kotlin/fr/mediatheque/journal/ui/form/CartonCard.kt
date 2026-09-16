package fr.mediatheque.journal.ui.form

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.frise.EtatChronique
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier

/**
 * La carte « Et pendant ce temps… » (brief du 16 septembre 2026) : même cartouche kitsch que
 * `Cartouche` (`ui/frise/AnneeScreen.kt`) — papier jauni, cadre ornementé — pour le carton d'un
 * film plutôt que le récit d'une année.
 *
 * `attente` distingue les deux emplacements : `true` après un `create` (le chroniqueur peut être
 * encore en train d'écrire, « Le chroniqueur arrive… » puis, après dix essais, « Il sera dans la
 * fiche du film ») ; `false` en bas de `Screen.Edit`, où la carte ne s'affiche que si le carton
 * est déjà prêt — silencieuse sinon, jamais « en préparation » sur un écran de correction.
 */
@Composable
fun CartonCard(ui: CartonUi, attente: Boolean, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (ui.etat == EtatChronique.NON_CONFIGURE) return
    if (!attente && ui.etat != EtatChronique.PRETE) return

    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier
            .fillMaxWidth()
            .background(PapierJauni, shape)
            .border(1.dp, CadrePapier, shape)
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Et pendant ce temps…",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextePapier,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Fermer", tint = TextePapier)
                }
            }
            when (ui.etat) {
                EtatChronique.PRETE -> {
                    ui.contexte?.let { Text(it, style = MaterialTheme.typography.bodyLarge, color = TextePapier) }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        ui.faits.forEach { fait -> Text("· $fait", style = MaterialTheme.typography.bodyMedium, color = TextePapier) }
                    }
                }
                EtatChronique.EN_PREPARATION -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TextePapier)
                    Text("Le chroniqueur arrive…", style = MaterialTheme.typography.bodyMedium, color = TextePapier)
                }
                EtatChronique.ABANDON -> Text(
                    "Il sera dans la fiche du film.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextePapier,
                )
                EtatChronique.NON_CONFIGURE -> {}
            }
        }
    }
}
