package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
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
 */
@Composable
fun CartonTitre(monde: Monde, entree: EtatEntree, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(76.dp)
            .background(FondCarton, MaterialTheme.shapes.extraSmall)
            .border(1.dp, monde.accent, MaterialTheme.shapes.extraSmall),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // .1 em à 20 sp (§D) : 2 sp — la même conversion, en littéral, que le reste de
            // l'écran du Voyage (`letterSpacing` y est toujours posé en `sp`, jamais en `em`).
            Text(
                monde.nom.uppercase(),
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = Limelight, letterSpacing = 2.sp, fontSize = 20.sp),
                color = monde.accent,
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
