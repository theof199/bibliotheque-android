package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.Limelight

/**
 * Le carton-titre d'un monde (delta de Léon du 25 septembre 2026, « pavillon par pavillon », §D) :
 * 358 × 76 dp, en tête de chaque section de la route — remplace l'ancien carton plein écran et
 * son animation de projecteur.
 *
 * Livraison 1 : `entree` est toujours `EtatEntree.Jouee` (aucune ambiance ni animation d'entrée
 * avant la livraison 3 du delta) — le paramètre est déjà là pour ne pas retoucher la signature
 * quand l'entrée animée arrivera, mais le carton se dessine à l'identique quel que soit son état
 * tant que l'animation n'existe pas.
 *
 * Retouche du 28 septembre 2026 (revue de la livraison 1) : les cadres par décennie du §G, tous
 * statiques — 1910 (le plus orné : fond, filet pointillé, fleurons), 1930 (filet), 1960 (bord
 * pointillé), 1980 (néon rose), 1990 (titre en lettres d'acier), 2000/2010 (coins arrondis),
 * 2020 (liseré). Rien n'y bouge : l'entrée animée elle-même reste pour la livraison 3.
 */
@Composable
fun CartonTitre(monde: Monde, entree: EtatEntree, modifier: Modifier = Modifier) {
    val decennie = monde.decennie
    val fond = if (decennie == 1910) Color(0xFF3A2A14) else FondCarton
    // Coins vifs par défaut (§D ne donne pas de rayon) ; seuls 2000 et 2010 en demandent un (§G).
    val rayon = when (decennie) { 2000 -> 4.dp; 2010 -> 6.dp; else -> 0.dp }
    val shape = RoundedCornerShape(rayon)
    val bordCouleur = when (decennie) {
        1980 -> Color(0xFFFF4FD8)
        2020 -> MaterialTheme.colorScheme.secondary
        else -> monde.accent
    }

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(76.dp)
            .background(fond, shape)
            .let {
                if (decennie == 1960) it.dashedBorder(bordCouleur, cornerRadius = rayon, strokeWidth = 1.dp) else it.border(1.dp, bordCouleur, shape)
            }
            .drawWithContent {
                drawContent()
                decorationsDeCarton(decennie, bordCouleur)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // .1 em à 20 sp (§D) : 2 sp — la même conversion, en littéral, que le reste de
            // l'écran du Voyage (`letterSpacing` y est toujours posé en `sp`, jamais en `em`).
            Text(
                monde.nom.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(
                    fontFamily = Limelight,
                    letterSpacing = 2.sp,
                    fontSize = 20.sp,
                    brush = if (decennie == 1990) DegradeAcier else null,
                    shadow = if (decennie == 1980) Shadow(Color(0xFFFF4FD8).copy(alpha = 0.7f), blurRadius = 22f) else null,
                ),
                color = when {
                    decennie == 1990 -> Color.Unspecified
                    decennie == 1980 -> Color(0xFFFF4FD8)
                    decennie == 2020 -> MaterialTheme.colorScheme.onSurface
                    else -> monde.accent
                },
                textAlign = TextAlign.Center,
            )
            Text(
                "${monde.decennie} · ${monde.sousTitre}".uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontSize = 10.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val FondCarton = Color(0xFF0A0704)

/** Le dégradé « lettres d'acier » de 1990 (§G), de haut en bas : clair, gris moyen, gris foncé, clair. */
private val DegradeAcier = Brush.verticalGradient(
    colors = listOf(Color(0xFFF8FAFC), Color(0xFF94A3B8), Color(0xFF475569), Color(0xFFE2E8F0)),
)

/**
 * Les décorations statiques d'un cadre de carton (§G, retouche du 28 septembre 2026) : filets
 * intérieurs, fleurons, lueur ou liseré — jamais un bord ou un fond, déjà posés par l'appelant.
 */
private fun DrawScope.decorationsDeCarton(decennie: Int, accent: Color) {
    when (decennie) {
        1910 -> {
            filetInterieur(inset = 6.dp.toPx(), couleur = accent, pointille = true)
            fleuronsAuxCoins(accent)
        }
        1930 -> filetInterieur(inset = 5.dp.toPx(), couleur = accent, pointille = false)
        1980 -> drawRoundRect(
            color = Color(0xFFFF4FD8).copy(alpha = 0.25f),
            topLeft = Offset(4.dp.toPx(), 4.dp.toPx()),
            size = Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
            style = Stroke(width = 6.dp.toPx()),
        )
        2020 -> filetInterieur(inset = 4.dp.toPx(), couleur = accent.copy(alpha = 0.22f), pointille = false)
    }
}

private fun DrawScope.filetInterieur(inset: Float, couleur: Color, pointille: Boolean) {
    drawRect(
        color = couleur.copy(alpha = if (pointille) 0.7f else 0.55f),
        topLeft = Offset(inset, inset),
        size = Size(size.width - 2 * inset, size.height - 2 * inset),
        style = Stroke(
            width = 1.dp.toPx(),
            pathEffect = if (pointille) PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 3.dp.toPx())) else null,
        ),
    )
}

private fun DrawScope.fleuronsAuxCoins(couleur: Color) {
    val marge = 6.dp.toPx()
    listOf(
        Offset(marge, marge),
        Offset(size.width - marge, marge),
        Offset(marge, size.height - marge),
        Offset(size.width - marge, size.height - marge),
    ).forEach { coin -> drawCircle(couleur.copy(alpha = 0.75f), radius = 2.dp.toPx(), center = coin) }
}
