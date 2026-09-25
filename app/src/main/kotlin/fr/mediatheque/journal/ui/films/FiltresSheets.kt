package fr.mediatheque.journal.ui.films

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.Puce
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * La rangée de puces de « Mes films · le hall » et ses deux feuilles, Note et Réaction (spec de
 * Léon, décisions du propriétaire du 24 septembre 2026). Chaque tap s'applique tout de suite au
 * `FiltresFilmsViewModel` : pas de bouton « OK », une feuille se ferme au tap dehors ou au retour.
 */

/**
 * Les trois puces sous le champ : Date (un tap inverse l'ordre), Note et Réaction (chacune ouvre
 * sa feuille). Défilement horizontal si les trois ne tiennent pas, à la taille de police maximale.
 */
@Composable
fun PucesFiltres(
    filtres: FiltresFilms,
    onDate: () -> Unit,
    onNote: () -> Unit,
    onReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Puce(libellePuceDate(filtres), active = filtres.tri == TriFilms.DATE_ASC, onClick = onDate, icone = "arrows-sort")
        Puce(
            libellePuceNote(filtres),
            active = filtres.tri == TriFilms.NOTE_DESC || filtres.notes.isNotEmpty(),
            onClick = onNote,
            icone = "chevron-down",
        )
        Puce(libellePuceReaction(filtres), active = filtres.reactions.isNotEmpty(), onClick = onReaction, icone = "chevron-down")
    }
}

/**
 * La feuille de la puce Note, qui porte le tri par note **et** le filtre par notes (décisions du
 * propriétaire des 24 et 25 septembre 2026) : « Trier » (Date / Note), puis « Notes », dix
 * pastilles compactes cochables à plusieurs — cocher 4 et 7 montre les 4 et les 7.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleNote(filtres: FiltresFilms, vm: FiltresFilmsViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Note", style = MaterialTheme.typography.titleMedium)
            Text("Trier", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Puce("Date", active = filtres.tri != TriFilms.NOTE_DESC, onClick = { vm.setTri(TriFilms.DATE_DESC) })
                Puce("Note", active = filtres.tri == TriFilms.NOTE_DESC, onClick = { vm.setTri(TriFilms.NOTE_DESC) })
            }
            Text("Notes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            // Deux rangées de cinq : dix pastilles de 30 dp, chacune dans sa cible de 48, ne
            // tiennent pas sur une seule rangée de 360 dp moins les marges.
            for (rangee in listOf(1..5, 6..10)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (n in rangee) {
                        PastilleNote(n, selected = n in filtres.notes) { vm.basculerNote(n) }
                    }
                }
            }
            TextButton(
                onClick = { vm.setTri(TriFilms.DATE_DESC); vm.effacerNotes() },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Effacer") }
        }
    }
}

/**
 * Une pastille de note, compacte : cercle de 30 dp dans une cible de 48, corail quand elle est
 * cochée — les couleurs de `RatingDot` du formulaire, qui reste privé à `FormScreen`. Un tap la
 * coche ou la décoche, les autres pastilles gardent leur état.
 */
@Composable
private fun PastilleNote(n: Int, selected: Boolean, onClick: () -> Unit) {
    val fond = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
    val texte = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .minimumInteractiveComponentSize()
            .size(30.dp)
            .background(fond, CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = "Note $n"
                this.selected = selected
            },
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = texte) }
}

/** La feuille de la puce Réaction : les treize réactions en puces, plusieurs cochables. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FeuilleReactions(filtres: FiltresFilms, vm: FiltresFilmsViewModel, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Réaction", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (reaction in Reactions.all) {
                    Puce(
                        Reactions.label(reaction.key),
                        active = reaction.key in filtres.reactions,
                        onClick = { vm.basculerReaction(reaction.key) },
                        description = Reactions.phrase(reaction.key),
                    )
                }
            }
            TextButton(
                onClick = { vm.effacerReactions() },
                modifier = Modifier.align(Alignment.End),
            ) { Text("Effacer") }
        }
    }
}
