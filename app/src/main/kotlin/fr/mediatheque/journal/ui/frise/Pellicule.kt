package fr.mediatheque.journal.ui.frise

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.Dp
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.random.Random

/**
 * Le dessin du Voyage (brief du 16 septembre 2026, phase 2) : la pellicule qui serpente, les
 * motifs de fond d'un monde, la marquise d'une décennie et les trois glyphes de festival. Le
 * podium d'une année (21 septembre 2026) reprend cette bande, droite plutôt que serpentine
 * (`bandeDePellicule`).
 *
 * Tout est vectoriel et dessiné au `Canvas` — aucune image bitmap, aucune ressource de plus. Les
 * fonctions sont des extensions de `DrawScope` : elles ne connaissent ni l'état de l'écran, ni les
 * données du Voyage, seulement des couleurs et des fractions de largeur.
 */

/** La largeur de la bande, perforations comprises (brief : « ~40 dp »). */
val LARGEUR_PELLICULE: Dp = Dp(40f)

/**
 * Un segment de pellicule dans une cellule d'année : il entre en haut à `xEntree`, passe par
 * `xAncre` à mi-hauteur (là où se pose le photogramme) et ressort en bas à `xSortie`.
 *
 * Deux courbes quadratiques plutôt qu'une cubique : le point de passage est ainsi **sur** la
 * courbe, pas seulement tiré par elle — le photogramme se pose exactement dessus. Les cellules se
 * raccordent parce que la sortie de l'une est l'entrée de la suivante (`VoyageScreen` les calcule
 * d'une seule suite), et que la tangente y est verticale des deux côtés.
 *
 * Les perforations sont posées le long de la courbe, à la normale : elles suivent donc le
 * serpentin au lieu de rester sur deux colonnes droites.
 */
fun DrawScope.segmentDePellicule(
    xEntree: Float,
    xAncre: Float,
    xSortie: Float,
    largeur: Float,
    couleurBande: Color,
    couleurPerforation: Color,
) {
    val h = size.height
    val entree = Offset(xEntree, 0f)
    val ancre = Offset(xAncre, h / 2f)
    val sortie = Offset(xSortie, h)
    val controleHaut = Offset(xAncre, h * 0.2f)
    val controleBas = Offset(xAncre, h * 0.8f)

    val chemin = Path().apply {
        moveTo(entree.x, entree.y)
        quadraticTo(controleHaut.x, controleHaut.y, ancre.x, ancre.y)
        quadraticTo(controleBas.x, controleBas.y, sortie.x, sortie.y)
    }
    drawPath(chemin, couleurBande, style = Stroke(width = largeur))

    val ecart = largeur / 2f - largeur * 0.11f
    val perfoL = largeur * 0.13f
    val perfoH = largeur * 0.09f
    listOf(Triple(entree, controleHaut, ancre), Triple(ancre, controleBas, sortie)).forEach { (p0, c, p1) ->
        for (pas in 0 until 4) {
            val t = (pas + 0.5f) / 4f
            val point = pointQuadratique(t, p0, c, p1)
            val tangente = tangenteQuadratique(t, p0, c, p1)
            val norme = hypot(tangente.x, tangente.y).takeIf { it > 0f } ?: continue
            val normale = Offset(-tangente.y / norme, tangente.x / norme)
            val angle = Math.toDegrees(atan2(tangente.y.toDouble(), tangente.x.toDouble())).toFloat() - 90f
            listOf(-1f, 1f).forEach { cote ->
                val centre = point + normale * (ecart * cote)
                rotate(angle, centre) {
                    drawRoundRect(
                        color = couleurPerforation,
                        topLeft = Offset(centre.x - perfoL / 2f, centre.y - perfoH / 2f),
                        size = Size(perfoL, perfoH),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(perfoH / 3f),
                    )
                }
            }
        }
    }
}

/**
 * Un bout de pellicule droit (brief du 21 septembre 2026, « le podium ») : jumeau horizontal de
 * `segmentDePellicule`, sans le serpentin — une bande droite sur toute la largeur du `DrawScope`,
 * perforée aux deux bords, sous les trois photogrammes du podium d'une année.
 */
fun DrawScope.bandeDePellicule(couleurBande: Color, couleurPerforation: Color) {
    val largeur = size.height
    val y = largeur / 2f
    drawLine(couleurBande, Offset(0f, y), Offset(size.width, y), strokeWidth = largeur)

    val ecart = largeur / 2f - largeur * 0.11f
    val perfoL = largeur * 0.13f
    val perfoH = largeur * 0.09f
    val pas = perfoL * 3f
    var x = pas / 2f
    while (x < size.width) {
        listOf(-1f, 1f).forEach { cote ->
            drawRoundRect(
                color = couleurPerforation,
                topLeft = Offset(x - perfoL / 2f, y + ecart * cote - perfoH / 2f),
                size = Size(perfoL, perfoH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(perfoH / 3f),
            )
        }
        x += pas
    }
}

private fun pointQuadratique(t: Float, p0: Offset, c: Offset, p1: Offset): Offset {
    val u = 1f - t
    return Offset(
        u * u * p0.x + 2f * u * t * c.x + t * t * p1.x,
        u * u * p0.y + 2f * u * t * c.y + t * t * p1.y,
    )
}

private fun tangenteQuadratique(t: Float, p0: Offset, c: Offset, p1: Offset): Offset {
    val u = 1f - t
    return Offset(
        2f * u * (c.x - p0.x) + 2f * t * (p1.x - c.x),
        2f * u * (c.y - p0.y) + 2f * t * (p1.y - c.y),
    )
}

/**
 * Le motif de fond d'un monde, par-dessus sa couleur (brief, item 3). **Version simple assumée** :
 * six motifs génériques, quelques lignes ou points à très faible opacité — un décor, jamais un
 * élément qu'on lit.
 *
 * `graine` fixe le hasard des motifs pointillés : à graine égale, le même semis à chaque frame —
 * un `Random` sans graine ferait scintiller le fond à chaque recomposition.
 */
fun DrawScope.motifDeMonde(motif: MotifMonde, accent: Color, graine: Int) {
    val encre = accent.copy(alpha = 0.10f)
    when (motif) {
        MotifMonde.CERCLE -> {
            val centre = Offset(size.width / 2f, size.height / 2f)
            listOf(0.42f, 0.58f).forEach { facteur ->
                drawCircle(encre, radius = size.minDimension * facteur, center = centre, style = Stroke(width = 1.5f))
            }
        }
        MotifMonde.ETOILES -> {
            val hasard = Random(graine)
            repeat(14) {
                val x = hasard.nextFloat() * size.width
                val y = hasard.nextFloat() * size.height
                drawCircle(accent.copy(alpha = 0.22f), radius = hasard.nextFloat() * 1.6f + 0.6f, center = Offset(x, y))
            }
        }
        MotifMonde.DIAGONALES -> {
            var x = -size.height
            while (x < size.width) {
                drawLine(encre, Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = 1.5f)
                x += 26f
            }
        }
        MotifMonde.RAYURES -> {
            var x = 6f
            while (x < size.width) {
                drawLine(encre, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                x += 18f
            }
        }
        MotifMonde.GRAIN -> {
            val hasard = Random(graine)
            repeat(70) {
                val x = hasard.nextFloat() * size.width
                val y = hasard.nextFloat() * size.height
                drawCircle(accent.copy(alpha = 0.07f), radius = 1f, center = Offset(x, y))
            }
        }
        MotifMonde.BANDES -> {
            var y = 10f
            while (y < size.height) {
                drawLine(encre, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                y += 22f
            }
        }
    }
}

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
 * La façade d'une marquise de cinéma (brief, item 4) : un bloc, un auvent en trapèze, et une
 * rangée d'ampoules le long de l'auvent.
 *
 * `allumage` va de 0 à 1 et allume les ampoules **une à une** : à 0,5, la moitié brille. C'est
 * l'appelant qui l'anime (une seconde et demie) ou le fige à 1 pour une décennie déjà bouclée.
 */
fun DrawScope.facadeDeMarquise(fond: Color, accent: Color, eteinte: Color, allumage: Float, ampoules: Int) {
    val auventH = size.height * 0.3f
    drawRoundRect(
        color = fond,
        topLeft = Offset(0f, auventH * 0.6f),
        size = Size(size.width, size.height - auventH * 0.6f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(10f),
    )
    val auvent = Path().apply {
        moveTo(size.width * 0.06f, auventH)
        lineTo(size.width * 0.94f, auventH)
        lineTo(size.width * 0.86f, 0f)
        lineTo(size.width * 0.14f, 0f)
        close()
    }
    drawPath(auvent, accent.copy(alpha = 0.22f))
    drawPath(auvent, accent.copy(alpha = 0.6f), style = Stroke(width = 2f))

    val pas = size.width * 0.88f / (ampoules - 1).coerceAtLeast(1)
    for (i in 0 until ampoules) {
        val x = size.width * 0.06f + pas * i
        val allumee = allumage * ampoules > i
        if (allumee) {
            drawCircle(accent.copy(alpha = 0.28f), radius = 7f, center = Offset(x, auventH))
            drawCircle(accent, radius = 3.2f, center = Offset(x, auventH))
        } else {
            drawCircle(eteinte, radius = 3.2f, center = Offset(x, auventH))
        }
    }
}
