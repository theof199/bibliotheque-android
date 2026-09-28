package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.Fraunces

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
 *
 * Livraison 4 (28 septembre 2026, §G suite) : 1960 → 2020 gagnent leur ambiance. Certaines
 * dessinent aussi un texte (« Fin », « PLAY », l'enseigne de salle, le menu DVD) — `Box` plutôt
 * que le `Canvas` seul de 1930-1950, ces mondes-là ayant du texte à afficher en plus des tracés.
 *
 * Livraison 5 (28 septembre 2026, §G) : 1890 → 1910 ferment le muet — chaque sous-effet quantifié
 * à son propre nombre de paliers via `progressionQuantifiee` (le saccadé voulu par le delta,
 * §G) plutôt que d'animer en continu comme 1930 → 2020. 1920 gagne les ombres qui penchent (un
 * vrai cisaillement via `nativeCanvas.skew`, pas une rotation approximée) et le réverbère qui se
 * balance ; l'ombre d'Orlok elle-même est statique (§G, « Image »), posée par `SectionMonde.kt`.
 */
@Composable
fun AmbianceDeMonde(monde: Monde, active: Boolean, k: Float, modifier: Modifier = Modifier) {
    when (monde.decennie) {
        1890 -> AmbianceDesOrigines(active, k, modifier)
        1900 -> AmbianceDeLaFeerie(active, k, modifier)
        1910 -> AmbianceDuMuet(active, modifier)
        1920 -> AmbianceDeLExpressionnisme(active, k, monde.accent, modifier)
        1930 -> AmbianceDuParlant(active, k, monde.accent, modifier)
        1940 -> AmbianceDuNoir(active, k, monde.accent, modifier)
        1950 -> AmbianceDuTechnicolor(active, modifier)
        1960 -> AmbianceDesNouvellesVagues(active, k, monde.accent, modifier)
        1970 -> AmbianceDuNouvelHollywood(active, k, monde.accent, modifier)
        1980 -> AmbianceDuNeon(active, k, monde.accent, modifier)
        1990 -> AmbianceDuBlockbuster(active, k, modifier)
        2000 -> AmbianceDuNumerique(active, k, monde.accent, modifier)
        2010 -> AmbianceDuStreaming(active, k, monde.accent, modifier)
        2020 -> AmbianceDAujourdhui(active, k, modifier)
    }
}

/** 1890 (§G) : le motif de scintillement du voile radial chaud, déterministe (graine fixe par palier). */
private val SCINTILLEMENT_1890 = FloatArray(21) { kotlin.random.Random(it).nextInt(55, 100) / 100f }

/**
 * 1890, « les origines » (§G) : pas d'image, la flamme scintille directement sur le fond de la
 * section (retour téléphone du 28 septembre 2026 — 1890 n'a rien d'autre à décorer). Voile radial
 * chaud qui scintille (21 paliers, 1,3 s, boucle), rayure verticale claire qui traverse une fois
 * toutes les 20 s (320 paliers) et silhouette du train de La Ciotat qui traverse en 36 s
 * (576 paliers) — dessinée (pas de SVG commité pour ce détail) plutôt qu'importée.
 */
@Composable
private fun AmbianceDesOrigines(active: Boolean, k: Float, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1890")
    val scintillement by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_300, easing = LinearEasing)),
        label = "scintillement",
    )
    val rayure by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(20_000, easing = LinearEasing)),
        label = "rayure",
    )
    val train by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(36_000, easing = LinearEasing)),
        label = "train",
    )
    Canvas(modifier) {
        val palierScintillement = (progressionQuantifiee(scintillement, 1f / 21) * 21).toInt().coerceIn(0, 20)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color(0xFFFFDCA0).copy(alpha = 0.16f * SCINTILLEMENT_1890[palierScintillement]), Color.Transparent),
            ),
            radius = size.minDimension * 0.6f,
            center = Offset(size.width / 2f, size.height / 2f),
        )
        val rayureT = progressionQuantifiee(rayure, 1f / 320)
        drawRect(
            Color(0xFFFFF6E0).copy(alpha = 0.28f + 0.32f * rayureT),
            topLeft = Offset(rayureT * size.width, 0f),
            size = Size(1.dp.toPx(), size.height),
        )
        val trainT = progressionQuantifiee(train, 1f / 576)
        val largeurTrain = 90.dp.toPx() * k
        translate(left = size.width - trainT * (size.width + largeurTrain), top = 560.dp.toPx()) {
            val silhouette = Path().apply {
                moveTo(0f, 24.dp.toPx())
                lineTo(largeurTrain * 0.15f, 4.dp.toPx())
                lineTo(largeurTrain * 0.85f, 4.dp.toPx())
                lineTo(largeurTrain, 24.dp.toPx())
                close()
            }
            drawPath(silhouette, Color.Black.copy(alpha = 0.45f))
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 5.dp.toPx(), center = Offset(largeurTrain * 0.25f, 26.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.45f), radius = 5.dp.toPx(), center = Offset(largeurTrain * 0.75f, 26.dp.toPx()))
        }
    }
}

/**
 * 1900, « la féerie » (§G) : les ampoules de la marquise foraine se chassent (six paliers,
 * décalées d'une ampoule à l'autre — approximation d'une guirlande, distincte de la vraie
 * `Marquise.kt`, générique à tout le voyage) ; les teintes coloriées à la main dérivent sur le
 * fond (trois radiaux rose/bleu/vert, aller-retour en 40 s).
 */
@Composable
private fun AmbianceDeLaFeerie(active: Boolean, k: Float, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1900")
    val chasse by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_200, easing = LinearEasing)),
        label = "chasse",
    )
    val derive by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(40_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "derive",
    )
    Canvas(modifier) {
        val palier = (progressionQuantifiee(chasse, 1f / 6) * 6).toInt().coerceIn(0, 5)
        val nombreAmpoules = 9
        val pas = size.width / (nombreAmpoules + 1)
        repeat(nombreAmpoules) { i ->
            val allumee = (i + palier) % 3 == 0
            drawCircle(
                Color(0xFFFFE7A0).copy(alpha = if (allumee) 0.9f else 0.25f),
                radius = 2.5.dp.toPx(),
                center = Offset(pas * (i + 1), 60.dp.toPx()),
            )
        }
        listOf(Color(0xFFFF7FC2) to 0.15f, Color(0xFF5FA8FF) to 0.5f, Color(0xFF6FE08A) to 0.85f).forEach { (couleur, fraction) ->
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(couleur.copy(alpha = 0.3f), Color.Transparent)),
                radius = 90.dp.toPx() * k,
                center = Offset((derive * size.width + size.width * fraction) % size.width, 380.dp.toPx()),
            )
        }
    }
}

/**
 * 1910, « le muet » (§G) : le rouleau de piano défile vers le haut au bord droit de la route —
 * bande de 14 dp, motif de trous répété, en boucle sur 3 s. Pas de nombre de paliers donné par le
 * delta pour ce défilement (contrairement à 1890/1900) : laissé continu plutôt que quantifié.
 */
@Composable
private fun AmbianceDuMuet(active: Boolean, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1910")
    val defilement by transition.animateFloat(
        initialValue = 0f, targetValue = -40f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing)),
        label = "rouleau",
    )
    Canvas(modifier) {
        val largeurBande = 14.dp.toPx()
        val left = size.width - largeurBande
        drawRect(Color(0xFFD8C49A).copy(alpha = 0.35f), topLeft = Offset(left, 0f), size = Size(largeurBande, size.height))
        val pasTrou = 16.dp.toPx()
        var y = defilement.dp.toPx().mod(pasTrou)
        while (y < size.height) {
            drawCircle(Color.Black.copy(alpha = 0.35f), radius = 2.dp.toPx(), center = Offset(left + largeurBande / 2f, y))
            y += pasTrou
        }
    }
}

/**
 * 1920, « l'expressionnisme » (§G) : les ombres penchent — un vrai cisaillement (`skewX`, via
 * `nativeCanvas.skew`, l'origine ramenée en bas de la section comme le demande le delta) plutôt
 * qu'une rotation approximée — sur quelques bandes sombres obliques ; le réverbère se balance
 * (rotation, origine en bas du mât) et promène sa lumière (un halo qui suit la tête du mât).
 * L'ombre d'Orlok elle-même est statique (§G, « Image »), posée par `SectionMonde.kt`.
 */
@Composable
private fun AmbianceDeLExpressionnisme(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1920")
    val inclinaison by transition.animateFloat(
        initialValue = -3f, targetValue = 3f,
        animationSpec = infiniteRepeatable(tween(8_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "ombres",
    )
    val balancement by transition.animateFloat(
        initialValue = -7f, targetValue = 7f,
        animationSpec = infiniteRepeatable(tween(3_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "reverbere",
    )
    Canvas(modifier) {
        val palierOmbres = (progressionQuantifiee((inclinaison + 3f) / 6f, 1f / 128) * 6f - 3f)
        val sx = kotlin.math.tan(Math.toRadians(palierOmbres.toDouble())).toFloat()
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val sauvegarde = nativeCanvas.save()
        nativeCanvas.translate(0f, size.height)
        nativeCanvas.skew(sx, 0f)
        nativeCanvas.translate(0f, -size.height)
        var x = 40.dp.toPx() * k
        while (x < size.width) {
            drawRect(Color.Black.copy(alpha = 0.28f), topLeft = Offset(x, 100.dp.toPx()), size = Size(18.dp.toPx(), 500.dp.toPx()))
            x += 130.dp.toPx() * k
        }
        nativeCanvas.restoreToCount(sauvegarde)

        val palierReverbere = (progressionQuantifiee((balancement + 7f) / 14f, 1f / 48) * 14f - 7f)
        val basDuMat = Offset(340.dp.toPx() * k, 600.dp.toPx())
        rotate(palierReverbere, pivot = basDuMat) {
            val hauteurMat = 90.dp.toPx()
            val tete = basDuMat - Offset(0f, hauteurMat)
            drawLine(Color(0xFF3A3A3A), basDuMat, tete, strokeWidth = 2.dp.toPx())
            drawCircle(
                brush = Brush.radialGradient(colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent)),
                radius = 46.dp.toPx(),
                center = tete,
            )
            drawCircle(accent.copy(alpha = 0.8f), radius = 3.dp.toPx(), center = tete)
        }
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

/**
 * 1960, « les nouvelles vagues » (§G) : seul « Fin » signe l'image — Fraunces italique (synthétisé,
 * la police n'a pas de fichier italique dédié), alpha .55, immobile en bas à droite.
 *
 * Le tremblement à l'épaule de l'image demandé par le delta n'est pas dessiné ici : `AmbianceDeMonde`
 * ne peut transformer que ce qu'elle dessine elle-même, jamais `ImageDeFond` (composée
 * indépendamment, plus bas dans la pile de `SectionMonde.kt`) — lui donner prise dessus sortirait
 * du périmètre de cette livraison (« ne pas reprendre la machinerie »). Déviation à signaler.
 */
@Composable
private fun AmbianceDesNouvellesVagues(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    Box(modifier, contentAlignment = Alignment.TopEnd) {
        Text(
            "Fin",
            fontFamily = Fraunces,
            fontStyle = FontStyle.Italic,
            fontSize = 22.sp,
            color = accent.copy(alpha = 0.55f),
            modifier = Modifier.offset(x = -20.dp * k, y = 520.dp),
        )
    }
}

private val BlancCasse = Color(0xFFF5F0E6)

/**
 * 1970, « le Nouvel Hollywood » (§G) : la rayure de copie saute d'un bord à l'autre plutôt que de
 * glisser (neuf paliers tenus sur 3,7 s, `steps(1)` du delta — deux paliers d'effacement,
 * un après chaque position tenue) ; le flare orange traverse en continu, linéaire, sur 16 s.
 */
@Composable
private fun AmbianceDuNouvelHollywood(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1970")
    val cycleRayure by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(3_700, easing = LinearEasing)),
        label = "rayure",
    )
    val avanceFlare by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16_000, easing = LinearEasing)),
        label = "flare",
    )
    Canvas(modifier) {
        val etape = (cycleRayure * 9).toInt().coerceIn(0, 8)
        val rayureVisible = etape != 3 && etape != 8
        if (rayureVisible) {
            val x = (if (etape < 4) 60f else 250f).dp.toPx() * k
            drawRect(BlancCasse.copy(alpha = 0.5f), topLeft = Offset(x, 0f), size = Size(1.dp.toPx(), size.height))
        }
        val largeurFlare = 220.dp.toPx() * k
        val hauteurFlare = 120.dp.toPx()
        val x = -largeurFlare + avanceFlare * (size.width + largeurFlare * 2f)
        drawOval(
            brush = Brush.radialGradient(colors = listOf(accent.copy(alpha = 0.35f), Color.Transparent)),
            topLeft = Offset(x, 300.dp.toPx() - hauteurFlare / 2f),
            size = Size(largeurFlare, hauteurFlare),
        )
    }
}

/**
 * 1980, « le néon » (§G) : les lignes de balayage sont statiques (rien à animer dans le delta) ; la
 * bande de tracking descend en boucle sur l'image (250-540 dp), et « PLAY » clignote — flou de la
 * bande approximé par une simple transparence plutôt qu'un vrai flou gaussien (coût par frame).
 */
@Composable
private fun AmbianceDuNeon(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1980")
    val tracking by transition.animateFloat(
        initialValue = 250f,
        targetValue = 540f,
        animationSpec = infiniteRepeatable(tween(5_000, easing = LinearEasing)),
        label = "tracking",
    )
    val clignote by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_000, easing = LinearEasing)),
        label = "play",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            var y = 250.dp.toPx()
            val pas = 4.dp.toPx()
            while (y < 550.dp.toPx()) {
                drawRect(Color.Black.copy(alpha = 0.28f), topLeft = Offset(0f, y), size = Size(size.width, 2.dp.toPx()))
                y += pas
            }
            drawRect(Color.White.copy(alpha = 0.22f), topLeft = Offset(0f, tracking.dp.toPx()), size = Size(size.width, 10.dp.toPx()))
        }
        Text(
            "▶ PLAY",
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
            color = accent.copy(alpha = if (clignote < 0.5f) 1f else 0f),
            modifier = Modifier.align(Alignment.TopEnd).offset(x = -24.dp * k, y = 262.dp),
        )
    }
}

/**
 * 1990, « le blockbuster » (§G) : le balayage de lumière est posé « sur le carton » par le delta —
 * hors de portée d'`AmbianceDeMonde`, qui ne dessine jamais sur le carton (celui-ci joue sa propre
 * entrée, `CartonTitre.kt`, §D). Approximé sur l'image (250-550 dp, §H) à la place ; le dégradé à
 * 100° est approximé par une rotation de 10°, proche de l'horizontale demandée. Déviation à
 * signaler.
 */
@Composable
private fun AmbianceDuBlockbuster(active: Boolean, k: Float, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-1990")
    val avanceBalayage by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            keyframes {
                durationMillis = 6_000
                -1f at 0
                1f at 3_600 using LinearEasing
                1f at 6_000
            },
        ),
        label = "balayage",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val largeur = 80.dp.toPx() * k
            val x = 195.dp.toPx() * k + avanceBalayage * (size.width / 2f + largeur)
            rotate(10f, pivot = Offset(x, 400.dp.toPx())) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.35f), Color.Transparent),
                    ),
                    topLeft = Offset(x - largeur / 2f, 250.dp.toPx()),
                    size = Size(largeur, 300.dp.toPx()),
                )
            }
        }
        Text(
            "SALLE 7 · 20H30 · THX",
            fontFamily = FontFamily.Monospace,
            fontSize = 12.sp,
            letterSpacing = 3.6.sp,
            color = Color.White.copy(alpha = 0.35f),
            modifier = Modifier.align(Alignment.TopCenter).offset(y = 480.dp),
        )
    }
}

/**
 * 2000, « le numérique » (§G) : le menu DVD clignote (alpha .6/.25 toutes les 1,5 s), la barre de
 * chargement glisse en boucle sous la marquise.
 */
@Composable
private fun AmbianceDuNumerique(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-2000")
    val clignoteMenu by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1_500, easing = LinearEasing)),
        label = "menu",
    )
    val avanceBarre by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing)),
        label = "barre",
    )
    Box(modifier) {
        Canvas(Modifier.fillMaxSize()) {
            val largeurPiste = 210.dp.toPx() * k
            val hauteurPiste = 3.dp.toPx()
            val left = 24.dp.toPx() * k
            val top = 536.dp.toPx()
            drawRect(Color(0xFF1E293B), topLeft = Offset(left, top), size = Size(largeurPiste, hauteurPiste))
            val largeurSegment = largeurPiste * 0.4f
            val xRelative = (-0.4f + avanceBarre * 1.4f) * largeurPiste
            drawRect(accent, topLeft = Offset(left + xRelative, top), size = Size(largeurSegment, hauteurPiste))
        }
        Text(
            "▸ LIRE LE FILM   SCÈNES BONUS",
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp,
            letterSpacing = 2.sp,
            color = accent.copy(alpha = if (clignoteMenu < 0.5f) 0.6f else 0.25f),
            modifier = Modifier.offset(x = 24.dp * k, y = 500.dp),
        )
    }
}

/**
 * 2010, « le streaming » (§G) : la rangée de vignettes défile de droite à gauche, contenu doublé
 * (une seconde rangée décalée d'une largeur totale, 450 dp) pour boucler sans coupure.
 */
@Composable
private fun AmbianceDuStreaming(active: Boolean, k: Float, accent: Color, modifier: Modifier) {
    if (!active) return
    val transition = rememberInfiniteTransition(label = "ambiance-2010")
    val defilement by transition.animateFloat(
        initialValue = 0f,
        targetValue = -450f,
        animationSpec = infiniteRepeatable(tween(28_000, easing = LinearEasing)),
        label = "vignettes",
    )
    Canvas(modifier) {
        val largeur = 64.dp.toPx() * k
        val hauteur = 36.dp.toPx()
        val gap = 8.dp.toPx() * k
        val pas = largeur + gap
        val top = 500.dp.toPx()
        val dx = defilement.dp.toPx() * k
        listOf(0f, 450.dp.toPx() * k).forEach { decalageRangee ->
            var x = dx + decalageRangee
            while (x < size.width) {
                if (x + largeur > 0f) {
                    drawRoundRect(
                        accent.copy(alpha = 0.45f),
                        topLeft = Offset(x, top),
                        size = Size(largeur, hauteur),
                        cornerRadius = CornerRadius(4.dp.toPx()),
                    )
                }
                x += pas
            }
        }
    }
}

/**
 * 2020, « aujourd'hui » (§G) : la lampe corail respire — un dégradé radial dont l'alpha va et vient,
 * centré sur la flaque de lumière de la route (290, 444).
 */
@Composable
private fun AmbianceDAujourdhui(active: Boolean, k: Float, modifier: Modifier) {
    if (!active) return
    val corail = MaterialTheme.colorScheme.primary
    val transition = rememberInfiniteTransition(label = "ambiance-2020")
    val respiration by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4_000, easing = LinearEasing), repeatMode = RepeatMode.Reverse),
        label = "lampe",
    )
    Canvas(modifier) {
        val centre = Offset(290.dp.toPx() * k, 444.dp.toPx())
        val rayon = 140.dp.toPx() * k
        drawCircle(
            brush = Brush.radialGradient(colors = listOf(corail.copy(alpha = 0.18f), Color.Transparent), center = centre, radius = rayon),
            radius = rayon,
            center = centre,
            alpha = respiration,
        )
    }
}
