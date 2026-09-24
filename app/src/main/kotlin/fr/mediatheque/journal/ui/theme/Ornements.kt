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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition

/**
 * Les ornements de l'habillage « papier et pellicule » (23 septembre 2026) : une bande de trous de
 * pellicule (`Perforations`), un cadre à double filet or (`CadreOrne`) — repris du cartouche du
 * Voyage, généralisé à toute l'appli — et une bobine qui tourne (`BobineIndicateur`, complément du
 * 23 septembre 2026, geste 21 ; portée par l'animation Lottie `bobine-1.json` depuis le brief des
 * animations des célébrations, le même jour en soirée), l'indicateur de tirer-pour-rafraîchir de la
 * Frise et de l'accueil.
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
 * Ce que la bobine doit montrer, selon la progression du tirage — décision pure, sans Compose,
 * testée en JVM (`OrnementsTest.kt`). Au repos (`distanceFraction` nul, aucun chargement), elle
 * n'est pas visible : le repère de l'indicateur Material par défaut, caché hors écran au repos,
 * là où le dessin `Canvas` d'origine restait planté en haut du contenu (correctif du 24 septembre
 * 2026). Pendant le tirage, `decalage` et `opacite` montent avec `distanceFraction`, plafonnés à 1.
 * Pendant le chargement, elle reste à son décalage d'arrivée (1) et tourne — `isRefreshing`
 * l'emporte sur `distanceFraction`, que `PullToRefreshBox` peut avoir déjà ramené à 0 pendant que
 * le chargement se termine.
 */
data class PresentationBobine(val visible: Boolean, val decalage: Float, val opacite: Float)

fun presentationBobine(distanceFraction: Float, isRefreshing: Boolean): PresentationBobine {
    if (isRefreshing) return PresentationBobine(visible = true, decalage = 1f, opacite = 1f)
    val fraction = distanceFraction.coerceIn(0f, 1f)
    return PresentationBobine(visible = fraction > 0f, decalage = fraction, opacite = fraction)
}

/**
 * L'indicateur de tirer-pour-rafraîchir (geste 21 du complément du 23 septembre 2026 à
 * l'habillage ; porté par l'animation Lottie `bobine-1.json` depuis le brief des animations des
 * célébrations, le même jour en soirée, en remplacement du dessin `Canvas` d'origine — une caméra
 * de cinéma vintage sur pied, `assets/lottie/LICENCES.md`).
 *
 * `presentationBobine` décide si/où/comment elle se dessine (correctif du 24 septembre 2026:
 * l'indicateur restait affiché en permanence, `PullToRefreshDefaults.Indicator` de Material 3 sert
 * de repère — caché hors écran au repos, descend avec le doigt). Invisible, rien n'est composé :
 * ni place ni capteur de taps. Sinon, elle descend depuis le haut et s'éclaircit avec `decalage`
 * (`graphicsLayer.translationY` et `alpha`) pendant le tirage. Avant le déclenchement
 * (`isRefreshing` faux), la progression de l'animation suit `decalage` directement, par le
 * `progress` explicite de `LottieAnimation` : elle avance du tirage, elle ne joue pas toute seule.
 * Une fois `isRefreshing` vrai, elle boucle à sa cadence propre (`animateLottieCompositionAsState`,
 * `LottieConstants.IterateForever`) jusqu'à la fin du chargement.
 */
@Composable
fun BobineIndicateur(state: PullToRefreshState, isRefreshing: Boolean, modifier: Modifier = Modifier) {
    val presentation = presentationBobine(state.distanceFraction, isRefreshing)
    if (!presentation.visible) return

    val composition by rememberLottieComposition(LottieCompositionSpec.Asset("lottie/bobine-1.json"))
    val progressionBoucle by animateLottieCompositionAsState(
        composition,
        isPlaying = isRefreshing,
        iterations = LottieConstants.IterateForever,
    )
    LottieAnimation(
        composition = composition,
        progress = { if (isRefreshing) progressionBoucle else presentation.decalage },
        modifier = modifier
            .size(TAILLE_BOBINE)
            .graphicsLayer {
                translationY = -TAILLE_BOBINE.toPx() * (1f - presentation.decalage)
                alpha = presentation.opacite
            },
    )
}
