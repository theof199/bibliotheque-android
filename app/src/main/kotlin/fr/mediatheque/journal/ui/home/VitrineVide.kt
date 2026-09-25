package fr.mediatheque.journal.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.reflet
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * La vitrine vide (« Accueil · la vitrine vide », planche de Léon validée le 25 septembre 2026) :
 * à la place d'« Aucun film pour l'instant. » en texte seul, un cadre d'affiche encore vide — bord
 * pointillé, même verre que les jaquettes (`reflet`, `AfficheVitrine.kt`) — qui porte lui-même
 * « Ajouter un film ». L'accueil cache son bouton rond tant que la vitrine est vide : un seul
 * corail à l'écran, et c'est celui-ci.
 */
@Composable
fun VitrineVide(onAjouter: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        CadreVide(onAjouter)
        Text(
            "La vitrine attend sa première affiche.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Le cadre 2:3 de 173 dp : la largeur d'une jaquette de la grille à deux colonnes sur un téléphone
 * ordinaire, pour que la première affiche vienne prendre exactement sa place. Un seul bouton pour
 * TalkBack, « Ajouter un film », plutôt qu'une icône et un texte lus l'un après l'autre.
 */
@Composable
private fun CadreVide(onAjouter: () -> Unit) {
    val forme = MaterialTheme.shapes.small
    val bord = MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)
    Box(
        Modifier
            .width(173.dp)
            .aspectRatio(2f / 3f)
            .clip(forme)
            .bordPointille(bord, forme)
            .reflet(forme)
            .clickable(onClick = onAjouter)
            .clearAndSetSemantics {
                contentDescription = "Ajouter un film"
                role = Role.Button
                onClick { onAjouter(); true }
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                Modifier.size(44.dp).border(1.5.dp, MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconeTabler("plus", null, tint = MaterialTheme.colorScheme.primary)
            }
            Text("Ajouter un film", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

/**
 * Un bord pointillé de 1 dp (tirets de 6, jours de 4) : `border` n'accepte pas de `PathEffect`,
 * d'où le contour dessiné à la main, dans la forme du cadre.
 */
private fun Modifier.bordPointille(couleur: Color, forme: Shape): Modifier = this.drawBehind {
    val trait = 1.dp.toPx()
    drawOutline(
        forme.createOutline(size, layoutDirection, this),
        couleur,
        style = Stroke(
            width = trait,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        ),
    )
}
