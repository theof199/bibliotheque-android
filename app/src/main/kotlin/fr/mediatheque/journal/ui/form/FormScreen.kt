package fr.mediatheque.journal.ui.form

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.AfficheVolante
import kotlinx.coroutines.delay
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.voler
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.Navigator
import fr.mediatheque.journal.ui.Screen
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.frise.ChroniqueUi
import fr.mediatheque.journal.ui.frise.ChroniqueViewModel
import fr.mediatheque.journal.ui.frise.EtatBoutonChronique
import fr.mediatheque.journal.ui.realisateur.NomRealisateurTouchable
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.realisateur.filmTmdbIdTouchable
import fr.mediatheque.journal.ui.subtitle
import fr.mediatheque.journal.ui.theme.IconeTabler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun FormScreen(
    vm: FormViewModel,
    nav: Navigator,
    realisateurResolveur: RealisateurResolveur,
    onBack: () -> Unit,
    carton: CartonViewModel? = null,
    /**
     * « Ajouter à la chronique » en bas de la correction (décision 1 du brief du 21 septembre 2026,
     * « la chronique et les salles ») : nul hors `Screen.Edit`, ou quand l'année du film n'est ni en
     * cours ni ouverte (`eligibleChroniqueDepuisEdition`, `Root.kt`) — absente dans les deux cas.
     */
    chronique: ChroniqueViewModel? = null,
    /**
     * L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : nulle sur `Screen.Form`
     * (la création, ouverte depuis la recherche, sans grille dont partir) ; posée sur `Screen.Edit`
     * seulement, même clé que l'accueil ou « Mes films » (`Root.kt`).
     */
    volante: AfficheVolante? = null,
) {
    val ui by vm.ui.collectAsState()
    var showPicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var cartonVisible by remember { mutableStateOf(true) }
    val editing = vm.mode is FormMode.Edit
    // Retour haptique (peaufinage du 23 septembre 2026, geste 10) : partagé par le bouton
    // d'enregistrement ci-dessous et par `RatingDot` plus bas dans ce fichier.
    val haptique = LocalHapticFeedback.current

    // La note qui s'allume (geste 16 du complément du 23 septembre 2026 à l'habillage) : quand la
    // note arrive autrement que par un tap direct (une correction déjà notée, un préremplissage
    // SensCritique), les pastilles se remplissent une à une jusqu'à elle — 40 ms d'écart, haptique
    // `SegmentTick` par pastille, la dernière rebondissant (échelle 1 → 1,25 → 1) une fois posée.
    // `directement`, armé juste avant l'appel à `vm.toggleRating`, court-circuite le remplissage
    // pour un tap : la saisie directe n'est jamais retardée, elle suit le doigt tout de suite.
    var noteBalayee by remember { mutableIntStateOf(ui.rating ?: 0) }
    val rebondNote = remember { Animatable(1f) }
    var directement by remember { mutableStateOf(false) }
    var ratingPrecedent by remember { mutableStateOf(ui.rating) }
    LaunchedEffect(ui.rating) {
        if (directement) {
            noteBalayee = ui.rating ?: 0
            directement = false
        } else if (ui.rating != ratingPrecedent) {
            val cible = ui.rating ?: 0
            noteBalayee = 0
            for (n in 1..cible) {
                delay(40)
                noteBalayee = n
                haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
            }
            if (cible > 0) {
                rebondNote.snapTo(1f)
                rebondNote.animateTo(1.25f, tween(110))
                rebondNote.animateTo(1f, tween(140))
            }
        }
        ratingPrecedent = ui.rating
    }

    val (title, coverUrl, sub) = when (val m = vm.mode) {
        is FormMode.Create -> Triple(m.result.title, m.result.cover_url, subtitle(m.result.metadata.director, m.result.year))
        is FormMode.Edit -> Triple(m.item.media.title, m.item.media.cover_url, subtitle(m.item.media.director, m.item.media.year))
    }

    // Le `ViewModel` ne connaît pas `nav` (correction 1 de la tâche 6) : indexé sur le film ou
    // l'entrée, il survivrait à une recréation d'Activité avec une référence à un `Navigator` mort
    // avec la composition qui l'a créé. C'est `FormScreen`, recomposé avec le `nav` du moment, qui
    // consomme le signal et referme la boucle. `title` passe avec lui, pour la célébration
    // (habillage du 23 septembre 2026, geste 9) — le film et l'année viennent du formulaire, par ce
    // même canal, jamais par un état global.
    LaunchedEffect(ui.done) {
        ui.done?.let {
            nav.home(it, ui.doneCartonTmdbId, ui.doneFilmAnnee, title)
            vm.doneConsumed()
        }
    }
    // Le réalisateur des métadonnées, touchable (décision 3 du brief du 21 septembre 2026, « la
    // page réalisateur », retouche du même jour : sur la création aussi, pas seulement en
    // correction). `filmTmdbIdTouchable` (`RealisateurEtats.kt`) ne rend un identifiant que pour
    // une source TMDB : `SearchResult` peut en porter une autre (SensCritique, Letterboxd, …) dont
    // l'`external_id` n'est pas un `tmdb_id`. Le journal, lui, ne connaît que des films TMDB.
    val (filmTmdbId, realisateur, anneeAffichee) = when (val m = vm.mode) {
        is FormMode.Create -> Triple(filmTmdbIdTouchable(m.result.source, m.result.external_id), m.result.metadata.director, m.result.year)
        is FormMode.Edit -> Triple(filmTmdbIdTouchable("tmdb", m.item.media.external_id), m.item.media.director, m.item.media.year)
    }

    Column(Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { IconeTabler("arrow-left", "Retour") }
        }
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(coverUrl, title, 96.dp, 144.dp, modifier = Modifier.voler(volante))
                Column(Modifier.align(Alignment.CenterVertically)) {
                    Text(title, style = MaterialTheme.typography.titleLarge)
                    if (filmTmdbId != null && !realisateur.isNullOrBlank()) {
                        NomRealisateurTouchable(
                            filmTmdbId = filmTmdbId,
                            nomConnu = realisateur,
                            resolveur = realisateurResolveur,
                            onOuvrirRealisateur = { nav.push(Screen.Realisateur(it)) },
                        )
                        anneeAffichee?.let { annee ->
                            Text(annee.toString(), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
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
                    for (n in rangee) {
                        RatingDot(
                            n,
                            selected = n == noteBalayee,
                            echelle = if (n == noteBalayee) rebondNote.value else 1f,
                        ) { directement = true; vm.toggleRating(n) }
                    }
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
                        // L'idiome Material va dans ce sens : `minimumInteractiveComponentSize()`
                        // d'abord, qui réserve la zone de toucher de 48 dp autour du composant,
                        // `height(40.dp)` ensuite, qui fixe la taille visible à l'intérieur — dans
                        // l'autre ordre, la hauteur fixe s'appliquait à la zone de toucher elle-même
                        // et la ramenait à 40 dp (revue de la vague finale, mineur 5).
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .height(40.dp)
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
            // Le Voyage (brief du 16 septembre 2026) : en bas de la correction, silencieuse tant
            // que le carton n'existe pas encore (`CartonCard` lui-même ne rend rien dans ce cas).
            if (carton != null && cartonVisible) {
                val cartonUi by carton.ui.collectAsState()
                CartonCard(cartonUi, attente = false, onDismiss = { cartonVisible = false })
            }
            // « Ajouter à la chronique » (décision 1 du brief du 21 septembre 2026, « la chronique
            // et les salles ») : nul (donc absent) quand `Root.kt` a jugé le film inéligible.
            if (chronique != null) {
                val chroniqueUi by chronique.ui.collectAsState()
                ChroniqueBoutonEdition(chroniqueUi, onClick = chronique::ajouter)
            }
            Spacer(Modifier.height(8.dp))
        }

        Column(Modifier.padding(16.dp)) {
            Button(
                // Retour haptique (geste 10) : valider un enregistrement est un `Confirm`.
                onClick = { haptique.performHapticFeedback(HapticFeedbackType.Confirm); vm.save() },
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

    ui.pendingSensCritiqueChoice?.let { pending ->
        SensCritiqueChoiceSheet(pending.candidates, onChoose = vm::chooseSensCritiqueCandidate, onDismiss = vm::abandonSensCritiqueChoice)
    }
}

/**
 * « Ajouter à la chronique » en bas de la correction (décision 1 du brief du 21 septembre 2026,
 * « la chronique et les salles ») : « Ajouter », « Le chroniqueur écrit… » pendant la relecture, ou
 * « Dans la chronique » avec le paragraphe affiché dessous — jumeau du bloc de `FicheVoyageScreen`.
 */
@Composable
private fun ChroniqueBoutonEdition(ui: ChroniqueUi, onClick: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        when (ui.etat) {
            EtatBoutonChronique.AJOUTER -> OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
                Text("Ajouter à la chronique")
            }
            EtatBoutonChronique.ECRIT_EN_COURS -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Le chroniqueur écrit…")
            }
            EtatBoutonChronique.DANS_LA_CHRONIQUE -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                Text("Dans la chronique")
            }
            EtatBoutonChronique.ABSENT -> {}
        }
        ui.texte?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
    }
}

/**
 * Une pastille de note : un cercle de 48 dp, corail quand elle est choisie — design §4, §7.
 * `echelle` (geste 16 du complément du 23 septembre 2026) porte le rebond de la pastille qui vient
 * de recevoir le remplissage en cascade — 1 hors rebond, sans effet sur les neuf autres.
 */
@Composable
private fun RatingDot(n: Int, selected: Boolean, echelle: Float = 1f, onClick: () -> Unit) {
    val fond by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = tween(150), label = "note",
    )
    val texte = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
    // Retour haptique (peaufinage du 23 septembre 2026, geste 10) : poser une note est un des
    // gestes de la palette Compose, `SegmentTick` — au tap comme au retrait.
    val haptique = LocalHapticFeedback.current
    Box(
        Modifier
            .size(48.dp)
            .scale(echelle)
            .background(fond, CircleShape)
            .clickable(onClick = { haptique.performHapticFeedback(HapticFeedbackType.SegmentTick); onClick() })
            .semantics { contentDescription = "Note $n sur 10"; this.selected = selected },
        contentAlignment = Alignment.Center,
    ) { Text("$n", style = MaterialTheme.typography.labelLarge, color = texte) }
}
