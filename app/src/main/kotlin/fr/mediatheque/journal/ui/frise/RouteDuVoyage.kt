package fr.mediatheque.journal.ui.frise

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * La route d'une section du Voyage (delta de Léon du 25 septembre 2026, « pavillon par
 * pavillon », §B) — remplace l'ancienne pellicule serpentine dessinée au `Canvas`
 * (`Pellicule.kt`).
 *
 * Le tracé de Léon est donné dans un repère de 390 dp de large : `k = maxWidth / 390.dp` (posé par
 * l'appelant, `SectionMonde.kt`) étire les abscisses (et les rayons des virages, des arcs
 * d'ellipse à l'œil invisibles) sur la largeur réelle de l'écran ; les ordonnées, elles, restent
 * telles quelles — la section garde toujours 650 dp de haut. `PathParser` (déjà utilisé par
 * `glypheRecompense`) fait l'analyse du tracé SVG une fois construit avec ces abscisses à
 * l'échelle ; le chemin qui en sort est en dp, mis à l'échelle de l'écran (densité) par
 * `DrawScope.scale` au dessin, comme `glypheRecompense` le fait déjà pour sa propre échelle.
 */
private fun cheminRoute(k: Float): String {
    val x0 = 0f
    val x130 = 130f * k
    val x230 = 230f * k
    val x390 = 390f * k
    val r = 60f * k
    return "M$x0 118 H$x230 A$r 60 0 0 1 $x230 238 H$x130 A$r 60 0 0 0 $x130 358 " +
        "H$x230 A$r 60 0 0 1 $x230 478 H$x130 A$r 60 0 0 0 $x130 598 H$x390"
}

/**
 * Le chemin de la route, analysé une fois par largeur d'écran (revue du 28 septembre 2026,
 * retouche de la livraison 1) — `remember(k)` évite de refaire l'analyse SVG à chaque frame
 * dessinée par `Canvas` ; `k` ne change qu'à une rotation d'écran ou un changement d'appareil.
 */
@Composable
fun rememberCheminRoute(k: Float): Path = remember(k) { PathParser().parsePathString(cheminRoute(k)).toPath() }

/**
 * `magnetique` (le monde 1980, « bande magnétique ») resserre le pointillé (2/4 au lieu de 5/9)
 * et l'assourdit (.35 d'alpha au lieu de .6) — la route se lit alors comme une bande VHS plutôt
 * que comme une route de studio.
 */
fun DrawScope.dessinerRoute(chemin: Path, magnetique: Boolean) {
    scale(density, density, pivot = Offset.Zero) {
        drawPath(chemin, Color(0xFF0F0C08), style = Stroke(width = 64f))
        drawPath(
            chemin,
            Color(0xFFECE2CC).copy(alpha = if (magnetique) 0.35f else 0.6f),
            style = Stroke(
                width = 64f,
                pathEffect = if (magnetique) {
                    PathEffect.dashPathEffect(floatArrayOf(2f, 4f))
                } else {
                    PathEffect.dashPathEffect(floatArrayOf(5f, 9f))
                },
            ),
        )
        drawPath(chemin, Color(0xFF1A1410), style = Stroke(width = 48f))
    }
}

/**
 * Le cône de lumière de l'année en cours (§B) : un triangle en dégradé linéaire, une flaque en
 * dégradé radial — posé entre la route et les photogrammes, sans animation en livraison 1 (aucune
 * ambiance avant la livraison 3 du delta).
 */
fun DrawScope.coneDeLumiere(k: Float, corail: Color) {
    scale(density, density, pivot = Offset.Zero) {
        val sommet = Offset(382f * k, 326f)
        val basGauche = Offset(240f * k, 448f)
        val basDroit = Offset(344f * k, 448f)
        val chemin = androidx.compose.ui.graphics.Path().apply {
            moveTo(sommet.x, sommet.y)
            lineTo(basGauche.x, basGauche.y)
            lineTo(basDroit.x, basDroit.y)
            close()
        }
        drawPath(
            chemin,
            brush = Brush.linearGradient(
                colors = listOf(corail.copy(alpha = 0.5f), corail.copy(alpha = 0.08f)),
                start = sommet,
                end = basGauche,
            ),
        )
        val centreFlaque = Offset(290f * k, 444f)
        val rx = 56f * k
        val ry = 14f
        drawOval(
            brush = Brush.radialGradient(
                colors = listOf(corail.copy(alpha = 0.35f), Color.Transparent),
                center = centreFlaque,
                radius = maxOf(rx, ry),
            ),
            topLeft = Offset(centreFlaque.x - rx, centreFlaque.y - ry),
            size = androidx.compose.ui.geometry.Size(rx * 2f, ry * 2f),
        )
    }
}
