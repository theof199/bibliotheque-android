package fr.mediatheque.journal.ui.realisateur

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.FilmDeFilmographie
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.voler
import fr.mediatheque.journal.ui.frise.ouvrirPlex
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * La fiche simple d'un film (décision 2 du brief du 21 septembre 2026, « la page réalisateur ») :
 * ouverte au tap sur une affiche de la filmographie dont `voyage` est nul (`destinationFilm`,
 * `RealisateurEtats.kt`) — nourrie par la ligne de filmographie, rien d'autre à charger. Lit le
 * même `RealisateurViewModel` que la page d'où elle s'est ouverte (`Root.kt`, indexé sur
 * `realisateurTmdbId`) et re-dérive le film par son `tmdb_id` : une correction posée ici (« Je l'ai
 * vu », « Introuvable ») se voit donc sans recharger la fiche elle-même.
 *
 * Une série (`type == "tv"`) n'a ni « Je l'ai vu » ni formulaire (`boutonsFicheFilm`) : elle garde
 * seulement Plex et Sir, avec la ligne « Les séries se suivent dans Suivis ».
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FicheFilmScreen(
    vm: RealisateurViewModel,
    resolveur: RealisateurResolveur,
    filmTmdbId: Int,
    onBack: () -> Unit,
    onOuvrirForm: (SearchResult) -> Unit,
    onOuvrirRealisateur: (Int) -> Unit,
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : même clé que la grille de la
    // filmographie d'où cette fiche s'est ouverte (`Root.kt`).
    volante: AfficheVolante? = null,
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    val contexte = LocalContext.current

    val page = (ui.etat as? EtatPageRealisateur.Pret)?.page
    val film = page?.films?.firstOrNull { it.tmdb_id == filmTmdbId }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        if (page == null || film == null) {
            // La fiche s'ouvre toujours depuis une affiche déjà affichée par `RealisateurScreen` :
            // ce film est donc déjà dans `vm.ui` sauf coup de malchance (retour système pendant un
            // rechargement de la page) — un simple retour plutôt qu'un écran muet.
            if (ui.etat !is EtatPageRealisateur.EnAttente) {
                LaunchedEffect(Unit) { onBack() }
            } else {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
            return@Scaffold
        }

        val etatFilm = etatFilmographie(film)
        val boutons = boutonsFicheFilm(film.type, etatFilm, film.plex_url)

        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { IconeTabler("arrow-left", "Retour") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(film.cover_url, film.title, 96.dp, 144.dp, modifier = Modifier.voler(volante))
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(film.title, style = MaterialTheme.typography.titleLarge)
                    if (film.original_title != null && film.original_title != film.title) {
                        Text(film.original_title, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        film.year?.toString() ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    NomRealisateurTouchable(
                        filmTmdbId = film.tmdb_id,
                        nomConnu = page.name,
                        resolveur = resolveur,
                        onOuvrirRealisateur = onOuvrirRealisateur,
                    )
                }
            }

            film.vu?.let { vu ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Vu", style = MaterialTheme.typography.bodyMedium)
                    vu.rating?.let { note -> Text("$note", style = MaterialTheme.typography.titleMedium) }
                }
            }

            if (film.type == "tv") {
                Text(
                    "Les séries se suivent dans Suivis",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                boutons.forEach { bouton ->
                    when (bouton) {
                        BoutonFicheFilm.VOIR_SUR_LE_PLEX -> OutlinedButton(
                            onClick = { ouvrirPlex(contexte, film.plex_url) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Voir sur le Plex") }
                        BoutonFicheFilm.JE_L_AI_VU -> Button(
                            onClick = { onOuvrirForm(film.versSearchResult(page.name)) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Je l’ai vu") }
                        BoutonFicheFilm.DEMANDER -> OutlinedButton(
                            onClick = { vm.demander(film.tmdb_id) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Demander sur Sir") }
                        BoutonFicheFilm.MARQUER_INTROUVABLE -> TextButton(onClick = { vm.marquerIntrouvable(film.tmdb_id) }) {
                            Text("Introuvable")
                        }
                        BoutonFicheFilm.RETIRER_INTROUVABLE -> TextButton(onClick = { vm.retirerIntrouvable(film.tmdb_id) }) {
                            Text("Le remettre à voir")
                        }
                    }
                }
                if (etatFilm == "demande") {
                    Text("demandé", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** Le même formulaire pré-rempli qu'un à-voir du Plex ou un film du Voyage — le réalisateur est celui de la page d'où la fiche s'est ouverte. */
private fun FilmDeFilmographie.versSearchResult(realisateurNom: String?): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdb_id.toString(),
    type = "movie",
    title = title,
    year = year,
    cover_url = cover_url,
    metadata = SearchMetadata(director = realisateurNom),
    original_title = original_title,
)
