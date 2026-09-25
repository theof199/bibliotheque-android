package fr.mediatheque.journal.ui.films

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.EtatVide
import fr.mediatheque.journal.ui.afficheVolante
import fr.mediatheque.journal.ui.profile.ProfileUi
import fr.mediatheque.journal.ui.theme.FiletOr
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.voler
import kotlinx.coroutines.flow.distinctUntilChanged

// Pas de snackbar ici : cet écran n'a jamais reçu `nav`, rien ne pousse de message vers
// « Mes films ». Seul l'accueil collecte `nav.messages` (revue du tour de correction 1, et
// Critique 1 de la vague finale pour le mécanisme lui-même).
/**
 * « Mes films · le hall » (spec de Léon, décisions du propriétaire du 24 septembre 2026) : en-tête
 * et compte, filet or, recherche, trois puces (Date, Note, Réaction) et leurs feuilles, la liste
 * filtrée en `LigneFilm` glissables, les deux états vides, le dialogue de suppression.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FilmsScreen(
    vm: FilmsViewModel,
    filtres: FiltresFilmsViewModel,
    /** Le compte de l'en-tête (`GET /stats`, `ProfileViewModel`). */
    compte: ProfileUi,
    onBack: () -> Unit,
    onOpen: (JournalItem) -> Unit,
    bottomBar: @Composable () -> Unit,
    /** L'action de l'état vide (point 16 de la revue du 24 septembre 2026) : la même recherche que le bouton rond de l'accueil. */
    onAdd: () -> Unit = {},
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : « Mes films » est un des
    // deux bouts de la paire vers « la fiche d'entrée » (`Screen.Edit`, `Root.kt`).
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
) {
    val ui by vm.ui.collectAsState()
    val f by filtres.filtres.collectAsState()
    val liste = rememberLazyListState()
    val visibles = remember(ui.items, f) { appliquerFiltres(ui.items, f) }

    // Une seule ligne ouverte à la fois : l'identifiant de son visionnage, `null` si aucune.
    var ligneOuverte by remember { mutableStateOf<String?>(null) }
    var aSupprimer by remember { mutableStateOf<JournalItem?>(null) }
    var feuilleNote by remember { mutableStateOf(false) }
    var feuilleReactions by remember { mutableStateOf(false) }

    // Un tri, un filtre ou une recherche ne peut rien affirmer sur des pages pas encore chargées :
    // la liste doit alors être complète. Rejoué aussi après un `refresh()` (entrée sur l'écran),
    // qui repart de la page 1 et retombe à `endReached` faux.
    LaunchedEffect(f.actifs, ui.endReached, ui.loading) {
        if (f.actifs && !ui.endReached && !ui.loading) vm.chargerTout()
    }

    // Sans filtre, la pagination d'avant : charger la suite quand la dernière ligne visible
    // approche de la fin. Avec un filtre, `chargerTout()` ci-dessus s'en charge déjà.
    LaunchedEffect(liste) {
        snapshotFlow { liste.layoutInfo.visibleItemsInfo.lastOrNull()?.index }
            .distinctUntilChanged()
            .collect { index -> if (!f.actifs && index != null && index >= ui.items.size - 5) vm.loadMore() }
    }
    // Défiler referme la ligne ouverte (« un tap ailleurs referme », au sens large).
    LaunchedEffect(liste) {
        snapshotFlow { liste.isScrollInProgress }.collect { if (it) ligneOuverte = null }
    }
    // Tout changement de tri ou de filtre (recherche comprise) ramène en haut de la liste (décision
    // du propriétaire du 25 septembre 2026) et referme la ligne ouverte. Pas à la première
    // composition : revenir sur l'écran avec des filtres mémorisés doit garder le défilement
    // sauvegardé (`rememberSaveableStateHolder`, Root.kt), seul un changement fait pendant la
    // visite remonte. `scrollToItem`, pas `animateScrollToItem` : un saut net, le contenu de la
    // liste change de toute façon.
    var filtresVus by remember { mutableStateOf(f) }
    LaunchedEffect(f) {
        if (f != filtresVus) {
            filtresVus = f
            ligneOuverte = null
            liste.scrollToItem(0)
        }
    }

    // `Scaffold` plutôt que `safeDrawingPadding()` : son `bottomBar` (la barre du 14 septembre
    // 2026, « Profil » sélectionnée puisque cet écran ne s'ouvre que depuis lui) réserve sa
    // propre place dans le `padding` reçu ci-dessous, comme les insets système que
    // `safeDrawingPadding()` réservait seul avant elle.
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { IconeTabler("arrow-left", "Retour") }
                Text("Mes films", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                // Absent tant que `GET /stats` n'a pas répondu : rien de réservé, pas de « … ».
                compteEnTete(compte.total, compte.thisYear)?.let {
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Le filet or sous l'en-tête : choix de gabarit adopté par le propriétaire le
            // 24 septembre 2026, à poser partout ensuite. Pas de perforations sur cet écran : la
            // pellicule reste au Voyage.
            FiletOr()
            ChampRecherche(f.texte, filtres::setTexte, Modifier.padding(horizontal = 16.dp).padding(top = 12.dp))
            PucesFiltres(
                f,
                onDate = filtres::basculerOrdreDate,
                onNote = { feuilleNote = true },
                onReaction = { feuilleReactions = true },
            )
            // Le journal se charge en entier (voir `chargerTout()` plus haut) : la barre de la
            // recherche, 2 dp `primary`.
            if (f.actifs && !ui.endReached) {
                LinearProgressIndicator(
                    Modifier.fillMaxWidth().padding(top = 8.dp).height(2.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            // L'erreur de suppression passe par le même bloc que celle du chargement : « Réessayer »
            // recharge la liste — la suite si elle est incomplète, depuis le début sinon (un
            // `loadMore()` sur une liste complète ne ferait rien), ce qui vaut aussi pour elle.
            ui.error?.let {
                ErrorBlock(
                    it.message ?: "",
                    retryable = it.retryable,
                    onRetry = { if (ui.endReached) vm.refresh() else vm.loadMore() },
                    modifier = Modifier.padding(16.dp),
                )
            }
            when {
                ui.items.isEmpty() && ui.endReached -> {
                    // État vide (point 16 de la revue du 24 septembre 2026) : une icône, la phrase,
                    // une action — « Ajouter un film » plutôt qu'un texte seul.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EtatVide(
                            icone = "movie",
                            phrase = "Aucun film pour l’instant.",
                            libelleAction = "Ajouter un film",
                            onAction = onAdd,
                        )
                    }
                }
                visibles.isEmpty() && f.actifs && ui.endReached -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        EtatVide(
                            icone = "search",
                            phrase = if (f.texte.isNotBlank()) "Rien trouvé pour « ${f.texte.trim()} »." else "Rien avec ces filtres.",
                            libelleAction = "Effacer",
                            onAction = filtres::effacer,
                        )
                    }
                }
                else -> LazyColumn(state = liste, contentPadding = PaddingValues(vertical = 8.dp)) {
                    items(visibles, key = { it.entry.id }) { item ->
                        val id = item.entry.id
                        // La liste se retasse (geste 3 du peaufinage du 23 septembre 2026) au lieu
                        // de sauter quand un film change de place ou disparaît.
                        val volante = afficheVolante(sharedTransitionScope, animatedVisibilityScope, "affiche-journal-$id")
                        LigneFilm(
                            item,
                            ouverte = ligneOuverte == id,
                            onOuvrir = { ligneOuverte = id },
                            onFermer = { if (ligneOuverte == id) ligneOuverte = null },
                            // Une autre ligne ouverte : ce tap la referme, il n'ouvre pas ce film.
                            onClick = { if (ligneOuverte != null) ligneOuverte = null else onOpen(item) },
                            onCorriger = { onOpen(item) },
                            onSupprimer = { aSupprimer = item },
                            // Pas pendant un chargement : une page 1 en vol pourrait ramener la
                            // ligne tout juste supprimée. Ni pendant une autre suppression.
                            supprimerActif = !ui.loading && !ui.suppressionEnCours,
                            modifier = Modifier.animateItem(),
                            coverModifier = Modifier.voler(volante),
                        )
                    }
                    if (ui.loading && !f.actifs) {
                        item {
                            Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }

    if (feuilleNote) FeuilleNote(f, filtres, onDismiss = { feuilleNote = false })
    if (feuilleReactions) FeuilleReactions(f, filtres, onDismiss = { feuilleReactions = false })

    // Le même dialogue que celui du formulaire (`FormScreen`), mêmes mots.
    aSupprimer?.let { item ->
        AlertDialog(
            onDismissRequest = { aSupprimer = null },
            title = { Text("Supprimer ce visionnage ?") },
            text = { Text("Le commentaire et les réactions partent avec.") },
            confirmButton = {
                TextButton(onClick = {
                    aSupprimer = null
                    ligneOuverte = null
                    vm.supprimer(item.entry.id)
                }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { aSupprimer = null }) { Text("Annuler") } },
        )
    }
}

/**
 * Le champ de recherche : 48 dp, fond `surfaceContainer`, coins 12, loupe en tête, croix
 * « Effacer » quand il y a du texte (elle n'efface que le texte, pas les puces). Jamais focalisé
 * à l'arrivée, contrairement à l'écran « Recherche » : on vient ici pour parcourir d'abord.
 */
@Composable
private fun ChampRecherche(texte: String, onTexte: (String) -> Unit, modifier: Modifier = Modifier) {
    val focus = LocalFocusManager.current
    val secondaire = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(12.dp))
            .padding(start = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconeTabler("search", null, tint = secondaire)
        Spacer(Modifier.width(8.dp))
        BasicTextField(
            value = texte,
            onValueChange = onTexte,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { focus.clearFocus() }),
            modifier = Modifier.weight(1f).padding(vertical = 12.dp),
            decorationBox = { champ ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (texte.isEmpty()) Text("Un titre, un réalisateur", style = MaterialTheme.typography.bodyLarge, color = secondaire)
                    champ()
                }
            },
        )
        if (texte.isNotEmpty()) {
            IconButton(onClick = { onTexte("") }) { IconeTabler("x", "Effacer", tint = secondaire) }
        } else {
            Spacer(Modifier.width(12.dp))
        }
    }
}
