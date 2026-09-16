package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.Cover
import kotlin.math.roundToInt

/**
 * Le rayon d'une décennie (brief du 16 septembre 2026) : son anneau de progression, puis une
 * étagère horizontale de ses films — vus en tuiles d'affiche notées, à voir en tuiles
 * pointillées corail — et dix puces année en dessous. Empilé depuis le calendrier de la Frise
 * (`Screen.Decennie`), sans barre du bas, comme `AnneeScreen`. Le retour système revient au
 * calendrier (`Root.kt`, `BackHandler` sur `nav.pop()`).
 */
@Composable
fun DecennieScreen(
    decennie: DecennieFrise,
    onBack: () -> Unit,
    onOuvrirVu: (JournalItem) -> Unit,
    onOuvrirAVoir: (PlexFilm) -> Unit,
    onOuvrirAnnee: (Int) -> Unit,
    /** Le Voyage (brief du 16 septembre 2026) : les années verrouillées se grisent, ici aussi. */
    voyage: VoyageUi = VoyageUi(),
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(Modifier.fillMaxWidth().padding(padding).padding(vertical = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 8.dp)) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text("Années ${decennie.decennie}", style = MaterialTheme.typography.titleLarge)
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                AnneauProgression(decennie.vus, decennie.aVoir)
                Text(
                    if (decennie.aVoir == 0) "${decennie.vus} vus" else "${decennie.vus} vus · ${decennie.aVoir} à voir",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
            ) {
                items(decennie.films) { film ->
                    when (film) {
                        is FilmDecennie.Vu -> TuileVue(film.item, onClick = { onOuvrirVu(film.item) })
                        is FilmDecennie.AVoir -> TuileAVoir(film.film, film.annee, onClick = { onOuvrirAVoir(film.film) })
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                decennie.annees.forEach { annee ->
                    // « Grisées si l'année n'a rien » (le constat, point 2), et désormais aussi si
                    // le Voyage la déclare verrouillée (brief du 16 septembre 2026) : opacité
                    // désactivée du design (§6, 38 %), jamais retirées de la rangée — les dix
                    // puces restent touchables, une année vide ou verrouillée ouvrant simplement
                    // l'écran correspondant.
                    val rien = annee.vus == 0 && annee.aVoir == 0
                    val verrouillee = statutVoyage(annee.annee, voyage) == StatutAnneeVoyage.VERROUILLEE
                    Text(
                        annee.annee.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .weight(1f)
                            .let { if (rien || verrouillee) it.alpha(0.38f) else it }
                            .clickable { onOuvrirAnnee(annee.annee) }
                            .padding(vertical = 6.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AnneauProgression(vus: Int, aVoir: Int) {
    val total = vus + aVoir
    val progression = if (total == 0) 1f else vus.toFloat() / total
    val pourcent = (progression * 100).roundToInt()
    Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(
            progress = { progression },
            modifier = Modifier.size(56.dp),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            strokeWidth = 4.dp,
        )
        Text(
            if (pourcent >= 100) "✓" else "$pourcent %",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/** Une tuile de film vu, jumelle de celle de l'accueil et d'`AnneeScreen` (affiche + pastille de note). */
@Composable
private fun TuileVue(item: JournalItem, onClick: () -> Unit) {
    Box(Modifier.clickable(onClick = onClick)) {
        Cover(item.media.cover_url, item.media.title, 52.dp, 78.dp)
        item.entry.rating?.let { note ->
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .clearAndSetSemantics { contentDescription = "Note $note sur 10" },
            ) {
                Text("$note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

/**
 * Une tuile « à voir », jumelle du liseré pointillé d'`AnneeScreen` mais en corail (le constat,
 * point 2) et avec l'année en bas : l'étagère mélange plusieurs années, contrairement à celle
 * d'`AnneeScreen`, une par écran, où l'année ne se répète pas.
 */
@Composable
private fun TuileAVoir(film: PlexFilm, annee: Int, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .dashedBorder(MaterialTheme.colorScheme.primary, cornerRadius = 8.dp),
    ) {
        Cover(film.cover_url, film.title, 52.dp, 78.dp)
        Text(
            annee.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.BottomStart).padding(4.dp),
        )
    }
}
