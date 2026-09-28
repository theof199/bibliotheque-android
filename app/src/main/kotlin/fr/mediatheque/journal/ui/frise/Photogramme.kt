package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.theme.Fraunces

/**
 * Le photogramme d'une année du Voyage (delta de Léon du 25 septembre 2026, « pavillon par
 * pavillon », §C) — un des dix posés sur la route de sa section (`SectionMonde.kt`).
 *
 * Trois états seulement en livraison 1 : `Ouverte` (une année déjà visitée), `EnCours` (l'année en
 * cours du Voyage), `Verrouillee` (pas encore atteinte). Un quatrième état, `Affiche`, est prévu
 * par le plan pour `AnneeScreen`/`DecennieScreen` (livraison 6) — non ajouté ici : ni écran ni
 * appelant ne le construirait encore, une branche morte de plus dans ce `when`.
 *
 * Retouche du 28 septembre 2026 (revue de la livraison 1) : le traitement de couleur du monde
 * (`matriceDe`, `Mondes.kt`) est posé sur la jaquette, les trois états portent tout leur habillage
 * (fond, bord, halo, étiquette) **dans** la case plutôt que dessous, et 1890/1900/1920 gagnent
 * leurs décorations statiques (vignette, rayure, voiles, ombre oblique).
 */
sealed interface EtatPhotogramme {
    /** Une année déjà visitée : son affiche (ou le dernier film vu, `afficheAnnee`), sa profondeur, sa récompense. */
    data class Ouverte(val affiche: String?, val profondeur: Int, val recompense: Recompense?) : EtatPhotogramme

    /** L'année en cours du Voyage — « Tu es ici ». */
    data class EnCours(val affiche: String?) : EtatPhotogramme

    /** Pas encore atteinte — `enAvance` : le nombre de films déjà vus par avance (0 la plupart du temps). */
    data class Verrouillee(val enAvance: Int) : EtatPhotogramme
}

private val CouleurPointille = Color(0xFF6B5A3E)
private val CouleurATourner = Color(0xFF8A7A57)
private val FondEnCours = Color(0xFF1A1410)
private val FondVerrouillee = Color(0xFF0F0C08)

@Composable
fun Photogramme(format: FormatPhotogramme, etat: EtatPhotogramme, annee: Int, monde: Monde, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(format.rayon)
    val or = MaterialTheme.colorScheme.secondary
    val corail = MaterialTheme.colorScheme.primary

    val description = when (etat) {
        is EtatPhotogramme.Ouverte -> "$annee, ${etiquetteProfondeur(etat.profondeur)}" + (etat.recompense?.let { ", ${it.singulier}" } ?: "")
        is EtatPhotogramme.EnCours -> "$annee, tu es ici"
        is EtatPhotogramme.Verrouillee -> if (etat.enAvance > 0) "$annee, ${etat.enAvance} vu${if (etat.enAvance > 1) "s" else ""} en avance" else "$annee, à tourner"
    }

    val affiche = when (etat) {
        is EtatPhotogramme.Ouverte -> etat.affiche
        is EtatPhotogramme.EnCours -> etat.affiche
        is EtatPhotogramme.Verrouillee -> null
    }
    val filtreCouleur = matriceDe(format.traitement)?.let { ColorFilter.colorMatrix(it) }

    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                // Le halo de l'année en cours (§C, « shadow 0 0 22 dp primary 55 % ») : posé avant
                // le `clip` pour ne pas être rogné par lui, nul (donc invisible) hors `EnCours`.
                .shadow(
                    elevation = if (etat is EtatPhotogramme.EnCours) 22.dp else 0.dp,
                    shape = shape,
                    ambientColor = corail.copy(alpha = 0.55f),
                    spotColor = corail.copy(alpha = 0.55f),
                )
                .size(format.largeur, format.hauteur)
                .clip(shape)
                .background(
                    when (etat) {
                        is EtatPhotogramme.Ouverte -> Color.Black
                        is EtatPhotogramme.EnCours -> FondEnCours
                        is EtatPhotogramme.Verrouillee -> FondVerrouillee
                    },
                    shape,
                )
                .let {
                    when (etat) {
                        is EtatPhotogramme.Ouverte -> it.border(1.5.dp, format.bord ?: or, shape)
                        is EtatPhotogramme.EnCours -> it.border(1.5.dp, corail, shape)
                        is EtatPhotogramme.Verrouillee -> it.dashedBorder(CouleurPointille, cornerRadius = format.rayon, strokeWidth = 1.5.dp)
                    }
                }
                // Les décorations statiques d'un monde (1890 vignette + rayure, 1900 voiles,
                // 1920 ombre oblique, §G) : dessinées après le contenu, jamais animées.
                .drawWithContent {
                    drawContent()
                    decorationsDeMonde(monde.decennie)
                },
            contentAlignment = Alignment.Center,
        ) {
            if (affiche != null) {
                Cover(affiche, "$annee", format.largeur, format.hauteur, colorFilter = filtreCouleur)
            } else if (etat is EtatPhotogramme.Ouverte) {
                Text(
                    annee.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Fraunces),
                    color = monde.accent,
                )
            }

            // Les décorations statiques du format (§C) : la bande son des mondes 1930/1940, la
            // bande de tracking du VHS de 1980.
            when (format.traitement) {
                TraitementImage.BANDE_SON, TraitementImage.BANDE_SON_CONTRASTE -> BandeSon(Modifier.align(Alignment.CenterStart))
                TraitementImage.VHS -> BandeTracking(Modifier.align(Alignment.BottomCenter))
                else -> Unit
            }

            // L'état, tout entier dans la case (retouche du 28 septembre 2026) : « Tu es ici »
            // pour l'année en cours, « à tourner »/« N vu(s) en avance » pour une année
            // verrouillée — jamais dessous, où ça chevauchait le millésime.
            when (etat) {
                is EtatPhotogramme.EnCours -> Text(
                    "Tu es ici",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = corail,
                    textAlign = TextAlign.Center,
                )
                is EtatPhotogramme.Verrouillee -> Text(
                    if (etat.enAvance > 0) "${etat.enAvance} vu${if (etat.enAvance > 1) "s" else ""} en avance" else "à tourner",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                    color = CouleurATourner,
                    textAlign = TextAlign.Center,
                )
                is EtatPhotogramme.Ouverte -> Unit
            }

            // La pastille « N films » (§C) : ne reste que pour une année ouverte, la seule
            // information que la case ne porte pas déjà par son état.
            if (etat is EtatPhotogramme.Ouverte) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(2.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 3.dp, vertical = 1.dp),
                ) {
                    Text(etiquetteProfondeur(etat.profondeur), style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp), color = Color.White)
                }
            }
        }
    }
}

/** « 12 films », « 1 film », « 0 film » : la profondeur d'une année ouverte. */
private fun etiquetteProfondeur(profondeur: Int): String = "$profondeur ${if (profondeur == 1) "film" else "films"}"

/**
 * Les décorations statiques d'un monde sur son photogramme (§G, retouche du 28 septembre 2026) :
 * 1890 (vignette lourde + une rayure claire), 1900 (voiles rose haut-gauche / bleu bas-droite),
 * 1920 (ombre oblique). Aucune autre décennie n'en porte en livraison 1 — les entrées animées de
 * §G restent pour la livraison 3.
 */
private fun DrawScope.decorationsDeMonde(decennie: Int) {
    when (decennie) {
        1890 -> {
            drawRect(
                brush = Brush.radialGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f)),
                    center = Offset(size.width / 2f, size.height / 2f),
                    radius = size.maxDimension * 0.8f,
                ),
            )
            drawLine(
                Color.White.copy(alpha = 0.3f),
                Offset(size.width * 0.2f, 0f),
                Offset(size.width * 0.32f, size.height),
                strokeWidth = 1f,
            )
        }
        1900 -> {
            drawCircle(Color(0xFFF3B6C6).copy(alpha = 0.22f), radius = size.minDimension * 0.6f, center = Offset(0f, 0f))
            drawCircle(Color(0xFF9FC9E8).copy(alpha = 0.22f), radius = size.minDimension * 0.6f, center = Offset(size.width, size.height))
        }
        1920 -> {
            drawRect(
                brush = Brush.linearGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.4f)),
                    start = Offset(0f, 0f),
                    end = Offset(size.width, size.height),
                ),
            )
        }
    }
}

/** La bande son du parlant (§C, 1930/1940) : 7 dp à gauche, rayures horizontales séparées d'un filet d'accent. */
@Composable
private fun BandeSon(modifier: Modifier = Modifier) {
    val accent = MaterialTheme.colorScheme.secondary
    Canvas(modifier.fillMaxHeight().width(7.dp)) {
        val hauteurs = listOf(1f, 2f, 2f, 1f)
        val periode = size.height / 6f
        var y = 0f
        var i = 0
        while (y < size.height) {
            val h = hauteurs[i % hauteurs.size] * periode / 2f
            drawRect(Color(0xFFECE2CC).copy(alpha = 0.8f), topLeft = Offset(0f, y), size = Size(size.width, h))
            drawRect(accent.copy(alpha = 0.8f), topLeft = Offset(0f, y + h), size = Size(size.width, 1f))
            y += h + 1f
            i += 1
        }
    }
}

/** La bande de tracking d'une cassette VHS (§C, 1980) : traits blancs 3/7 en bas du photogramme. */
@Composable
private fun BandeTracking(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(5.dp)) {
        val blanc = Color.White.copy(alpha = 0.7f)
        var x = 0f
        while (x < size.width) {
            drawLine(blanc, Offset(x, size.height / 2f), Offset(x + 3f, size.height / 2f), strokeWidth = size.height, cap = StrokeCap.Butt)
            x += 10f
        }
    }
}
