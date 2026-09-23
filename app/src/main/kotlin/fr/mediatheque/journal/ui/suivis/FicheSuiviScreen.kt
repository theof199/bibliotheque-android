package fr.mediatheque.journal.ui.suivis

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.showBriefly

/**
 * La fiche d'un réalisateur ou d'une saga suivis (brief du 15 septembre
 * 2026, puis généralisée le même jour pour les sagas) : ses films dans
 * l'ordre, de la plus ancienne sortie à la plus récente, avec sa note à
 * droite quand je l'ai vu. Le premier film non vu et non introuvable porte
 * une pastille corail « à voir » — c'est celui que la liste annonce sous le
 * nom, et celui que l'accueil met dans « Ensuite » quand c'est cette entité
 * qui est en cours.
 *
 * Un film peut aussi être marqué introuvable (décision du propriétaire du
 * 15 septembre 2026) : un appui long sur un film non vu ouvre la feuille qui
 * marque ou démarque. L'interrupteur en tête masque les films marqués, ou les
 * grise avec la mention « introuvable » à droite — jamais hors d'atteinte
 * d'un appui long, dans un cas comme dans l'autre.
 *
 * Sur une saga seulement (brief « les films de saga ajoutés à la main »,
 * 15 septembre 2026 : une collection TMDB n'est pas toujours complète), un
 * bouton « Ajouter un film » sous le nom ouvre `Screen.ChoisirFilmDeSaga`, la
 * recherche existante en mode « choisir ». Un film ainsi ajouté porte
 * « ajouté » à droite de son titre, et un appui long dessus propose aussi
 * « Retirer de la saga », même s'il est déjà vu.
 *
 * Empilée depuis `Screen.Suivis`, sans barre du bas. Elle lit le `ViewModel`
 * partagé plutôt que de recharger : la liste a déjà tiré les films.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FicheSuiviScreen(
    vm: SuivisViewModel,
    source: SourceSuivi,
    tmdbId: Int,
    onBack: () -> Unit,
    onOuvrirVu: (JournalItem) -> Unit,
    onOuvrirAVoir: (SearchResult) -> Unit,
    onSupprimer: () -> Unit,
    onAjouterFilm: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val etatSource = if (source == SourceSuivi.REALISATEURS) ui.realisateurs else ui.sagas
    val entite = etatSource.entites.firstOrNull { it.tmdbId == tmdbId }
    val etat = etatSource.filmographies[tmdbId] ?: EtatFilmographie.EnAttente
    var confirmation by remember { mutableStateOf(false) }
    var feuillePour by remember { mutableStateOf<FilmSuivi?>(null) }

    val snackbar = remember { SnackbarHostState() }
    // Même canal que la liste (`SuivisScreen`) : un `ViewModel` partagé, un seul `messages`. Les
    // deux écrans ne sont jamais composés ensemble, donc jamais collecté deux fois pour un même
    // message.
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }

    if (confirmation) {
        AlertDialog(
            onDismissRequest = { confirmation = false },
            title = { Text("Ne plus suivre ${entite?.nom ?: "…"} ?") },
            text = { Text("Ses films disparaîtront de la liste. Tes films vus, eux, restent au journal.") },
            confirmButton = {
                TextButton(onClick = { confirmation = false; onSupprimer() }) { Text("Ne plus suivre") }
            },
            dismissButton = { TextButton(onClick = { confirmation = false }) { Text("Annuler") } },
        )
    }

    feuillePour?.let { film ->
        IntrouvableSheet(
            film = film,
            peutRetirerDeSaga = peutRetirerDeSaga(film),
            onMarquer = { feuillePour = null; vm.marquerIntrouvable(source, tmdbId, film.tmdb_id) },
            onRetirer = { feuillePour = null; vm.retirerIntrouvable(source, tmdbId, film.tmdb_id) },
            onRetirerDeSaga = { feuillePour = null; vm.retirerFilm(tmdbId, film.tmdb_id) },
            onDismiss = { feuillePour = null },
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = {
            SnackbarHost(snackbar) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
                Text(
                    entite?.nom ?: "",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { confirmation = true }) {
                    Icon(Icons.Filled.Delete, contentDescription = "Ne plus suivre")
                }
            }

            // Seulement sur une saga (brief « les films de saga ajoutés à la main »,
            // 15 septembre 2026) : une collection TMDB n'est pas toujours complète,
            // rien de tout cela sur la filmographie d'un réalisateur.
            if (source == SourceSuivi.SAGAS) {
                TextButton(onClick = onAjouterFilm, modifier = Modifier.padding(start = 8.dp)) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.padding(end = 4.dp))
                    Text("Ajouter un film")
                }
            }

            when (etat) {
                EtatFilmographie.EnAttente -> Box(
                    Modifier.weight(1f).fillMaxWidth(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }

                EtatFilmographie.Indisponible -> ErrorBlock(
                    "Ses films sont indisponibles.",
                    retryable = true,
                    onRetry = { vm.rechargerFilmographie(source, tmdbId) },
                    modifier = Modifier.padding(16.dp),
                )

                is EtatFilmographie.Pret -> {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Masquer les introuvables",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f),
                        )
                        Switch(checked = ui.masquerIntrouvables, onCheckedChange = { vm.basculerMasquerIntrouvables() })
                    }

                    // Comparé par identifiant et non par place dans la liste affichée : masquer
                    // les introuvables retire des lignes, et un index calculé sur la liste entière
                    // pointerait alors sur la mauvaise ligne. `prochainAVoir` ignore déjà les
                    // introuvables, donc jamais désigné ici.
                    val prochain = prochainAVoir(etat.films)
                    val filmsAffiches = if (ui.masquerIntrouvables) etat.films.filterNot { it.introuvable } else etat.films

                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(filmsAffiches, key = { it.tmdb_id }) { film ->
                            LigneFilm(
                                film = film,
                                aVoir = film.tmdb_id == prochain?.tmdb_id,
                                // « Masquer les introuvables » (geste 3 du peaufinage du 23 septembre
                                // 2026) : les lignes qui restent glissent vers leur nouvelle place au
                                // lieu de sauter, la clé ci-dessus leur donnant leur identité.
                                modifier = Modifier.animateItem(),
                                onClick = {
                                    val entree = film.vu?.let { ui.entrees[it.entry_id] }
                                    // Un film vu ouvre *son* entrée de journal, jamais un
                                    // formulaire de création : la rouvrir en création ajouterait
                                    // un second visionnage au lieu de corriger celui-ci. Tant que
                                    // le journal n'est pas relu (`chargerEntrees`), l'entrée
                                    // manque et la ligne ne fait rien — mieux que d'ouvrir le
                                    // mauvais écran.
                                    when {
                                        entree != null -> onOuvrirVu(entree)
                                        // Jamais de nom de réalisateur ici depuis le brief du
                                        // 21 septembre 2026, « la page réalisateur » (décision 4) :
                                        // cette fiche ne sert plus qu'aux sagas, qui n'en ont pas.
                                        film.vu == null -> onOuvrirAVoir(film.toSearchResult(null))
                                        else -> Unit
                                    }
                                },
                                // Un film déjà vu n'a pas de marque « introuvable » à poser, mais un
                                // film ajouté à la main reste retirable de la saga même vu (brief
                                // « les films de saga ajoutés à la main », 15 septembre 2026).
                                onLongClick = { if (film.vu == null || peutRetirerDeSaga(film)) feuillePour = film },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * « Marquer introuvable » / « Annuler » sur un film non encore marqué, « Le
 * remettre à voir » sur un film qui l'est déjà — jumeau de
 * `SensCritiqueChoiceSheet` (`ui/form/`). Le retour système la referme comme
 * `onDismiss`, sans rien poser : ce n'est pas une décision, juste une sortie.
 *
 * « Retirer de la saga » (brief « les films de saga ajoutés à la main »,
 * 15 septembre 2026) s'ajoute, seul ou à côté des deux boutons ci-dessus —
 * `peutRetirerDeSaga` (`SuivisViewModel.kt`) l'autorise sur un film ajouté à
 * la main, même déjà vu, ce que « marquer introuvable » ne permet pas.
 * `onDismiss` sort toujours en dernier, pour rester atteignable quel que soit
 * le nombre de boutons au-dessus.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntrouvableSheet(
    film: FilmSuivi,
    peutRetirerDeSaga: Boolean,
    onMarquer: () -> Unit,
    onRetirer: () -> Unit,
    onRetirerDeSaga: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Retour haptique (peaufinage du 23 septembre 2026, geste 10) : cocher ou décocher
    // « introuvable » est un `ToggleOn`/`ToggleOff`.
    val haptique = LocalHapticFeedback.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            if (film.vu == null) {
                if (film.introuvable) {
                    TextButton(onClick = {
                        haptique.performHapticFeedback(HapticFeedbackType.ToggleOff)
                        onRetirer()
                    }) { Text("Le remettre à voir") }
                } else {
                    TextButton(onClick = {
                        haptique.performHapticFeedback(HapticFeedbackType.ToggleOn)
                        onMarquer()
                    }) { Text("Marquer introuvable") }
                }
            }
            if (peutRetirerDeSaga) {
                TextButton(onClick = onRetirerDeSaga) { Text("Retirer de la saga") }
            }
            TextButton(onClick = onDismiss) { Text("Annuler") }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LigneFilm(film: FilmSuivi, aVoir: Boolean, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            // Grisée plutôt que cachée : c'est l'interrupteur « Masquer les introuvables », pas
            // cette ligne, qui décide si un film introuvable apparaît. Un appui long reste
            // possible dessus, grisée ou non — « la remettre à voir » ne doit pas être plus dur à
            // atteindre que « la marquer » ne l'a été.
            .alpha(if (film.introuvable) 0.5f else 1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            film.year?.toString() ?: "—",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.End,
            modifier = Modifier.width(36.dp),
        )
        Cover(film.cover_url, film.title, 30.dp, 45.dp)
        Text(film.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        // Petite mention à droite du titre (brief « les films de saga ajoutés à la
        // main », 15 septembre 2026), distincte du bloc note/introuvable/à voir plus
        // loin : un film ajouté peut être vu, introuvable ou à voir tout autant, les
        // deux mentions doivent donc pouvoir cohabiter plutôt que s'exclure.
        if (film.ajoute) {
            Text(
                "ajouté",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        val note = film.vu?.rating
        when {
            note != null -> Text(
                "$note",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.clearAndSetSemantics { contentDescription = "Note $note sur 10" },
            )

            film.introuvable -> Text(
                "introuvable",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            aVoir -> Text(
                "à voir",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}
