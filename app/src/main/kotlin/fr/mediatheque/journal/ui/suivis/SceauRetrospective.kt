package fr.mediatheque.journal.ui.suivis

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * Le sceau or « rétrospective complète » (geste 20 du complément du 23 septembre 2026 à
 * l'habillage) : un cercle plein avec `tabler:award` (geste 2 du brief du 23 septembre 2026 soir,
 * qui remplace le `✦` en Fraunces d'origine), posé sur le portrait.
 *
 * Sorti de `RealisateurScreen.kt` pour les Suivis (rétrospectives et cycles, 25 septembre 2026) :
 * `taille` 30 dp sur la page d'un réalisateur, 22 dp sur une carte de la liste ; `fond` est la
 * couleur de ce qu'il y a dessous (le fond du monde sur la page, `surfaceContainer` sur une carte),
 * reprise pour son liseré et son icône, qui se découpent ainsi dans l'or.
 *
 * `anime` : à l'échelle avec dépassement à la première composition (`LaunchedEffect(Unit)`, jamais
 * rejouée à une simple recomposition — l'interrupteur des introuvables juste en dessous, par
 * exemple), statique ensuite, comme le sceau (plus petit) d'une année ouverte (`Photogramme`,
 * `AnneeScreen.kt`). Faux sur une carte : une liste qui défile ne doit pas faire rebondir ses
 * sceaux à chaque carte recomposée.
 */
@Composable
fun SceauRetrospective(
    taille: Dp,
    anime: Boolean,
    modifier: Modifier = Modifier,
    fond: Color = MaterialTheme.colorScheme.surfaceContainer,
) {
    val echelle = remember { Animatable(if (anime) 0f else 1f) }
    if (anime) {
        LaunchedEffect(Unit) {
            echelle.animateTo(1f, tween(400, easing = CubicBezierEasing(0.3f, 1.6f, 0.4f, 1f)))
        }
    }
    Box(
        modifier
            .size(taille)
            .graphicsLayer { scaleX = echelle.value; scaleY = echelle.value }
            .background(MaterialTheme.colorScheme.secondary, CircleShape)
            .border(1.dp, fond, CircleShape)
            .semantics { contentDescription = "Rétrospective complète" },
        contentAlignment = Alignment.Center,
    ) {
        // 16 dp dans 30 : la même proportion à toute taille.
        IconeTabler("award", null, tint = fond, modifier = Modifier.size(taille * (16f / 30f)))
    }
}
