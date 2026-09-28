package fr.mediatheque.journal.ui.frise

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser

/**
 * Le dessin du Voyage (brief du 16 septembre 2026, phase 2) : les trois glyphes de festival, les
 * perforations d'un ticket de cinéma et le trait qui le composte.
 *
 * L'ancienne pellicule qui serpentait d'année en année, la façade de marquise pleine largeur et
 * le motif de fond générique d'un monde sont partis — delta de Léon du 25 septembre 2026
 * (« pavillon par pavillon », livraison 1) : la route et ses dix pavillons (`RouteDuVoyage.kt`),
 * le carton-titre (`CartonTitre.kt`) et la nouvelle marquise (`Marquise.kt`) les remplacent.
 *
 * Tout est vectoriel et dessiné au `Canvas` — aucune image bitmap, aucune ressource de plus. Les
 * fonctions sont des extensions de `DrawScope` : elles ne connaissent ni l'état de l'écran, ni les
 * données du Voyage, seulement des couleurs et des fractions de largeur.
 */

/** La feuille de la Palme, sur une grille de 16 × 16 — la nervure comprise. */
private const val PALME_PATH =
    "M8,1 C11.2,4.2 12.2,9.4 8,15.2 C3.8,9.4 4.8,4.2 8,1 Z"
private const val PALME_NERVURE = "M8,2.6 L8,14.4"

/** La crinière du Lion, une étoile à dix branches ; la face se pose par-dessus, au `drawCircle`. */
private const val LION_CRINIERE =
    "M8,0.4 L9.7,3.2 L12.7,2 L12.2,5.2 L15.4,5.6 L13.2,7.9 L15.6,10 L12.4,10.8 L13.2,13.8 " +
        "L10,13 L8,15.6 L6,13 L2.8,13.8 L3.6,10.8 L0.4,10 L2.8,7.9 L0.6,5.6 L3.8,5.2 L3.3,2 L6.3,3.2 Z"

/**
 * Un glyphe de festival (brief, item 5) : la feuille de la Palme, le lion et l'ours, en chemins
 * vectoriels sur une grille de 16 × 16, mis à l'échelle de la place qu'on leur laisse.
 *
 * `fond` est la couleur sur laquelle le glyphe se pose : elle sert à creuser la face du lion et le
 * museau de l'ours, faute de quoi les deux ne seraient que des taches pleines à cette taille.
 */
fun DrawScope.glypheRecompense(recompense: Recompense, encre: Color, fond: Color) {
    val facteur = size.minDimension / 16f
    scale(facteur, facteur, pivot = Offset.Zero) {
        when (recompense) {
            Recompense.PALME -> {
                drawPath(PathParser().parsePathString(PALME_PATH).toPath(), encre)
                drawPath(PathParser().parsePathString(PALME_NERVURE).toPath(), fond, style = Stroke(width = 0.9f))
            }
            Recompense.LION -> {
                drawPath(PathParser().parsePathString(LION_CRINIERE).toPath(), encre)
                drawCircle(fond, radius = 3.6f, center = Offset(8f, 8f))
                drawCircle(encre, radius = 0.8f, center = Offset(6.6f, 7.4f))
                drawCircle(encre, radius = 0.8f, center = Offset(9.4f, 7.4f))
                drawCircle(encre, radius = 1f, center = Offset(8f, 9.6f))
            }
            Recompense.OURS -> {
                drawCircle(encre, radius = 2.6f, center = Offset(3.4f, 3.8f))
                drawCircle(encre, radius = 2.6f, center = Offset(12.6f, 3.8f))
                drawCircle(encre, radius = 5.6f, center = Offset(8f, 9.4f))
                drawCircle(fond, radius = 2f, center = Offset(8f, 11.4f))
                drawCircle(encre, radius = 0.9f, center = Offset(8f, 10.6f))
            }
        }
    }
}

/**
 * Les bords perforés d'un ticket de cinéma (décision 2 du brief du 21 septembre 2026, « le
 * ticket ») : une rangée de petits ronds le long du haut et du bas du papier.
 */
fun DrawScope.bordsPerfores(couleur: Color) {
    val rayon = 2.2f
    val pas = 14f
    var x = pas / 2f
    while (x < size.width) {
        drawCircle(couleur, radius = rayon, center = Offset(x, rayon))
        drawCircle(couleur, radius = rayon, center = Offset(x, size.height - rayon))
        x += pas
    }
}

/** Le trait qui composte un ticket déjà utilisé, dans le portefeuille (décision 3 du brief du 21 septembre 2026). */
fun DrawScope.barrerTicket(couleur: Color) {
    drawLine(
        couleur,
        Offset(size.width * 0.08f, size.height * 0.85f),
        Offset(size.width * 0.92f, size.height * 0.15f),
        strokeWidth = 2.5f,
    )
}
