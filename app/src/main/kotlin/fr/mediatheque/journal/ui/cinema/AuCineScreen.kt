package fr.mediatheque.journal.ui.cinema

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortieCinemaFilm
import fr.mediatheque.journal.api.dto.SortieFilm
import fr.mediatheque.journal.api.dto.SortieSemaine
import fr.mediatheque.journal.api.dto.SortiesEnCours
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.SceauOr
import fr.mediatheque.journal.ui.films.LigneFilm
import fr.mediatheque.journal.ui.lisereOr
import fr.mediatheque.journal.ui.theme.FiletOr
import fr.mediatheque.journal.ui.theme.IconeTabler
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * « Au ciné » (brief du 14 septembre 2026) : rien que ça — mes séances et les sorties en salle,
 * pas de recommandation.
 *
 * « Le guichet » (brief de Léon et décisions du propriétaire du 25 septembre 2026) : l'en-tête et
 * son filet or, comme Mes films et Suivis ; des titres de section en petites capitales ; des
 * affiches liserées d'or, qui gardent la coche corail « déjà au journal » et gagnent un sceau or
 * quand leur réalisateur ou leur saga est suivi (`ReperesSuivis`, sans appel réseau) ; « Tes
 * séances » en `LigneFilm`, la ligne de Mes films, glissement « Corriger » / « Supprimer » compris.
 *
 * Un seul `LazyColumn`, pour que le défilement de « Tes séances » (paginée, jumelle de « Mes
 * films ») et les deux grilles de sorties (petites, non paginées : la fenêtre TMDB borne leur
 * taille) partagent une seule barre de défilement. Il n'a pas de marge horizontale : les lignes
 * `LigneFilm` portent la leur (16 dp) et leur trait de séparation, les autres éléments prennent
 * leurs 16 dp un par un.
 */
@Composable
fun AuCineScreen(
    vm: AuCineViewModel,
    /** Ce que l'appli sait déjà des Suivis : le sceau or des tuiles (`AuCineEtats.kt`). */
    reperes: ReperesSuivis,
    onOpenSortie: (SearchResult) -> Unit,
    onOpenSeance: (JournalItem) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val liste = rememberLazyListState()

    // Une seule ligne ouverte à la fois, comme dans Mes films : l'identifiant de son visionnage,
    // `null` si aucune.
    var ligneOuverte by remember { mutableStateOf<String?>(null) }
    var aSupprimer by remember { mutableStateOf<JournalItem?>(null) }

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
    // Défiler referme la ligne ouverte (« un tap ailleurs referme », au sens large), comme Mes films.
    LaunchedEffect(liste) {
        snapshotFlow { liste.isScrollInProgress }.collect { if (it) ligneOuverte = null }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // L'en-tête n'a que le titre (décision du propriétaire du 25 septembre 2026 : le lien
            // « Mes cinémas » est abandonné), à la hauteur de celui de Mes films.
            Row(
                Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val n = ui.seancesCetteAnnee()
                Text("$n séance${if (n > 1) "s" else ""} cette année", style = MaterialTheme.typography.titleLarge)
            }
            FiletOr()

            BoxWithConstraints(Modifier.fillMaxSize()) {
                val ecart = 8.dp
                val largeurJaquette = (maxWidth - 32.dp - ecart * 2) / 3
                val hauteurJaquette = largeurJaquette * 1.5f
                // 16 dp entre deux éléments, comme l'ancien `spacedBy(16.dp)` — sauf entre deux
                // lignes de « Tes séances », qui se suivent sans jour comme dans Mes films (leur
                // propre marge verticale et leur trait les séparent déjà).
                val marge = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp)

                LazyColumn(state = liste, contentPadding = PaddingValues(bottom = 16.dp)) {
                    item {
                        Row(marge.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            TitreSection("Sorti cette semaine dans mes cinémas", Modifier.weight(1f))
                            ui.sorties?.en_cours?.miseAJourAffichee()?.let { texte ->
                                Text(
                                    texte,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp),
                                )
                            }
                        }
                    }
                    item {
                        SortiesEnCoursSection(
                            enCours = ui.sorties?.en_cours,
                            loading = ui.sortiesLoading,
                            error = ui.sortiesError,
                            onRetry = vm::retrySorties,
                            dejaVu = ui::dejaDansLeJournal,
                            marque = { reperes.marque(it) },
                            onOpen = { film -> onOpenSortie(film.toSearchResult()) },
                            largeur = largeurJaquette,
                            hauteur = hauteurJaquette,
                            ecart = ecart,
                            modifier = marge,
                        )
                    }

                    item { TitreSection("La semaine prochaine", marge) }
                    item {
                        SortiesSection(
                            semaine = ui.sorties?.prochaine,
                            loading = ui.sortiesLoading,
                            error = null, // l'erreur des sorties n'est montrée qu'une fois, plus haut.
                            onRetry = vm::retrySorties,
                            dejaVu = ui::dejaDansLeJournal,
                            marque = { reperes.marque(it) },
                            onOpen = { film -> onOpenSortie(film.toSearchResult()) },
                            largeur = largeurJaquette,
                            hauteur = hauteurJaquette,
                            ecart = ecart,
                            modifier = marge,
                        )
                    }

                    item { TitreSection("Tes séances", marge) }
                    ui.seancesError?.let {
                        item { ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = vm::loadMoreSeances, modifier = marge) }
                    }
                    if (ui.seances.isEmpty() && ui.seancesEndReached) {
                        item {
                            Text(
                                "Aucune séance pour l’instant.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = marge,
                            )
                        }
                    } else {
                        items(ui.seances, key = { it.entry.id }) { item ->
                            val id = item.entry.id
                            // La ligne de Mes films, pilotée de la même façon (décision du
                            // propriétaire du 25 septembre 2026 : le même glissement).
                            LigneFilm(
                                item,
                                ouverte = ligneOuverte == id,
                                onOuvrir = { ligneOuverte = id },
                                onFermer = { if (ligneOuverte == id) ligneOuverte = null },
                                // Une autre ligne ouverte : ce tap la referme, il n'ouvre pas la séance.
                                onClick = { if (ligneOuverte != null) ligneOuverte = null else onOpenSeance(item) },
                                onCorriger = { onOpenSeance(item) },
                                onSupprimer = { aSupprimer = item },
                                // Pas pendant un chargement : une page 1 en vol pourrait ramener la
                                // ligne tout juste supprimée. Ni pendant une autre suppression.
                                supprimerActif = !ui.seancesLoading && !ui.suppressionEnCours,
                            )
                        }
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

    // Le même dialogue que Mes films (`FilmsScreen`) et le formulaire, mêmes mots.
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

/** Un titre de section en petites capitales espacées, le style de « RÉTROSPECTIVES COMPLÈTES » (Suivis). */
@Composable
private fun TitreSection(texte: String, modifier: Modifier = Modifier) {
    Text(
        texte.uppercase(),
        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14.em),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Une grille de sorties, avec son propre chargement et sa propre erreur — « La semaine prochaine ». */
@Composable
private fun SortiesSection(
    semaine: SortieSemaine?,
    loading: Boolean,
    error: ApiError?,
    onRetry: () -> Unit,
    dejaVu: (SortieFilm) -> Boolean,
    marque: (SortieFilm) -> MarqueSuivi?,
    onOpen: (SortieFilm) -> Unit,
    largeur: Dp,
    hauteur: Dp,
    ecart: Dp,
    modifier: Modifier = Modifier,
) {
    error?.let {
        ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = onRetry, modifier = modifier)
        return
    }
    val films = semaine?.films
    when {
        // Le bloc à spinner réserve la hauteur d'une rangée d'affiches (peaufinage du 23 septembre
        // 2026, geste 6) : la grille qui arrive ne décale plus « Tes séances » d'un coup.
        films == null && loading -> Box(
            modifier.fillMaxWidth().heightIn(min = hauteur).padding(vertical = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(Modifier.size(40.dp), color = MaterialTheme.colorScheme.primary)
        }
        films.isNullOrEmpty() -> Text(
            "Rien cette semaine.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = modifier,
        )
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ecart)) {
            films.chunked(3).forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    rangee.forEach { film ->
                        // `SortieFilm` ne porte pas d'horaire (vérifié dans `contract/openapi.json`) :
                        // l'affiche et le titre, rien de plus.
                        TuileSortie(
                            coverUrl = film.cover_url,
                            title = film.title,
                            dejaVu = dejaVu(film),
                            marque = marque(film),
                            largeur = largeur,
                            hauteur = hauteur,
                            sousTitre = null,
                            onOpen = { onOpen(film) },
                        )
                    }
                }
            }
        }
    }
}

/**
 * La grille « à l'affiche dans mes cinémas » (brief du 15 septembre 2026) — mêmes tuiles que
 * `SortiesSection`, plus un sous-titre de cinémas sous chacune quand ils diffèrent ; une tuile
 * sans `tmdb_id` n'est ni touchable, ni cochable, ni scellée.
 */
@Composable
private fun SortiesEnCoursSection(
    enCours: SortiesEnCours?,
    loading: Boolean,
    error: ApiError?,
    onRetry: () -> Unit,
    dejaVu: (SortieCinemaFilm) -> Boolean,
    marque: (SortieCinemaFilm) -> MarqueSuivi?,
    onOpen: (SortieCinemaFilm) -> Unit,
    largeur: Dp,
    hauteur: Dp,
    ecart: Dp,
    modifier: Modifier = Modifier,
) {
    error?.let {
        ErrorBlock(it.message ?: "", retryable = it.retryable, onRetry = onRetry, modifier = modifier)
        return
    }

    val message = enCours?.messageAuCine()
    when {
        // Jumeau de `SortiesSection` ci-dessus (geste 6) : même réserve de hauteur.
        enCours == null && loading -> Box(
            modifier.fillMaxWidth().heightIn(min = hauteur).padding(vertical = 8.dp),
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
            modifier = modifier,
        )
        else -> Column(modifier, verticalArrangement = Arrangement.spacedBy(ecart)) {
            // Le cinéma en titre de section, une seule fois (point 13 de la revue du 24 septembre
            // 2026), en `titleMedium` depuis le guichet : quand toutes les tuiles partagent le même
            // (`cinemaUniqueEnCours`, fonction pure testée), il n'a plus à se répéter sous chaque
            // affiche — sinon (plusieurs cinémas suivis), `sousTitreCinemas()` garde son rôle,
            // tuile par tuile, seul moyen de rester exact.
            val cinemaUnique = cinemaUniqueEnCours(enCours.films)
            cinemaUnique?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            }
            enCours.films.chunked(3).forEach { rangee ->
                Row(horizontalArrangement = Arrangement.spacedBy(ecart)) {
                    rangee.forEach { film ->
                        val ouvrable = film.estOuvrable()
                        TuileSortie(
                            coverUrl = film.cover_url,
                            title = film.title,
                            dejaVu = ouvrable && dejaVu(film),
                            marque = if (ouvrable) marque(film) else null,
                            largeur = largeur,
                            hauteur = hauteur,
                            sousTitre = if (cinemaUnique == null) film.sousTitreCinemas() else null,
                            onOpen = if (ouvrable) ({ onOpen(film) }) else null,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Une tuile de sortie, commune aux deux grilles (« le guichet », 25 septembre 2026 — chacune avait
 * avant elle sa copie) : l'affiche liserée d'or, la coche corail « déjà au journal » en bas à
 * droite, le sceau or d'un suivi en haut à droite, le titre dessous (point 13 de la revue du
 * 24 septembre 2026), puis le sous-titre de cinémas s'il y en a un. `onOpen` nul : tuile non
 * touchable.
 */
@Composable
private fun TuileSortie(
    coverUrl: String?,
    title: String,
    dejaVu: Boolean,
    marque: MarqueSuivi?,
    largeur: Dp,
    hauteur: Dp,
    sousTitre: String?,
    onOpen: (() -> Unit)?,
) {
    Column(Modifier.width(largeur).let { if (onOpen != null) it.clickable(onClick = onOpen) else it }) {
        Box {
            Cover(coverUrl, title, largeur, hauteur, modifier = Modifier.lisereOr())
            if (dejaVu) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                        .padding(4.dp)
                        .clearAndSetSemantics { contentDescription = "Déjà dans ton journal" },
                ) {
                    IconeTabler("check", null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
            }
            // Le sceau or d'un suivi (« le guichet », 25 septembre 2026) : le réalisateur
            // l'emporte sur la saga (`ReperesSuivis.marque`). Statique, une grille ne rebondit pas.
            marque?.let {
                val (icone, description) = when (it) {
                    MarqueSuivi.REALISATEUR -> "user" to "Réalisateur suivi"
                    MarqueSuivi.SAGA -> "movie" to "Saga suivie"
                }
                SceauOr(
                    20.dp,
                    anime = false,
                    icone = icone,
                    description = description,
                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
                    fond = MaterialTheme.colorScheme.background,
                )
            }
        }
        Text(
            title,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 2.dp),
        )
        sousTitre?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
