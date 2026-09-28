package fr.mediatheque.journal.ui.frise

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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Canvas
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

    Column(modifier.semantics { contentDescription = description }, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(format.largeur, format.hauteur)
                .clip(shape)
                .background(Color.Black, shape)
                .let {
                    when (etat) {
                        is EtatPhotogramme.Ouverte -> it.border(1.5.dp, format.bord ?: or, shape)
                        is EtatPhotogramme.EnCours -> it.background(corail.copy(alpha = 0.14f), shape).border(2.dp, corail, shape)
                        is EtatPhotogramme.Verrouillee -> it.dashedBorder(CouleurPointille, cornerRadius = format.rayon, strokeWidth = 1.5.dp)
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            val affiche = when (etat) {
                is EtatPhotogramme.Ouverte -> etat.affiche
                is EtatPhotogramme.EnCours -> etat.affiche
                is EtatPhotogramme.Verrouillee -> null
            }
            if (affiche != null) {
                Cover(affiche, "$annee", format.largeur, format.hauteur)
            } else {
                Text(
                    annee.toString(),
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = Fraunces),
                    color = if (etat is EtatPhotogramme.Verrouillee) MaterialTheme.colorScheme.onSurfaceVariant else monde.accent,
                )
            }

            // Les décorations statiques du format (§C) : la bande son des mondes 1930/1940, la
            // bande de tracking du VHS de 1980 — jamais un filtre de couleur (réservé à l'image de
            // fond, livraison 2), seulement ce que le format lui-même dessine sur le photogramme.
            when (format.traitement) {
                TraitementImage.BANDE_SON, TraitementImage.BANDE_SON_CONTRASTE -> BandeSon(Modifier.align(Alignment.CenterStart))
                TraitementImage.VHS -> BandeTracking(Modifier.align(Alignment.BottomCenter))
                else -> Unit
            }

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

        val sous = when (etat) {
            is EtatPhotogramme.EnCours -> "Tu es ici"
            is EtatPhotogramme.Ouverte -> etiquetteProfondeur(etat.profondeur)
            is EtatPhotogramme.Verrouillee -> if (etat.enAvance > 0) "${etat.enAvance} vu${if (etat.enAvance > 1) "s" else ""} en avance" else "à tourner"
        }
        Text(
            sous,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = when (etat) {
                is EtatPhotogramme.EnCours -> corail
                is EtatPhotogramme.Verrouillee -> CouleurATourner
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
        )
    }
}

/** « 12 films », « 1 film », « 0 film » : la profondeur d'une année ouverte. */
private fun etiquetteProfondeur(profondeur: Int): String = "$profondeur ${if (profondeur == 1) "film" else "films"}"

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
            drawRect(Color(0xFFECE2CC).copy(alpha = 0.8f), topLeft = androidx.compose.ui.geometry.Offset(0f, y), size = androidx.compose.ui.geometry.Size(size.width, h))
            drawRect(accent.copy(alpha = 0.8f), topLeft = androidx.compose.ui.geometry.Offset(0f, y + h), size = androidx.compose.ui.geometry.Size(size.width, 1f))
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
            drawLine(blanc, androidx.compose.ui.geometry.Offset(x, size.height / 2f), androidx.compose.ui.geometry.Offset(x + 3f, size.height / 2f), strokeWidth = size.height, cap = androidx.compose.ui.graphics.StrokeCap.Butt)
            x += 10f
        }
    }
}
