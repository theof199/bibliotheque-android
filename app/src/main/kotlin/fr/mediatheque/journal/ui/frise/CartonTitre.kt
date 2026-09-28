package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.Fraunces
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
 *
 * Livraison 3 (28 septembre 2026, §D, §G) : `entree` sert enfin à quelque chose — `EnCours` dessine
 * l'entrée propre au monde par-dessus le cadre statique ci-dessus, `Jouee` (ou n'importe quel autre
 * état, pour les mondes sans entrée encore) rend le carton fini, à l'identique d'avant. Seuls 1930
 * (la piste son qui traverse le carton et allume les lettres une à une), 1940 (l'ombre du store qui
 * balaie et découvre le titre) et 1950 (les trois plaques qui se recalent) ont une entrée pour
 * l'instant — 1960 → 2020 (livraison 4) et 1890 → 1920 en 16 i/s (livraison 5) n'auront qu'à
 * ajouter leur propre branche à `TitreCarton` et `entreeDeCarton` ci-dessous.
 *
 * Livraison 4 (28 septembre 2026, §G suite) : les sept mondes 1960 → 2020 gagnent leur entrée —
 * `modifierEntreeTitre` porte les transformations du titre lui-même (saut, zoom, grésillement,
 * claque, décodage, fondu) pendant qu'`entreeDeCarton` garde le dessin par-dessus (éclat de 1990,
 * spinner de 2010). Toutes fluides (24 i/s), comme 1930-1950.
 *
 * Livraison 5 (28 septembre 2026, §G) : 1890 (le titre tressaute, `modifierEntreeTitre` — la
 * manivelle qui tourne, `entreeDeCarton`), 1900 (le clignotement du truc à arrêt, la lune de
 * Méliès) et 1910 (l'iris qui s'ouvre sur tout le carton, puis l'intertitre tapé sous le titre)
 * ferment le muet — `progression` leur arrive déjà tenue par pas de 16 images par seconde
 * (`progressionQuantifiee`, posée en amont par `SectionMonde.kt`), le saccadé voulu par le delta.
 * 1920 (le titre dessiné à la main) suit dans un commit séparé (§G, « traits brisés »).
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
    // Nulle hors `EnCours` (§D, livraison 3) : `Jouee` et les mondes sans entrée dessinée encore
    // rendent directement leur état final, comme avant cette livraison.
    val progressionEntree = (entree as? EtatEntree.EnCours)?.progression

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
                if (progressionEntree != null) entreeDeCarton(decennie, progressionEntree, monde.accent)
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            TitreCarton(monde, decennie, progressionEntree)
            // 1910 (§G) : l'intertitre tapé — Fraunces italique 14 sp accent plutôt que la légende
            // habituelle, révélée en largeur pendant l'entrée (`intertitreTape1910`) ; `Jouee` (ou
            // hors entrée) l'affiche déjà pleine, comme les autres mondes.
            if (decennie == 1910) {
                Text(
                    "${monde.decennie} · ${monde.sousTitre}".uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = Fraunces,
                        fontStyle = FontStyle.Italic,
                        letterSpacing = 2.sp,
                        fontSize = 14.sp,
                    ),
                    color = monde.accent,
                    textAlign = TextAlign.Center,
                    modifier = if (progressionEntree != null) Modifier.intertitreTape1910(progressionEntree) else Modifier,
                )
            } else {
                Text(
                    "${monde.decennie} · ${monde.sousTitre}".uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp, fontSize = 10.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * Le titre du carton (§D) : .1 em à 20 sp — 2 sp, la même conversion, en littéral, que le reste de
 * l'écran du Voyage (`letterSpacing` y est toujours posé en `sp`, jamais en `em`).
 *
 * 1930, pendant l'entrée (§G) : les lettres s'allument une à une, `texteLettresAllumees` en
 * dessous. 1950, pendant l'entrée : deux plaques rouge et bleue, décalées, qui se recalent à (0,0)
 * — approximées en alpha, Compose ne mélangeant pas nativement en mode « screen » sur du texte.
 */
@Composable
private fun TitreCarton(monde: Monde, decennie: Int, progressionEntree: Float?) {
    val style = MaterialTheme.typography.titleLarge.copy(
        fontFamily = Limelight,
        letterSpacing = 2.sp,
        fontSize = 20.sp,
        brush = if (decennie == 1990) DegradeAcier else null,
        shadow = if (decennie == 1980) Shadow(Color(0xFFFF4FD8).copy(alpha = 0.7f), blurRadius = 22f) else null,
    )
    val couleurFinale = when {
        decennie == 1990 -> Color.Unspecified
        decennie == 1980 -> Color(0xFFFF4FD8)
        decennie == 2020 -> MaterialTheme.colorScheme.onSurface
        else -> monde.accent
    }
    val texte = if (decennie == 1930 && progressionEntree != null) {
        texteLettresAllumees(monde.nom.uppercase(), progressionEntree, monde.accent)
    } else {
        AnnotatedString(monde.nom.uppercase())
    }

    Box(contentAlignment = Alignment.Center) {
        if (decennie == 1950 && progressionEntree != null) {
            // Le trajet restant (§G) : 1 au tout début (décalage complet), 0 une fois recalées.
            val reste = 1f - progressionEntree
            Text(
                texte, style = style, textAlign = TextAlign.Center, color = Color(0xFFFF3B3B).copy(alpha = 0.65f),
                modifier = Modifier.offset(x = (-14f * reste).dp, y = (-3f * reste).dp),
            )
            Text(
                texte, style = style, textAlign = TextAlign.Center, color = Color(0xFF38BDF8).copy(alpha = 0.65f),
                modifier = Modifier.offset(x = (14f * reste).dp, y = (3f * reste).dp),
            )
        }
        Text(
            texte,
            style = style,
            color = when {
                decennie == 1930 && progressionEntree != null -> Color.Unspecified
                // La plaque verte de 1950 (§G, `#4ADE80`), le temps de l'entrée seulement — le
                // titre revient à l'accent du monde une fois les trois plaques recalées (`Jouee`).
                decennie == 1950 && progressionEntree != null -> Color(0xFF4ADE80)
                else -> couleurFinale
            },
            textAlign = TextAlign.Center,
            modifier = if (progressionEntree != null) modifierEntreeTitre(decennie, progressionEntree) else Modifier,
        )
    }
}

/**
 * La transformation du titre pendant l'entrée, 1960 → 2020 (§G suite, livraison 4) — un
 * `Modifier` plutôt qu'un dessin `DrawScope`, le titre restant un vrai `Text` (police, style
 * dégradé de 1990 compris) tout du long. Neutre (`Modifier`) pour les mondes sans entrée de titre
 * dessinée.
 */
private fun modifierEntreeTitre(decennie: Int, progression: Float): Modifier = when (decennie) {
    // 1890 : le titre tressaute à la manivelle (§G) — translateY par pas irréguliers plutôt
    // qu'une liste à intervalle constant, comme le décrit le delta (« pas irréguliers »).
    1890 -> {
        val palier = (progression * POSITIONS_TRESSAUT_1890.size).toInt().coerceIn(0, POSITIONS_TRESSAUT_1890.size - 1)
        Modifier.offset(y = POSITIONS_TRESSAUT_1890[palier].dp)
    }
    // 1900 : « le truc à arrêt » (§G) — le titre s'efface puis reparaît, légèrement décalé au
    // départ, le décalage se résorbant à (0,0) en même temps que l'opacité revient.
    1900 -> {
        val alpha = if (progression < 0.5f) 1f - 2f * progression else 2f * (progression - 0.5f)
        Modifier.alpha(alpha.coerceIn(0f, 1f)).offset(x = ((1f - progression) * -10f).dp)
    }
    // 1960 : « trois sauts » (§G) — quatre positions tenues, `steps(1)` : la lettre saute d'un
    // point à l'autre plutôt que de glisser, la première tenue étant aussi l'apparition.
    1960 -> {
        val position = POSITIONS_SAUT_1960[(progression * POSITIONS_SAUT_1960.size).toInt().coerceIn(0, POSITIONS_SAUT_1960.size - 1)]
        Modifier.offset(x = position.x.dp, y = position.y.dp).alpha(if (progression <= 0f) 0f else 1f)
    }
    // 1970 : zoom lent, la courbe du delta portée sur `progression` directement (celui-ci reste
    // animé en linéaire par `SectionMonde`, comme chaque monde).
    1970 -> {
        val avance = EasingZoomLent1970.transform(progression)
        Modifier.graphicsLayer {
            scaleX = 1.25f - 0.25f * avance
            scaleY = scaleX
            alpha = 0.4f + 0.6f * avance
        }
    }
    // 1980 : le néon grésille — huit valeurs tenues (§G, `steps(1)`).
    1980 -> Modifier.alpha(ALPHAS_NEON_1980[(progression * ALPHAS_NEON_1980.size).toInt().coerceIn(0, ALPHAS_NEON_1980.size - 1)])
    // 1990 : la claque — même principe que 1970, courbe et amplitude différentes.
    1990 -> {
        val avance = EasingClaque1990.transform(progression)
        Modifier.graphicsLayer {
            scaleX = 1.7f - 0.7f * avance
            scaleY = scaleX
            alpha = avance
        }
    }
    // 2000 : le titre se décode — flou et alpha en six paliers tenus (§G, `steps(1)`).
    2000 -> {
        val niveau = (progression * 6).toInt().coerceIn(0, 5) / 5f
        Modifier.blur(((1f - niveau) * 6f).dp).alpha(0.3f + 0.7f * niveau)
    }
    // 2010 : le titre n'apparaît qu'après le spinner (`spinnerCarton`, `entreeDeCarton` plus bas) —
    // les fractions supposent `dureeEntree(2010)` à 2,8 s (`SectionMonde.kt`).
    2010 -> Modifier.alpha(alphaTitreStreaming2010(progression))
    // 2020 : le titre s'allume, ease-out simple (§G).
    2020 -> Modifier.alpha(1f - (1f - progression) * (1f - progression))
    else -> Modifier
}

/** 1890 (§G) : le tressaut à la manivelle, en pas irréguliers plutôt qu'à intervalle constant. */
private val POSITIONS_TRESSAUT_1890 = listOf(0f, -3f, -1f, -2f, 0f, -3f, -1f, 0f)

private val POSITIONS_SAUT_1960 = listOf(Offset(-30f, 0f), Offset(18f, -4f), Offset(-6f, 2f), Offset(0f, 0f))
private val EasingZoomLent1970 = CubicBezierEasing(0.2f, 0.6f, 0.2f, 1f)
private val ALPHAS_NEON_1980 = floatArrayOf(0f, 1f, 0.2f, 1f, 0.4f, 1f, 0.7f, 1f)
private val EasingClaque1990 = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1f)

/** Fraction de l'entrée de 2010 à laquelle le spinner a fini ses trois tours (§G) : 2,4 s / 2,8 s. */
private const val FRACTION_SPINNER_FIN_2010 = 2_400f / 2_800f

private fun alphaTitreStreaming2010(progression: Float): Float {
    if (progression <= FRACTION_SPINNER_FIN_2010) return 0f
    val t = ((progression - FRACTION_SPINNER_FIN_2010) / (1f - FRACTION_SPINNER_FIN_2010)).coerceIn(0f, 1f)
    return 1f - (1f - t) * (1f - t)
}

/**
 * 1930 (§G) : chaque lettre passe de `#4A4335` (éteinte) à l'accent du monde, décalée d'une lettre
 * à l'autre — une lettre s'allume dès que la progression dépasse son rang dans le mot.
 */
private fun texteLettresAllumees(nom: String, progression: Float, accent: Color): AnnotatedString = buildAnnotatedString {
    val eteinte = Color(0xFF4A4335)
    nom.forEachIndexed { index, lettre ->
        val seuil = index / nom.length.toFloat()
        withStyle(SpanStyle(color = if (progression > seuil) accent else eteinte)) { append(lettre.toString()) }
    }
}

/**
 * 1910 (§G) : l'intertitre tapé — la ligne « 1910 · LES GRANDS RÉCITS » sous le titre, révélée en
 * largeur (17 paliers) après un délai de 0,7 s (le temps que l'iris finisse de s'ouvrir,
 * `irisCarton1910`), sur les 0,9 s restants des 1,6 s de l'entrée. `clipRect`, comme
 * `ondeSonoreCarton` (1930) : Compose n'anime pas de vrai « dashoffset » de police.
 */
private fun Modifier.intertitreTape1910(progression: Float): Modifier = drawWithContent {
    val debutTape = 0.7f / 1.6f
    val avance = ((progression - debutTape) / (1f - debutTape)).coerceIn(0f, 1f)
    val paliers = progressionQuantifiee(avance, 1f / 17)
    clipRect(right = size.width * paliers) { this@drawWithContent.drawContent() }
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

/**
 * L'entrée d'un carton, dessinée par-dessus tout le reste tant qu'elle joue (§G, livraison 3) —
 * `CartonTitre` n'appelle cette fonction que si `entree` est `EnCours` (`progression` non nulle
 * dans l'appelant). Rien pour les mondes sans entrée dessinée encore.
 */
private fun DrawScope.entreeDeCarton(decennie: Int, progression: Float, accent: Color) {
    when (decennie) {
        1890 -> manivelleEntree1890(progression, accent)
        1900 -> luneDeMelies1900(accent)
        1910 -> irisCarton1910(progression)
        1930 -> ondeSonoreCarton(progression, accent)
        1940 -> storeVenitienCarton(progression)
        1990 -> eclatBlancCarton(progression)
        2010 -> spinnerCarton(progression, accent)
    }
}

/**
 * 1890 (§G) : la manivelle qui tourne en haut à droite du carton pendant que le titre tressaute
 * (`modifierEntreeTitre`) — 720° en 0,5 s (la moitié des 1 s de l'entrée totale), huit paliers
 * tenus. Un trait pour le bras et un petit cercle pour la poignée, plutôt qu'une icône dédiée —
 * pas de nouvelle ressource pour un détail aussi petit.
 */
private fun DrawScope.manivelleEntree1890(progression: Float, accent: Color) {
    val avance = (progression / 0.5f).coerceIn(0f, 1f)
    val palier = (avance * 8).toInt().coerceIn(0, 7)
    val rotation = palier / 8f * 720f
    val centre = Offset(size.width - 16.dp.toPx(), 16.dp.toPx())
    val poignee = Offset(size.width - 16.dp.toPx() + 8.dp.toPx(), 16.dp.toPx())
    rotate(rotation, pivot = centre) {
        drawLine(accent, centre, poignee, strokeWidth = 1.5.dp.toPx())
        drawCircle(accent, radius = 2.dp.toPx(), center = poignee)
    }
}

/**
 * 1900 (§G) : la lune de Méliès posée sur le carton pendant l'entrée — un disque, deux yeux, et
 * la fusée plantée dans l'œil droit (« Le Voyage dans la Lune », 1902), en traits plutôt qu'une
 * icône dédiée, comme 1890.
 */
private fun DrawScope.luneDeMelies1900(accent: Color) {
    val centre = Offset(size.width - 26.dp.toPx(), 18.dp.toPx())
    val oeilDroit = centre + Offset(3.dp.toPx(), -2.dp.toPx())
    drawCircle(accent.copy(alpha = 0.85f), radius = 10.dp.toPx(), center = centre)
    drawCircle(Color.Black, radius = 1.6.dp.toPx(), center = centre + Offset(-3.dp.toPx(), -2.dp.toPx()))
    drawCircle(Color.Black, radius = 1.6.dp.toPx(), center = oeilDroit)
    drawLine(Color.Black, oeilDroit, oeilDroit + Offset(4.dp.toPx(), -3.dp.toPx()), strokeWidth = 1.dp.toPx())
}

/**
 * 1910 (§G) : l'iris qui s'ouvre sur tout le carton — un cache noir percé d'un disque grandissant
 * (`Path` en règle pair-impair) plutôt qu'un `BlendMode.Clear`, qui exigerait d'isoler ce
 * `drawWithContent` dans son propre calque hors écran (fait pour l'image, §H, pas ici). Le rayon
 * final vaut 80 % de la demi-largeur du carton (§G, « 0 → 80 % »), atteint en 0,6 s des 1,6 s de
 * l'entrée.
 */
private fun DrawScope.irisCarton1910(progression: Float) {
    val fractionIris = 0.6f / 1.6f
    if (progression >= fractionIris) return
    val avance = progression / fractionIris
    val rayon = avance * size.minDimension / 2f * 0.8f
    val cache = Path().apply {
        addRect(Rect(Offset.Zero, size))
        addOval(Rect(center = center, radius = rayon))
        fillType = PathFillType.EvenOdd
    }
    drawPath(cache, Color.Black)
}

/**
 * 1930 (§G) : « une piste son optique traverse le carton » — une onde sinusoïdale, révélée de
 * gauche à droite par un `clipRect` qui avance avec `progression` plutôt qu'un vrai `dashoffset`
 * (Compose n'anime pas les `PathEffect` eux-mêmes).
 */
private fun DrawScope.ondeSonoreCarton(progression: Float, accent: Color) {
    clipRect(right = size.width * progression) {
        val pas = 12.dp.toPx()
        val amplitude = 9.dp.toPx()
        val milieu = size.height / 2f
        val chemin = Path().apply {
            moveTo(0f, milieu)
            var x = 0f
            var haut = true
            while (x < size.width) {
                val prochainX = (x + pas).coerceAtMost(size.width)
                quadraticTo(x + pas / 2f, if (haut) milieu - amplitude else milieu + amplitude, prochainX, milieu)
                x = prochainX
                haut = !haut
            }
        }
        drawPath(chemin, accent.copy(alpha = 0.9f), style = Stroke(width = 1.5.dp.toPx()))
    }
}

/**
 * 1940 (§G) : « l'ombre d'un store vénitien balaie le carton » — une colonne de bandes sombres,
 * légèrement penchée, qui traverse de gauche à droite en s'adoucissant en fin de course (ease-out,
 * approximé par une quadratique plutôt que la courbe de Bézier exacte du delta).
 */
private fun DrawScope.storeVenitienCarton(progression: Float) {
    val adouci = 1f - (1f - progression) * (1f - progression)
    val decalageX = -size.width + adouci * size.width * 2.2f
    rotate(8f, pivot = Offset(size.width / 2f, size.height / 2f)) {
        var y = -20.dp.toPx()
        val pas = 25.dp.toPx()
        while (y < size.height + 20.dp.toPx()) {
            drawRect(Color.Black.copy(alpha = 0.85f), topLeft = Offset(decalageX, y), size = Size(90.dp.toPx(), 9.dp.toPx()))
            y += pas
        }
    }
}

/**
 * 1990 (§G) : « éclat blanc plein carton » à 55-64 % de l'entrée — un aplat couvrant tout le
 * carton, tenu (`steps`) plutôt qu'un fondu, en même temps que la claque du titre s'achève.
 */
private fun DrawScope.eclatBlancCarton(progression: Float) {
    if (progression in 0.55f..0.64f) {
        drawRect(Color.White.copy(alpha = 0.9f))
    }
}

/** Fraction de l'entrée de 2010 à laquelle le spinner a fini de disparaître (§G) : 2,6 s / 2,8 s. */
private const val FRACTION_DISPARITION_FIN_2010 = 2_600f / 2_800f

/**
 * 2010 (§G) : le spinner de chargement — anneau à 25 % d'alpha et quart plein — tourne trois fois
 * en haut du carton en 2,4 s (`FRACTION_SPINNER_FIN_2010`) puis s'efface en 0,2 s de plus ; le
 * titre ne reprend qu'ensuite (`alphaTitreStreaming2010`).
 */
private fun DrawScope.spinnerCarton(progression: Float, accent: Color) {
    val alphaSpinner = when {
        progression < FRACTION_SPINNER_FIN_2010 -> 1f
        progression < FRACTION_DISPARITION_FIN_2010 ->
            1f - (progression - FRACTION_SPINNER_FIN_2010) / (FRACTION_DISPARITION_FIN_2010 - FRACTION_SPINNER_FIN_2010)
        else -> 0f
    }
    if (alphaSpinner <= 0f) return
    val rotation = (progression / FRACTION_SPINNER_FIN_2010) * 1_080f
    val centre = Offset(size.width / 2f, 14.dp.toPx())
    val rayon = 12.dp.toPx()
    rotate(rotation, pivot = centre) {
        drawCircle(accent.copy(alpha = 0.25f * alphaSpinner), radius = rayon, center = centre, style = Stroke(width = 2.dp.toPx()))
        drawArc(
            color = accent.copy(alpha = alphaSpinner),
            startAngle = -90f,
            sweepAngle = 90f,
            useCenter = false,
            topLeft = centre - Offset(rayon, rayon),
            size = Size(rayon * 2, rayon * 2),
            style = Stroke(width = 2.dp.toPx()),
        )
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
