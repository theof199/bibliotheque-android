package fr.mediatheque.journal.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortieCinemaFilm
import fr.mediatheque.journal.api.dto.SortieFilm
import fr.mediatheque.journal.api.dto.SortieSemaine
import fr.mediatheque.journal.api.dto.SortiesEnCours
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.JournalRow
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * « Au ciné » (brief du 14 septembre 2026) : rien que ça — mes séances et les
 * sorties en salle, pas de recommandation. Un seul `LazyColumn`, pour que le
 * défilement de « Tes séances » (paginée, jumelle de « Mes films ») et les
 * deux grilles de sorties (petites, non paginées : la fenêtre TMDB borne leur
 * taille) partagent une seule barre de défilement.
 */
@Composable
fun AuCineScreen(
    vm: AuCineViewModel,
    onOpenSortie: (SearchResult) -> Unit,
    onOpenSeance: (JournalItem) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val liste = rememberLazyListState()

    // Charger la suite de « Tes séances » quand la dernière ligne visible approche de la fin —
    // sur `totalItemsCount`, pas sur `ui.seances.size` (jumeau de `FilmsScreen`) : les grilles et
    // les en-têtes précèdent la liste dans ce `LazyColumn`, la taille de la liste seule ne dit
    // donc pas où est la fin du défilement.
    LaunchedEffect(liste) {
        snapshotFlow {
            val info = liste.layoutInfo
            (info.visibleItemsInfo.lastOrNull()?.index ?: -1) to info.totalItemsCount
        }
            .distinctUntilChanged()
            .collect { (index, total) -> if (index >= 0 && index >= total - 5) vm.loadMoreSeances() }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        BoxWithConstraints(Modifier.fillMaxSize().padding(padding).padding(16.dp)) {
            val ecart = 8.dp
            val largeurJaquette = (maxWidth - ecart * 2) / 3
            val hauteurJaquette = largeurJaquette * 1.5f

            LazyColumn(state = liste, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                item {
                    val n = ui.seancesCetteAnnee()
                    Text("$n séance${if (n > 1) "s" else ""} cette année", style = MaterialTheme.typography.titleLarge)
                }

                item { Text("Sorti cette semaine dans mes cinémas", style = MaterialTheme.typography.titleMedium) }
                ui.sorties?.en_cours?.miseAJourAffichee()?.let { texte ->
                    item {
                        Text(
                            texte,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                item {
                    SortiesEnCoursSection(
                        enCours = ui.sorties?.en_cours,
                        loading = ui.sortiesLoading,
                        error = ui.sortiesError,
                        onRetry = vm::retrySorties,
                        dejaVu = ui::dejaDansLeJournal,
                        onOpen = { film -> onOpenSortie(film.toSearchResult()) },
                        largeur = largeurJaquette,
                        hauteur = hauteurJaquette,
                        ecart = ecart,
                    )
                }

                item { Text("La semaine prochaine", style = MaterialTheme.typography.titleMedium) }
                item {
                    SortiesSection(
                        semaine = ui.sorties?.prochaine,
                        loading = ui.sortiesLoading,
                        error = null, // l'erreur des sorties n'est montrée qu'une fois, plus haut.
                        onRetry = vm::retrySorties,
                        dejaVu = ui::dejaDansLeJournal,
                        onOpen = { film -> onOpenSortie(film.toSearchResult()) },
                        largeur = largeurJaquette,
                        hauteur = hauteurJaquette,
                        ecart = ecart,
                    )
                }

                item { Text("Tes séances", style = MaterialTheme.typography.titleMedium) }
                ui.seancesError?.let {
                    item { ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = vm::loadMoreSeances) }
                }
                if (ui.seances.isEmpty() && ui.seancesEndReached) {
                    item {
                        Text(
                            "Aucune séance pour l’instant.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(ui.seances, key = { it.entry.id }) { item -> JournalRow(item, onClick = { onOpenSeance(item) }) }
                }
                if (ui.seancesLoading) {
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

/** Une grille de sorties, avec son propre chargement et sa propre erreur — « Cette semaine » ou « La semaine prochaine ». */
@Composable
private fun SortiesSection(
    semaine: SortieSemaine?,
    loading: Boolean,
    error: ApiError?,
    onRetry: () -> Unit,
    dejaVu: (SortieFilm) -> Boolean,
    onOpen: (SortieFilm) -> Unit,
    largeur: Dp,
    hauteur: Dp,
    ecart: Dp,
) {
    error?.let {
        ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = onRetry)
        return
    }
    val films = semaine?.films
    when {
        // Le bloc à spinner réserve la hauteur d'une rangée d'affiches (peaufinage du 23 septembre
        // 2026, geste 6) : la grille qui arrive ne décale plus « Tes séances » d'un coup.
        films == null && loading -> Box(
            Modifier.fillMaxWidth().heightIn(min = hauteur).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
        }
        films.isNullOrEmpty() -> Text(
            "Rien cette semaine.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(ecart)) {
            films.chunked(3).forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    rangee.forEach { film ->
                        Box(Modifier.clickable { onOpen(film) }) {
                            Cover(film.cover_url, film.title, largeur, hauteur)
                            if (dejaVu(film)) {
                                Box(
                                    Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                                        .padding(4.dp)
                                        .clearAndSetSemantics { contentDescription = "Déjà dans ton journal" },
                                ) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * La grille « à l'affiche dans mes cinémas » (brief du 15 septembre 2026) —
 * mêmes tuiles que `SortiesSection`, plus un sous-titre de cinémas de 11 sp
 * sous chacune ; une tuile sans `tmdb_id` n'est ni touchable ni cochable.
 */
@Composable
private fun SortiesEnCoursSection(
    enCours: SortiesEnCours?,
    loading: Boolean,
    error: ApiError?,
    onRetry: () -> Unit,
    dejaVu: (SortieCinemaFilm) -> Boolean,
    onOpen: (SortieCinemaFilm) -> Unit,
    largeur: Dp,
    hauteur: Dp,
    ecart: Dp,
) {
    error?.let {
        ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = onRetry)
        return
    }

    val message = enCours?.messageAuCine()
    when {
        // Jumeau de `SortiesSection` ci-dessus (geste 6) : même réserve de hauteur.
        enCours == null && loading -> Box(
            Modifier.fillMaxWidth().heightIn(min = hauteur).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
        }
        // Ni chargement ni réponse encore arrivée (premier rendu, avant `refresh()`) : rien à montrer.
        enCours == null -> Unit
        message != null -> Text(
            message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Column(verticalArrangement = Arrangement.spacedBy(ecart)) {
            enCours.films.chunked(3).forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    rangee.forEach { film ->
                        val ouvrable = film.estOuvrable()
                        Column(Modifier.width(largeur).let { if (ouvrable) it.clickable { onOpen(film) } else it }) {
                            Box {
                                Cover(film.cover_url, film.title, largeur, hauteur)
                                if (ouvrable && dejaVu(film)) {
                                    Box(
                                        Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(4.dp)
                                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                                            .padding(4.dp)
                                            .clearAndSetSemantics { contentDescription = "Déjà dans ton journal" },
                                    ) {
                                        Icon(
                                            Icons.Filled.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.onPrimary,
                                            modifier = Modifier.size(16.dp),
                                        )
                                    }
                                }
                            }
                            Text(
                                film.sousTitreCinemas(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
