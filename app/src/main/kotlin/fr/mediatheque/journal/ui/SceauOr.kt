package fr.mediatheque.journal.ui

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
 * Le sceau or : un cercle plein `secondary` portant une icône Tabler, posé sur une image. Né
 * « rétrospective complète » (`SceauRetrospective`, geste 20 du complément du 23 septembre 2026 à
 * l'habillage), il devient générique avec « Au ciné · le guichet » (25 septembre 2026), qui en
 * pose un `user` (réalisateur suivi) ou `movie` (saga suivie) sur les tuiles des sorties : une
 * seule forme pour toutes les marques d'or, l'icône et sa description en paramètres.
 *
 * `fond` est la couleur de ce qu'il y a dessous (le fond du monde sur la page d'un réalisateur,
 * `surfaceContainer` sur une carte, le fond de l'écran sur une affiche d'Au ciné), reprise pour son
 * liseré et son icône, qui se découpent ainsi dans l'or.
 *
 * `anime` : à l'échelle avec dépassement à la première composition (`LaunchedEffect(Unit)`, jamais
 * rejouée à une simple recomposition), statique ensuite, comme le sceau (plus petit) d'une année
 * ouverte (`Photogramme`, `AnneeScreen.kt`). Faux dans une liste ou une grille : ce qui défile ne
 * doit pas faire rebondir ses sceaux à chaque élément recomposé.
 */
@Composable
fun SceauOr(
    taille: Dp,
    anime: Boolean,
    icone: String,
    description: String,
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
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        // 16 dp dans 30 : la même proportion à toute taille.
        IconeTabler(icone, null, tint = fond, modifier = Modifier.size(taille * (16f / 30f)))
    }
}
