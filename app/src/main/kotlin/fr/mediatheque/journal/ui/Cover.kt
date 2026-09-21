package fr.mediatheque.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import coil3.compose.SubcomposeAsyncImage

/**
 * Décision 2 de la tâche 5 : un `contentDescription` toujours posé, jaquette
 * ou non — le brief le laissait vide sans jaquette, corrigé ici avec le titre
 * (design §8 : « une affiche dit “Affiche de {titre}” »), pour que l'initiale
 * de remplacement se lise aussi au lecteur d'écran.
 *
 * `colorFilter` (brief du 21 septembre 2026, l'étagère d'une salle du Voyage) : nul partout
 * ailleurs, il teinte l'affiche d'un film pas encore vu en sépia sans dupliquer ce composant.
 */
@Composable
fun Cover(url: String?, title: String, width: Dp, height: Dp, modifier: Modifier = Modifier, colorFilter: ColorFilter? = null) {
    val shape = MaterialTheme.shapes.small
    val description = "Affiche de $title"
    val initiale: @Composable () -> Unit = {
        Box(
            Modifier.size(width, height).background(MaterialTheme.colorScheme.surfaceContainerHigh, shape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                title.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (url == null) {
        Box(modifier.clearAndSetSemantics { contentDescription = description }) { initiale() }
    } else {
        SubcomposeAsyncImage(
            model = url,
            contentDescription = description,
            contentScale = ContentScale.Crop,
            colorFilter = colorFilter,
            // Rien au chargement d'une affiche (design §7) : le repli à l'initiale reste le geste
            // de l'absence de jaquette, pas celui d'une attente (revue de la vague finale, mineur
            // 9) — sinon chaque ligne d'une liste montre l'initiale une frame avant l'affiche.
            loading = {},
            error = { initiale() },
            modifier = modifier.size(width, height).clip(shape),
        )
    }
}
