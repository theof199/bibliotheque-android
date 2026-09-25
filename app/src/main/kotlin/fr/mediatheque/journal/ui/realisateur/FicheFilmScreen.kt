package fr.mediatheque.journal.ui.realisateur

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.fiche.EnTeteFiche
import fr.mediatheque.journal.ui.fiche.FORME_BOUTON_FICHE
import fr.mediatheque.journal.ui.fiche.PucesReactions
import fr.mediatheque.journal.ui.fiche.anneeEtDuree
import fr.mediatheque.journal.ui.fiche.etiquetteDecennie
import fr.mediatheque.journal.ui.fiche.tailleBoutonFiche
import fr.mediatheque.journal.ui.form.BoutonLeFilm
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.frise.mondeDe
import fr.mediatheque.journal.ui.frise.ouvrirPlex
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.titreOriginalAffiche

/**
 * La fiche simple d'un film (décision 2 du brief du 21 septembre 2026, « la page réalisateur ») :
 * ouverte au tap sur une affiche de la filmographie dont `voyage` est nul (`destinationFilm`,
 * `RealisateurEtats.kt`) — nourrie par la ligne de filmographie, rien d'autre à charger. Lit le
 * même `RealisateurViewModel` que la page d'où elle s'est ouverte (`Root.kt`, indexé sur
 * `realisateurTmdbId`) et re-dérive le film par son `tmdb_id` : une correction posée ici (« Je l'ai
 * vu », « Introuvable ») se voit donc sans recharger la fiche elle-même.
 *
 * Reprise sur l'en-tête commun le 25 septembre 2026 (« la fiche · trois visages », reprise
 * validée) : avant, un fond héros tiré de `backdrop_url` derrière une affiche 96 × 144, « Vu » et
 * la note nue, et « Les séries se suivent dans Suivis » sous une série ; désormais l'affiche en
 * héros d'`EnTeteFiche` sur le fond du monde de la décennie du film, son étiquette « Années 1990 ·
 * Le blockbuster », la note dans « TA NOTE », les réactions de l'entrée quand on la connaît, et
 * « Corriger » sur un film vu.
 *
 * `entrees` : le journal complet (`SuivisUi.entrees`, par identifiant d'entrée). La ligne de
 * filmographie ne porte que `vu.entry_id`, la note et la date : l'entrée entière — réactions, et
 * de quoi ouvrir la correction — se retrouve par cet identifiant. Tant qu'elle n'y est pas (journal
 * pas encore relu, ou relecture en panne), la fiche reste sans réactions ni « Corriger ».
 *
 * Une série (`type == "tv"`) n'a ni « Je l'ai vu » ni formulaire (`boutonsFicheFilm`) : elle garde
 * seulement Plex et Sir.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FicheFilmScreen(
    vm: RealisateurViewModel,
    resolveur: RealisateurResolveur,
    filmTmdbId: Int,
    entrees: Map<String, JournalItem>,
    onBack: () -> Unit,
    onOuvrirForm: (SearchResult) -> Unit,
    onOuvrirRealisateur: (Int) -> Unit,
    /** « Corriger » d'un film vu dont l'entrée est connue : le formulaire de correction de cette entrée. */
    onCorriger: (JournalItem) -> Unit,
    // L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : même clé que la grille de la
    // filmographie d'où cette fiche s'est ouverte (`Root.kt`).
    volante: AfficheVolante? = null,
    /** « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») : rouvre le carton en pop-in. */
    carton: CartonViewModel? = null,
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    val contexte = LocalContext.current

    val page = (ui.etat as? EtatPageRealisateur.Pret)?.page
    val film = page?.films?.firstOrNull { it.tmdb_id == filmTmdbId }
    // Le fond du monde de la décennie du film, calculé avant le `Scaffold` qui le porte ; celui de
    // l'appli sans année (ou le temps que la page arrive).
    val fond = film?.year?.let { mondeDe(it).fond } ?: MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = fond,
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
        val entree = film.vu?.entry_id?.let { entrees[it] }
        val boutons = boutonsFicheFilm(film.type, etatFilm, film.plex_url, entreeConnue = entree != null)

        // Pas de marge du haut : l'affiche passe sous la barre d'état, le disque du retour s'y range
        // lui-même (`statusBarsPadding`, `EnTeteFiche`) — jumeau de `FicheEntreeScreen`.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            EnTeteFiche(
                affiche = film.cover_url,
                titre = film.title,
                titreOriginal = titreOriginalAffiche(film.title, film.original_title),
                note = film.vu?.rating,
                realisateur = {
                    NomRealisateurTouchable(
                        filmTmdbId = film.tmdb_id,
                        nomConnu = page.name,
                        resolveur = resolveur,
                        onOuvrirRealisateur = onOuvrirRealisateur,
                        color = MaterialTheme.colorScheme.onSurface,
                        chevron = true,
                    )
                },
                // Ni durée ni synopsis dans `GET /me/realisateurs/{tmdbId}/page` : l'année seule.
                anneeEtDuree = anneeEtDuree(film.year, null),
                etiquette = etiquetteDecennie(film.year),
                couleurEtiquette = film.year?.let { mondeDe(it).accent } ?: MaterialTheme.colorScheme.secondary,
                fond = fond,
                onBack = onBack,
                volante = volante,
            )
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                PucesReactions(entree?.carnet?.reactions ?: emptyList())
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    boutons.forEach { bouton ->
                        when (bouton) {
                            BoutonFicheFilm.CORRIGER -> Button(
                                // `CORRIGER` n'est dans la pile que si l'entrée est connue.
                                onClick = { entree?.let(onCorriger) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Corriger") }
                            BoutonFicheFilm.VOIR_SUR_LE_PLEX -> OutlinedButton(
                                onClick = { ouvrirPlex(contexte, film.plex_url) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Voir sur le Plex") }
                            BoutonFicheFilm.JE_L_AI_VU -> Button(
                                onClick = { onOuvrirForm(film.versSearchResult(page.name)) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Je l’ai vu") }
                            BoutonFicheFilm.DEMANDER -> OutlinedButton(
                                onClick = { vm.demander(film.tmdb_id) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Demander sur Sir") }
                            // En bouton texte gris, pas en corail (point 10) : avant cette revue,
                            // `TextButton` gardait la couleur par défaut de Material
                            // (`colorScheme.primary`), le corail réservé ailleurs à un choix ou un
                            // déclenchement — jamais à ce geste, plus proche d'un aveu que d'une
                            // action positive.
                            BoutonFicheFilm.MARQUER_INTROUVABLE -> TextButton(
                                onClick = { vm.marquerIntrouvable(film.tmdb_id) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            ) { Text("Introuvable") }
                            BoutonFicheFilm.RETIRER_INTROUVABLE -> TextButton(onClick = { vm.retirerIntrouvable(film.tmdb_id) }) {
                                Text("Le remettre à voir")
                            }
                        }
                    }
                    if (etatFilm == "demande") {
                        Text("demandé", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // « Le film » (décision 4 du brief du 24 septembre 2026, « le voyage revu ») :
                    // rouvre le carton en pop-in — dernier de la pile, filet or, sur les trois fiches.
                    carton?.let {
                        BoutonLeFilm(
                            it,
                            titreConnu = film.title,
                            modifier = Modifier.tailleBoutonFiche(),
                            bord = MaterialTheme.colorScheme.secondary,
                            shape = FORME_BOUTON_FICHE,
                        )
                    }
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
