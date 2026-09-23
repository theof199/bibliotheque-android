package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.theme.Animation
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
 *
 * `poinconEchelle` (geste 15 de l'habillage du 23 septembre 2026, complément du 23 septembre) : nul
 * hors poinçon, sinon l'échelle 0 → 1 avec dépassement du trou qui perce le papier — un vrai trou
 * (`BlendMode.Clear` sur une composition hors écran), pas une pastille de la couleur du fond, pour
 * qu'il reste un trou quel que soit ce qu'il y a derrière le ticket.
 */
@Composable
private fun TicketPapier(
    annee: Int,
    motif: String,
    largeur: Dp,
    hauteur: Dp,
    compact: Boolean,
    barre: Boolean = false,
    poinconEchelle: Float = 0f,
    modifier: Modifier = Modifier,
) {
    val monde = mondeDe(annee)
    Box(
        modifier
            .size(largeur, hauteur)
            .rotate(-3f)
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .background(PapierJauni, RoundedCornerShape(6.dp))
            .drawBehind {
                bordsPerfores(CadrePapier)
                if (barre) barrerTicket(CadrePapier)
            }
            .let {
                if (poinconEchelle <= 0f) {
                    it
                } else {
                    it.drawWithContentTrouPoinconne(poinconEchelle)
                }
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
 * Le trou du poinçon (geste 15) : un vrai trou dans le papier — `BlendMode.Clear` efface plutôt que
 * peindre, ce que `compositingStrategy = CompositingStrategy.Offscreen` (posé sur le `Box` du
 * ticket) rend possible : sans lui, `Clear` effacerait jusqu'au fond de l'écran plutôt que jusqu'au
 * papier seul. `echelle` peut dépasser 1 (le dépassement de l'animation), le trou grandissant alors
 * un instant plus que sa taille finale avant de s'y stabiliser.
 */
private fun Modifier.drawWithContentTrouPoinconne(echelle: Float): Modifier = drawWithContent {
    drawContent()
    drawCircle(
        color = Color.Black,
        radius = size.minDimension * 0.11f * echelle,
        center = Offset(size.width * 0.22f, size.height * 0.5f),
        blendMode = BlendMode.Clear,
    )
}

/**
 * Le calque du ticket (décision 2) : un fond noir translucide qui bloque l'écran courant, le
 * ticket dessiné au centre, deux boutons. « Garder dans le portefeuille » referme tout de suite
 * (`onGarder`). « Utiliser maintenant » poinçonne d'abord le ticket (geste 15 du complément du
 * 23 septembre 2026 à l'habillage) — le trou perce à l'échelle avec dépassement, le ticket recule
 * légèrement, une haptique confirme, et l'animation Lottie `ticket-1.json` joue une fois par-dessus
 * (brief des animations des célébrations du 23 septembre 2026, soir, en plus du trou et non à sa
 * place) — puis seulement `onUtiliser` referme le calque ; c'est `FriseViewModel` qui pose
 * `POST .../montre` dans les deux cas.
 */
@Composable
fun TicketCalque(ticket: TicketAMontrerUi, onUtiliser: () -> Unit, onGarder: () -> Unit) {
    val haptique = LocalHapticFeedback.current
    var poinconne by remember(ticket.annee) { mutableStateOf(false) }
    val echellePoincon = remember(ticket.annee) { Animatable(0f) }
    val recul = remember(ticket.annee) { Animatable(0f) }

    LaunchedEffect(poinconne) {
        if (!poinconne) return@LaunchedEffect
        launch {
            recul.animateTo(1f, tween(120))
            recul.animateTo(0f, tween(200))
        }
        // L'échelle 0 → 1 avec dépassement (le brief) : une courbe qui grimpe au-delà de 1 avant
        // de s'y stabiliser, le trou paraissant percer d'un coup sec plutôt que grandir sagement.
        echellePoincon.animateTo(1f, tween(350, easing = CubicBezierEasing(0.3f, 1.7f, 0.4f, 1f)))
        haptique.performHapticFeedback(HapticFeedbackType.Confirm)
        delay(300)
        onUtiliser()
    }

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
            Box(contentAlignment = Alignment.Center) {
                TicketPapier(
                    ticket.annee,
                    ticket.motif,
                    LARGEUR_TICKET_CALQUE,
                    HAUTEUR_TICKET_CALQUE,
                    compact = false,
                    poinconEchelle = echellePoincon.value,
                    modifier = Modifier.graphicsLayer { translationY = recul.value * 6.dp.toPx() },
                )
                // `ticket-1.json` par-dessus, en plus du trou perforé dans le papier — pas à sa
                // place : le trou reste le vrai poinçon, l'animation en est le geste.
                if (poinconne) {
                    Animation(
                        nom = "ticket-1",
                        iterations = 1,
                        modifier = Modifier.size(LARGEUR_TICKET_CALQUE, HAUTEUR_TICKET_CALQUE),
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onGarder, enabled = !poinconne) {
                    Text("Garder dans le portefeuille", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Button(onClick = { poinconne = true }, enabled = !poinconne) { Text("Utiliser maintenant") }
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
