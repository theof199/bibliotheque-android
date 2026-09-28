package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.Limelight

private val CouleurNeon = Color(0xFFFF4FD8)

/**
 * La marquise d'un monde (delta de Léon du 25 septembre 2026, « pavillon par pavillon », §E) :
 * 210 dp de large, posée au pied de la route, alignée à droite. Retouche de l'ancienne marquise
 * (pleine largeur) plutôt que sa réécriture complète — les neuf ampoules et l'allumage en 1,5 s à
 * la décennie qui vient de se boucler restent.
 *
 * Retouche du 28 septembre 2026 (revue de la livraison 1) : `recompensesCount` (le nombre d'années
 * récompensées de la section, posé par `SectionMonde.kt`) fixe le nombre d'ampoules allumées hors
 * décennie bouclée — neuf sur neuf dès qu'elle l'est (le passage « toutes en 1,5 s » reste pour ce
 * moment). 1890 (peinte, sans ampoules, « en cours de tournage » en italique), 1920 (penchée −2°),
 * 1980 (néon rose : bord, titre, ampoules, halo) et 2020 (neuf ampoules toujours allumées) posent
 * leurs variantes de §E.
 */
@Composable
fun Marquise(monde: Monde, bouclee: Boolean, anime: Boolean, recompensesCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val decennie = monde.decennie
    // La cible d'allumage (retouche du 28 septembre 2026) : neuf sur neuf pour une décennie
    // bouclée ou pour 2020 (toujours allumée, §E) ; sinon une ampoule par année récompensée de la
    // section, neuf au plus.
    val cible = if (bouclee || decennie == 2020) 1f else (recompensesCount.coerceIn(0, 9) / 9f)
    val allumage = remember(monde.decennie) { Animatable(if (bouclee && !anime) 1f else cible) }
    LaunchedEffect(bouclee, anime, cible) {
        when {
            bouclee && anime -> allumage.animateTo(1f, tween(1_500))
            bouclee -> allumage.snapTo(1f)
            else -> allumage.snapTo(cible)
        }
    }
    val neon = decennie == 1980
    val eteinte = if (neon) CouleurNeon.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline
    val ampouleAllumee = if (neon) CouleurNeon else MaterialTheme.colorScheme.secondary
    val bordCouleur = if (neon) CouleurNeon else MaterialTheme.colorScheme.secondary
    val accentTitre = when {
        neon -> CouleurNeon
        bouclee -> monde.accent
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    // 1890 : une façade peinte plutôt que la vitrine de bulbes des autres décennies — un fond
    // teinté de l'accent du monde, sans ampoules (§E).
    val fondFacade = if (decennie == 1890) monde.accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.background

    Column(
        modifier
            .width(210.dp)
            // L'ombre de la marquise (§E, « 0 6 20 noir 50 % ») ; le halo néon de 1980 s'y ajoute.
            .shadow(6.dp, MaterialTheme.shapes.small, ambientColor = if (neon) CouleurNeon.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f), spotColor = if (neon) CouleurNeon.copy(alpha = 0.5f) else Color.Black.copy(alpha = 0.5f))
            .let { if (neon) it.shadow(14.dp, MaterialTheme.shapes.small, ambientColor = CouleurNeon.copy(alpha = 0.5f), spotColor = CouleurNeon.copy(alpha = 0.5f)) else it }
            .let { if (decennie == 1920) it.graphicsLayer { rotationZ = -2f } else it }
            .background(fondFacade, MaterialTheme.shapes.small)
            .border(1.dp, bordCouleur, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp)
            .semantics { contentDescription = "Années ${monde.decennie}, ${if (bouclee) monde.titreVoyageur else "décennie en cours"}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (decennie != 1890) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(9) { i ->
                    val allumee = allumage.value * 9 > i
                    Box(
                        Modifier
                            .size(8.dp)
                            .background(if (allumee) ampouleAllumee else eteinte, CircleShape),
                    )
                }
            }
        }
        Text(
            "ANNÉES ${monde.decennie}",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Limelight, letterSpacing = 1.5.sp, fontSize = 19.sp, fontWeight = FontWeight.Normal),
            color = accentTitre,
            textAlign = TextAlign.Center,
        )
        Text(
            (if (bouclee) monde.titreVoyageur else monde.slogan ?: "en cours de tournage").uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = 1.6.sp,
                fontStyle = if (decennie == 1890) FontStyle.Italic else FontStyle.Normal,
            ),
            color = if (bouclee) MaterialTheme.colorScheme.secondary.copy(alpha = allumage.value) else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
