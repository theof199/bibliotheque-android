package fr.mediatheque.journal.ui.frise

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Le calque du ticket, posé par `Root.kt` au-dessus de l'`AnimatedContent` (décision 2 du brief du
 * 21 septembre 2026, « le ticket ») : nourri par `FriseViewModel`, dès que `ticketAMontrer` est non
 * nul, le calque s'affiche par-dessus l'écran courant, quel qu'il soit. « Garder » et « Utiliser
 * maintenant » ferment tous deux le calque (`ticketAMontrer` retombe à `null` côté
 * `FriseViewModel`, jamais ici) — le back ne le renvoie plus ensuite.
 */
@Composable
fun TicketHote(frise: FriseViewModel) {
    val friseUiPourTicket by frise.ui.collectAsState()
    // Le calque est un dialogue maison, pas un `AlertDialog` (peaufinage du 23 septembre
    // 2026, geste 11) : `AnimatedVisibility` (fondu + échelle) lui donne une entrée et une
    // sortie, plutôt que d'apparaître net. `dernierTicket` garde le dernier ticket connu
    // pendant que `ticketAMontrer` est déjà retombé à `null` : sans lui, le contenu
    // disparaîtrait d'un coup au milieu de la sortie animée.
    var dernierTicket by remember { mutableStateOf<TicketAMontrerUi?>(null) }
    LaunchedEffect(friseUiPourTicket.voyage.ticketAMontrer) {
        friseUiPourTicket.voyage.ticketAMontrer?.let { dernierTicket = it }
    }
    AnimatedVisibility(
        visible = friseUiPourTicket.voyage.ticketAMontrer != null,
        enter = fadeIn(tween(200)) + scaleIn(initialScale = 0.92f, animationSpec = tween(200)),
        exit = fadeOut(tween(200)) + scaleOut(targetScale = 0.92f, animationSpec = tween(200)),
    ) {
        dernierTicket?.let { ticket ->
            TicketCalque(
                ticket = ticket,
                onUtiliser = { frise.utiliserTicketAMontrer(ticket.annee) },
                onGarder = { frise.garderTicket(ticket.annee) },
            )
        }
    }
}
