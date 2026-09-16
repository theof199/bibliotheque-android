package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.ui.Cover

/**
 * Le détail d'une année de la Frise (brief du 15 septembre 2026) : la grille
 * des films vus cette année-là (mêmes tuiles que l'accueil), puis « À voir
 * sur le Plex » — mêmes tuiles, un liseré pointillé à la place de la note,
 * pas de coche. Empilé depuis `Screen.Frise`, sans barre du bas.
 */
@Composable
fun AnneeScreen(
    annee: AnneeFrise,
    onBack: () -> Unit,
    onOpenVu: (JournalItem) -> Unit,
    onOpenAVoir: (PlexFilm) -> Unit,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val ecart = 8.dp
            val largeur = (maxWidth - ecart * 2) / 3
            val hauteur = largeur * 1.5f

            LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                        }
                        Text(annee.annee?.toString() ?: "Sans année", style = MaterialTheme.typography.titleLarge)
                    }
                }

                if (annee.vus.isEmpty()) {
                    item {
                        Text(
                            "Rien vu cette année.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    item {
                        TuilesEnLignes(annee.vus.chunked(3), ecart) { item ->
                            Box(Modifier.clickable { onOpenVu(item) }) {
                                Cover(item.media.cover_url, item.media.title, largeur, hauteur)
                                item.entry.rating?.let { note ->
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                            .clearAndSetSemantics { contentDescription = "Note $note sur 10" },
                                    ) {
                                        Text(
                                            "$note",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurface,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (annee.aVoir.isNotEmpty()) {
                    item { Text("À voir sur le Plex", style = MaterialTheme.typography.titleMedium) }
                    item {
                        TuilesEnLignes(annee.aVoir.chunked(3), ecart) { film ->
                            Box(
                                Modifier
                                    .clickable { onOpenAVoir(film) }
                                    .dashedBorder(MaterialTheme.colorScheme.onSurfaceVariant, cornerRadius = 8.dp),
                            ) {
                                Cover(film.cover_url, film.title, largeur, hauteur)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Une grille de tuiles, trois par ligne — même agencement que l'accueil et « Au ciné ». */
@Composable
private fun <T> TuilesEnLignes(rangees: List<List<T>>, ecart: Dp, tuile: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(ecart)) {
        rangees.forEach { rangee ->
            Row(horizontalArrangement = Arrangement.spacedBy(ecart), modifier = Modifier.fillMaxWidth()) {
                rangee.forEach { tuile(it) }
            }
        }
    }
}

/**
 * Le liseré pointillé qui distingue une tuile « à voir » d'une tuile vue
 * (brief du 15 septembre 2026) : pas de note, pas de coche, juste ce contour.
 * Repris tel quel (couleur libre) par l'étagère du rayon d'une décennie
 * (`DecennieScreen`, brief du 16 septembre 2026), d'où la visibilité de paquet.
 */
internal fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
    }
