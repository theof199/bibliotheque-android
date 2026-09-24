package fr.mediatheque.journal.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * Un état vide (point 16 de la revue du 24 septembre 2026) : une icône Tabler, une phrase, une
 * action — sur la recherche sans résultat, les suivis vides, le passeport vide, « Mes films »
 * vide. Aucune des six animations Lottie de l'appli (`assets/lottie/`) ne porte ce registre (un
 * clap, un trophée, des confettis, une bobine, un ticket, un projecteur — toutes des
 * célébrations, jamais une absence) : l'icône Tabler, seule option du point 16 qui reste juste,
 * les sert toutes les quatre plutôt que de forcer une animation hors de son sens.
 */
@Composable
fun EtatVide(icone: String, phrase: String, libelleAction: String, onAction: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconeTabler(icone, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
        Text(
            phrase,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Button(onClick = onAction, modifier = Modifier.padding(top = 16.dp)) { Text(libelleAction) }
    }
}
