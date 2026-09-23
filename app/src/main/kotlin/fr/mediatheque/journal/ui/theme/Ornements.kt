package fr.mediatheque.journal.ui.theme

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Les ornements de l'habillage « papier et pellicule » (23 septembre 2026) : une bande de trous de
 * pellicule (`Perforations`), un cadre à double filet or (`CadreOrne`) — repris du cartouche du
 * Voyage, généralisé à toute l'appli — et une bobine qui tourne (`BobineIndicateur`, complément du
 * 23 septembre 2026, geste 21), l'indicateur de tirer-pour-rafraîchir de la Frise et de l'accueil.
 */

/** L'espacement d'une perforation à l'autre — la maquette : `background-size:16px 10px`. */
private val PAS_PERFORATION: Dp = 16.dp
private val RAYON_PERFORATION: Dp = 2.2.dp

/**
 * Une bande de trous de pellicule, 10 dp de haut, sur toute la largeur. `defilement` anime un
 * défilement lent (3,5 s par pas, linéaire, sans fin) — la maquette : `perfScroll 3.5s linear
 * infinite`. Sans lui, la bande est fixe : le sceau d'une année déjà bouclée n'a pas à onduler.
 */
@Composable
fun Perforations(
    modifier: Modifier = Modifier,
    defilement: Boolean = false,
    couleur: Color = MaterialTheme.colorScheme.background,
    allumees: Int = 0,
) {
    val phase = if (defilement) {
        val transition = rememberInfiniteTransition(label = "perforations")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(3500, easing = LinearEasing)),
            label = "defilement",
        ).value
    } else {
        0f
    }
    val or = MaterialTheme.colorScheme.secondary
    Canvas(modifier.fillMaxWidth().height(10.dp)) {
        val pas = PAS_PERFORATION.toPx()
        val rayon = RAYON_PERFORATION.toPx()
        val decalage = phase * pas
        var index = 0
        var x = -pas + decalage
        while (x < size.width + pas) {
            val teinte = if (index < allumees) or else couleur
            drawCircle(teinte.copy(alpha = opacitePerforation(index, allumees)), radius = rayon, center = Offset(x, size.height / 2f))
            x += pas
            index++
        }
    }
}

/**
 * L'opacité d'une perforation le long de la bande : pleine (1) une fois « allumée » — la
 * célébration d'une salle bouclée (geste 10) les allume une à une en or — sinon le grain de base
 * de la pellicule au repos (0,5, la maquette : `#papier .perfs{opacity:.5}`). Fonction pure,
 * testée en JVM : `opacitePerforationTest.kt`.
 */
fun opacitePerforation(index: Int, allumees: Int): Float = if (index < allumees) 1f else 0.5f

/**
 * Le cartouche à double filet or, coins arrondis — le cadre déjà dessiné pour le papier jauni du
 * Voyage (`.cartouche::before` de la maquette : un filet plein, un second en retrait à demi
 * opacité), généralisé à un `couleur` quelconque, `Or` par défaut via `colorScheme.secondary`.
 */
@Composable
fun CadreOrne(
    modifier: Modifier = Modifier,
    couleur: Color = MaterialTheme.colorScheme.secondary,
    coin: Dp = 16.dp,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.drawBehind {
            drawRoundRect(
                couleur,
                cornerRadius = CornerRadius(coin.toPx()),
                style = Stroke(width = 1.dp.toPx()),
            )
            val retrait = 5.dp.toPx()
            val coinInterieur = (coin.toPx() - retrait).coerceAtLeast(0f)
            drawRoundRect(
                couleur.copy(alpha = 0.5f),
                topLeft = Offset(retrait, retrait),
                size = Size(size.width - retrait * 2, size.height - retrait * 2),
                cornerRadius = CornerRadius(coinInterieur),
                style = Stroke(width = 0.75.dp.toPx()),
            )
        },
        content = content,
    )
}

private val TAILLE_BOBINE: Dp = 32.dp

/**
 * L'indicateur de tirer-pour-rafraîchir, une bobine de pellicule (geste 21 du complément du
 * 23 septembre 2026 à l'habillage) — deux cercles, des rayons, des perforations — dessinée une
 * fois au `Canvas`, jamais une image : `Modifier.graphicsLayer` la fait tourner et grandir plutôt
 * que de redessiner des points pivotés à chaque frame.
 *
 * Avant le déclenchement (`isRefreshing` faux), la rotation suit `state.distanceFraction` (0 → 1,
 * la distance tirée) : la bobine tourne du tirage, elle ne file pas toute seule. Une fois
 * `isRefreshing` vrai, elle tourne en boucle (900 ms par tour, linéaire) jusqu'à la fin du
 * chargement — `PullToRefreshBox` retire alors l'indicateur lui-même, rien à arrêter ici.
 */
@Composable
fun BobineIndicateur(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val distance = state.distanceFraction.coerceIn(0f, 1f)
    val rotation = if (isRefreshing) {
        val transition = rememberInfiniteTransition(label = "bobine-chargement")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing)),
            label = "rotation",
        ).value
    } else {
        distance * 360f
    }
    val or = MaterialTheme.colorScheme.secondary
    Box(
        modifier
            .size(TAILLE_BOBINE)
            .graphicsLayer {
                rotationZ = rotation
                val echelle = if (isRefreshing) 1f else distance
                scaleX = echelle
                scaleY = echelle
                alpha = echelle
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(TAILLE_BOBINE)) {
            val rayonExterieur = size.minDimension / 2f
            val rayonMoyeu = rayonExterieur * 0.26f
            val epaisseurJante = rayonExterieur * 0.12f
            drawCircle(or, radius = rayonExterieur - epaisseurJante / 2f, style = Stroke(width = epaisseurJante))
            drawCircle(or, radius = rayonMoyeu)
            // Six rayons du moyeu à la jante, et une perforation ronde entre chaque paire.
            repeat(6) { i ->
                val angle = i * 60f
                rotate(angle, pivot = center) {
                    drawLine(
                        or,
                        start = Offset(center.x, center.y - rayonMoyeu),
                        end = Offset(center.x, center.y - rayonExterieur + epaisseurJante),
                        strokeWidth = epaisseurJante * 0.6f,
                    )
                    drawCircle(
                        or,
                        radius = rayonExterieur * 0.1f,
                        center = Offset(center.x, center.y - rayonExterieur * 0.62f),
                    )
                }
            }
        }
    }
}
