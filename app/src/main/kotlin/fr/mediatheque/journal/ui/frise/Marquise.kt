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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.Limelight

/**
 * La marquise d'un monde (delta de Léon du 25 septembre 2026, « pavillon par pavillon », §E) :
 * 210 dp de large, posée au pied de la route, alignée à droite. Retouche de l'ancienne marquise
 * (pleine largeur) plutôt que sa réécriture complète — les neuf ampoules et l'allumage en 1,5 s à
 * la décennie qui vient de se boucler restent.
 *
 * Livraison 1 : les variantes par décennie du delta (1890 peinte sans ampoules, 1920 penchée,
 * 1980 néon rose) ne sont pas posées — hors de l'essai de cette livraison (route, places, cartons,
 * marquise générique) et pas encore un carton à cocher ; à revoir avec le propriétaire.
 */
@Composable
fun Marquise(monde: Monde, bouclee: Boolean, anime: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val allumage = remember(monde.decennie) { Animatable(if (bouclee && !anime) 1f else 0f) }
    LaunchedEffect(bouclee, anime) {
        when {
            bouclee && anime -> allumage.animateTo(1f, tween(1_500))
            bouclee -> allumage.snapTo(1f)
            else -> allumage.snapTo(0f)
        }
    }
    val eteinte = MaterialTheme.colorScheme.outline
    val accent = if (bouclee) monde.accent else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        modifier
            .width(210.dp)
            .background(MaterialTheme.colorScheme.background, MaterialTheme.shapes.small)
            .border(1.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 10.dp)
            .semantics { contentDescription = "Années ${monde.decennie}, ${if (bouclee) monde.titreVoyageur else "décennie en cours"}" },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(9) { i ->
                val allumee = allumage.value * 9 > i
                Box(
                    Modifier
                        .size(8.dp)
                        .background(if (allumee) MaterialTheme.colorScheme.secondary else eteinte, CircleShape),
                )
            }
        }
        Text(
            "ANNÉES ${monde.decennie}",
            style = MaterialTheme.typography.titleMedium.copy(fontFamily = Limelight, letterSpacing = 2.sp, fontSize = 19.sp, fontWeight = FontWeight.Normal),
            color = accent,
            textAlign = TextAlign.Center,
        )
        Text(
            (if (bouclee) monde.titreVoyageur else monde.slogan ?: "en cours de tournage").uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = if (bouclee) MaterialTheme.colorScheme.secondary.copy(alpha = allumage.value) else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
