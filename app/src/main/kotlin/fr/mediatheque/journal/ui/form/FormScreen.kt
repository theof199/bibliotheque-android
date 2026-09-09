package fr.mediatheque.journal.ui.form

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.Navigator
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.subtitle
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun FormScreen(vm: FormViewModel, nav: Navigator, onBack: () -> Unit) {
    val ui by vm.ui.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val editing = vm.mode is FormMode.Edit

    // Le `ViewModel` ne connaît pas `nav` (correction 1 de la tâche 6) : indexé sur le film ou
    // l'entrée, il survivrait à une recréation d'Activité avec une référence à un `Navigator` mort
    // avec la composition qui l'a créé. C'est `FormScreen`, recomposé avec le `nav` du moment, qui
    // consomme le signal et referme la boucle.
    LaunchedEffect(ui.done) {
        ui.done?.let {
            nav.home(it)
            vm.doneConsumed()
        }
    }

    val (title, coverUrl, sub) = when (val m = vm.mode) {
        is FormMode.Create -> Triple(m.result.title, m.result.cover_url, subtitle(m.result.metadata.director, m.result.year))
        is FormMode.Edit -> Triple(m.item.media.title, m.item.media.cover_url, subtitle(m.item.media.director, m.item.media.year))
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(coverUrl, title, 96.dp, 144.dp)
                Column(Modifier.align(Alignment.CenterVertically)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // La date : un champ qui se lit, et s'ouvre au toucher.
            Box {
                OutlinedTextField(
                    value = formatDate(ui.date.toString()),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Vu le") },
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box(Modifier.matchParentSize().clickable { showPicker = true })
            }

            Text("Note", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (rangee in listOf(1..5, 6..10)) {
                Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    for (n in rangee) RatingDot(n, selected = ui.rating == n) { vm.toggleRating(n) }
                }
            }

            Text("Réactions", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val cles = Reactions.all.map { it.key } + (ui.reactions - Reactions.all.map { it.key }.toSet())
                for (key in cles) {
                    FilterChip(
                        selected = key in ui.reactions,
                        onClick = { vm.toggleReaction(key) },
                        label = { Text(Reactions.label(key), style = MaterialTheme.typography.bodyMedium) },
                        shape = CircleShape,
                        // 40 dp de haut (design §4), et une cible tactile de 48 dp par-dessus
                        // (décision 4 de la tâche 6) : les deux valeurs ne se confondent pas, la
                        // seconde ne fait qu'agrandir la zone de toucher autour de la première.
                        modifier = Modifier
                            .height(40.dp)
                            .minimumInteractiveComponentSize()
                            .semantics { contentDescription = Reactions.phrase(key) },
                    )
                }
            }

            OutlinedTextField(
                value = ui.comment,
                onValueChange = vm::setComment,
                label = { Text("Commentaire") },
                supportingText = { Text("Rien qu’à toi") },
                minLines = 3,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            )

            ui.error?.let { error ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    // Décision 3 : le message du back reste le sien ; cette ligne dit seulement
                    // que le film, lui, est bien ajouté — jamais l'inverse.
                    ui.errorContext?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ErrorBlock(error.message ?: "", retryable = error.retryable, onRetry = vm::retry)
                }
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(16.dp)) {
            Button(
                onClick = vm::save,
                enabled = !ui.busy,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (ui.busy) {
                    CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                    Spacer(Modifier.size(8.dp))
                }
                Text(if (editing) "Corriger" else "Enregistrer")
            }
            if (editing) {
                TextButton(
                    onClick = { confirmDelete = true },
                    enabled = !ui.busy,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) { Text("Supprimer", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }

    if (showPicker) {
        val aujourdHuiUtc = LocalDate.now().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() - 1
        val state = rememberDatePickerState(
            initialSelectedDateMillis = ui.date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= aujourdHuiUtc
            },
        )
        DatePickerDialog(
            onDismissRequest = { showPicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    showPicker = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showPicker = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ce visionnage ?") },
            text = { Text("Le commentaire et les réactions partent avec.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
}

/** Une pastille de note : un cercle de 48 dp, corail quand elle est choisie — design §4, §7. */
@Composable
private fun RatingDot(n: Int, selected: Boolean, onClick: () -> Unit) {
    val fond by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(150), label = "note",
    )
    val texte = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    Box(
        Modifier
            .size(48.dp)
            .background(fond, CircleShape)
            .clickable(onClick = onClick)
            .semantics { contentDescription = "Note $n sur 10"; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = texte) }
}
