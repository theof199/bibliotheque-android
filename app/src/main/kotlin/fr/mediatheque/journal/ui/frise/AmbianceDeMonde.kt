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
 */
@Composable
fun AmbianceDeMonde(monde: Monde, active: Boolean, k: Float, modifier: Modifier = Modifier) {
    when (monde.decennie) {
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
