package fr.mediatheque.journal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.reactions.Reactions

/**
 * Une ligne de journal : affiche, titre, date, emojis des réactions, note à
 * droite. Celle de « Mes films » (`FilmsScreen.kt`) — et, depuis le brief du
 * 14 septembre 2026, celle de « Tes séances » sur l'écran « Au ciné »
 * (`ui/cinema/AuCineScreen.kt`), qui doit rester « même ligne que Mes films »
 * à la lettre, pas seulement à l'œil. Un seul endroit, pour que les deux ne
 * divergent jamais.
 */
@Composable
fun JournalRow(
    item: JournalItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : un modificateur ordinaire,
    // pas un `AfficheVolante` — jamais de type expérimental dans cette signature, sinon « Tes
    // séances » (`AuCineScreen.kt`, qui n'a pas de paire déclarée) aurait dû s'y plier aussi. Nul
    // ici ; `Modifier.voler(volante)` posé par « Mes films » (`FilmsScreen.kt`).
    coverModifier: Modifier = Modifier,
) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(item.media.cover_url, item.media.title, 56.dp, 84.dp, modifier = coverModifier)
        Column(Modifier.weight(1f)) {
            Text(item.media.title, style = MaterialTheme.typography.titleMedium)
            Text(
                formatDate(item.entry.finished_at),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (item.carnet.reactions.isNotEmpty()) {
                Text(item.carnet.reactions.joinToString(" ") { Reactions.emoji(it) }, style = MaterialTheme.typography.bodyMedium)
            }
        }
        // La note en petit cercle or (habillage du 23 septembre 2026, geste 4), plutôt qu'un
        // chiffre nu — jumeau du cercle de la grille de l'accueil et de la fiche d'un film.
        item.entry.rating?.let { note ->
            Box(
                Modifier
                    .size(26.dp)
                    .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$note", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}
