package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * L'ambiance en boucle d'un monde (delta de Léon du 25 septembre 2026, « pavillon par pavillon »,
 * §G ; machinerie de la livraison 3) : une touche animée par-dessus l'image de fond, entre elle et
 * la route (§B, ordre de z de `SectionMonde.kt`) — jamais sur les pavillons, jamais sur le carton,
 * qui joue sa propre entrée (`CartonTitre.kt`).
 *
 * `active` (`ambianceActive`, `VoyageCarte.kt`, pure) dit si la section est visible et si les
 * animations ne sont pas réduites : ce n'est que dans ce cas qu'une des fonctions ci-dessous lance
 * sa `rememberInfiniteTransition` — jumeau du `if (defilement)` de `Perforations`
 * (`theme/Ornements.kt`). Hors de là, l'ambiance ne dessine rien : contrairement au carton, ces
 * boucles n'ont pas d'« état de repos » à montrer — une bande qui glisse, arrêtée, n'ajoute rien à
 * l'image qu'elle décore.
 *
 * Livraison 3 : seuls 1930, 1940 et 1950 ont une ambiance (§G). 1960 → 2020 (livraison 4) et
 * 1890 → 1920 (livraison 5) n'auront qu'à ajouter leur propre branche ici, `k` et `active` déjà en
 * place — rien d'autre à reprendre dans `SectionMonde.kt`.
 */
@Composable
fun AmbianceDeMonde(monde: Monde, active: Boolean, k: Float, modifier: Modifier = Modifier) {
    when (monde.decennie) {
        1930 -> AmbianceDuParlant(active, k, monde.accent, modifier)
        1940 -> AmbianceDuNoir(active, k, monde.accent, modifier)
        1950 -> AmbianceDuTechnicolor(active, modifier)
    }
}

/**
 * 1930, « le parlant » (§G) : l'onde sonore court le long des deux bords de la route — deux bandes
 * de 14 dp, translatées de 0 à −24 dp en 1 s, linéaire, sans fin.
 */
@Composable
private fun AmbianceDuParlant(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1930")
    val decalage by transition.animateFloat(
        initialValue = 0f,
        targetValue = -24f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "onde",
    )
    Canvas(modifier) {
        val bande = accent.copy(alpha = 0.8f)
        val largeur = 14.dp.toPx() * k
        val dx = decalage.dp.toPx() * k
        val marge = 8.dp.toPx() * k
        // Bord gauche, en haut de la route (§G) : 230 dp de haut à partir de 76 dp.
        drawRect(bande, topLeft = Offset(marge + dx, 76.dp.toPx()), size = Size(largeur, 230.dp.toPx()))
        // Bord droit, en bas de la route : 260 dp de haut à partir de 622 dp.
        drawRect(bande, topLeft = Offset(size.width - largeur - marge + dx, 622.dp.toPx()), size = Size(largeur, 260.dp.toPx()))
    }
}

/**
 * 1940, « le noir » (§G) : les stores glissent sur l'image (bandes à 172°, aller-retour en 14 s) ;
 * une volute de fumée (approximée par un trait courbe, plutôt que l'illustration exacte de Léon)
 * monte au bord droit de la route en 9 s, sans fin.
 */
@Composable
private fun AmbianceDuNoir(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1940")
    val position by transition.animateFloat(
        initialValue = 0f,
        targetValue = 60f,
        animationSpec = infiniteRepeatable(tween(14_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "stores",
    )
    val fumee by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9_000, easing = LinearEasing)),
        label = "fumee",
    )
    Canvas(modifier) {
        rotate(172f) {
            val pas = 26.dp.toPx()
            val decalage = position.dp.toPx() % pas
            var y = -pas * 2 + decalage
            while (y < size.height + pas) {
                drawRect(Color(0xFFECE2CC).copy(alpha = 0.55f), topLeft = Offset(-size.width, y), size = Size(size.width * 3, 14.dp.toPx()))
                drawRect(Color.Black.copy(alpha = 0.55f), topLeft = Offset(-size.width, y + 14.dp.toPx()), size = Size(size.width * 3, 12.dp.toPx()))
                y += pas
            }
        }
        // La volute (§G) : opacité en triangle (elle apparaît puis s'efface), pas un simple fondu.
        val alphaFumee = if (fumee < 0.5f) fumee else 1f - fumee
        translate(left = size.width - 60.dp.toPx() * k, top = 560.dp.toPx() - fumee * 90.dp.toPx()) {
            scale(1f + fumee * 0.6f, 1f, pivot = Offset.Zero) {
                val chemin = Path().apply {
                    moveTo(0f, 80.dp.toPx())
                    cubicTo(20.dp.toPx(), 60.dp.toPx(), -10.dp.toPx(), 30.dp.toPx(), 10.dp.toPx(), 0f)
                }
                drawPath(chemin, accent.copy(alpha = alphaFumee * 0.5f), style = Stroke(width = 1.2.dp.toPx()))
            }
        }
    }
}

/**
 * 1950, « le Technicolor » (§G) : la respiration colorée sur l'image — un dégradé horizontal
 * rouge/vert/bleu en mode « screen », qui va et vient en 12 s.
 */
@Composable
private fun AmbianceDuTechnicolor(active: Boolean, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1950")
    val respiration by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "respiration",
    )
    val brush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFFFF3B3B).copy(alpha = 0.18f),
            Color(0xFF4ADE80).copy(alpha = 0.14f),
            Color(0xFF38BDF8).copy(alpha = 0.18f),
        ),
    )
    Canvas(modifier) {
        val dx = (-30f + respiration * 60f).dp.toPx()
        val alpha = 0.5f + respiration * 0.5f
        translate(left = dx) {
            drawRect(brush, alpha = alpha, blendMode = BlendMode.Screen)
        }
    }
}
