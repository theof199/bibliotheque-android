package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier

/**
 * Le ticket (décision 2 du brief du 21 septembre 2026, « le ticket ») : le calque qui l'annonce par-
 * dessus l'écran courant (`TicketCalque`, `Root.kt`), et sa ligne du portefeuille (`LigneTicketPortefeuille`,
 * décision 3, `ui/profile/ProfileScreen.kt`) — le même dessin de papier (`TicketPapier`), en grand
 * puis en petit. Dessiné au `Canvas` (`bordsPerfores`, `barrerTicket`, `Pellicule.kt`), jamais une image.
 */

private val LARGEUR_TICKET_CALQUE = 240.dp
private val HAUTEUR_TICKET_CALQUE = 148.dp
private val LARGEUR_TICKET_PORTEFEUILLE = 96.dp
private val HAUTEUR_TICKET_PORTEFEUILLE = 60.dp

/**
 * Le papier du ticket : rectangle `#F2E8D5` à bords perforés, penché de −3°, « ADMIS UNE PERSONNE »
 * en petit espacé, l'année dans l'accent du monde de cette année-là, le motif en dessous.
 * `compact` réduit la typographie pour la ligne du portefeuille — même dessin, en petit.
 */
@Composable
private fun TicketPapier(annee: Int, motif: String, largeur: Dp, hauteur: Dp, compact: Boolean, barre: Boolean = false) {
    val monde = mondeDe(annee)
    Box(
        Modifier
            .size(largeur, hauteur)
            .rotate(-3f)
            .background(PapierJauni, RoundedCornerShape(6.dp))
            .drawBehind {
                bordsPerfores(CadrePapier)
                if (barre) barrerTicket(CadrePapier)
            }
            .padding(if (compact) 6.dp else 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(if (compact) 1.dp else 4.dp)) {
            Text(
                "ADMIS UNE PERSONNE",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontSize = if (compact) 7.sp else MaterialTheme.typography.labelSmall.fontSize),
                color = TextePapier,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            Text(
                annee.toString(),
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
                color = monde.accent,
            )
            Text(
                motif,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.bodyMedium,
                color = TextePapier,
                textAlign = TextAlign.Center,
                maxLines = if (compact) 2 else 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Le calque du ticket (décision 2) : un fond noir translucide qui bloque l'écran courant, le
 * ticket dessiné au centre, deux boutons. « Garder dans le portefeuille » et « Utiliser maintenant »
 * appellent chacun `onGarder`/`onUtiliser` — c'est `FriseViewModel` qui pose `POST .../montre` dans
 * les deux cas et referme le calque (`ticketAMontrer` retombe à `null`).
 */
@Composable
fun TicketCalque(ticket: TicketAMontrerUi, onUtiliser: () -> Unit, onGarder: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f))
            // Bloque le passage vers l'écran en dessous, sans rien y faire — décoration exclue,
            // c'est l'absence de trou dans le toucher qui compte ici.
            .clickable(onClick = {}),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
            TicketPapier(ticket.annee, ticket.motif, LARGEUR_TICKET_CALQUE, HAUTEUR_TICKET_CALQUE, compact = false)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onGarder) {
                    Text("Garder dans le portefeuille", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = onUtiliser) { Text("Utiliser maintenant") }
            }
        }
    }
}

/**
 * Une ligne du portefeuille (décision 3) : le ticket en petit, à gauche. Non utilisé : un bouton
 * « Utiliser » à droite. Utilisé : grisé, barré, « utilisé le *date* » à la place du bouton.
 */
@Composable
fun LigneTicketPortefeuille(ticket: TicketPortefeuilleUi, onUtiliser: () -> Unit) {
    val compose = ticket.utiliseLe != null
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp).alpha(if (compose) 0.5f else 1f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TicketPapier(ticket.annee, ticket.motif, LARGEUR_TICKET_PORTEFEUILLE, HAUTEUR_TICKET_PORTEFEUILLE, compact = true, barre = compose)
        if (compose) {
            Text(
                "utilisé le ${formatDate(ticket.utiliseLe!!.substring(0, 10))}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textDecoration = TextDecoration.LineThrough,
                modifier = Modifier.weight(1f),
            )
        } else {
            TextButton(onClick = onUtiliser) { Text("Utiliser") }
        }
    }
}
