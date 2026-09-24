package fr.mediatheque.journal.ui.frise

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.drawBehind
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.FondHeros
import fr.mediatheque.journal.ui.titreOriginalAffiche
import fr.mediatheque.journal.ui.voler
import fr.mediatheque.journal.ui.realisateur.NomRealisateurTouchable
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.form.BoutonLeFilm
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier

/**
 * La fiche d'un film du Voyage (nouvel écran, `Screen.FicheVoyage`, spec du 19 septembre 2026, §3,
 * brief du 21 septembre 2026 §4) : affiche, titre, réalisateur, durée d'un programme s'il y en a
 * une ; la salle et la raison en évidence ; ma note et mes réactions si je l'ai déjà vu (depuis le
 * journal déjà chargé par `FriseViewModel`) ; « Le film » qui rouvre le carton en pop-in (décision 4
 * du brief du 24 septembre 2026, « le voyage revu ») ; pour un programme, ses bobines, chacune
 * ouvrant le formulaire pré-rempli ; puis les boutons selon l'état.
 *
 * Le podium s'y ajoute le 21 septembre 2026 (décision 3 du brief « le podium ») : « Mettre sur le
 * podium » ouvre le choix d'une marche (`lignesChoixMarche`).
 *
 * Lit `vm` (le même `AnneeViewModel` que l'année d'où elle s'est ouverte, `Root.kt`) plutôt que de
 * recharger quoi que ce soit : `salleId` et `filmId` désignent le film dans son état déjà connu.
 */
@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalLayoutApi::class)
@Composable
fun FicheVoyageScreen(
    annee: Int,
    vm: AnneeViewModel,
    salleId: String,
    filmId: String,
    journalItem: JournalItem?,
    carton: CartonViewModel,
    realisateurResolveur: RealisateurResolveur,
    onBack: () -> Unit,
    onOpenForm: (SearchResult) -> Unit,
    onPodiumChange: () -> Unit,
    /** Le nom du réalisateur est touchable ici aussi (décision 3 du brief du 21 septembre 2026, « la page réalisateur »). */
    onOuvrirRealisateur: (Int) -> Unit,
    /**
     * L'affiche partagée (peaufinage du 23 septembre 2026, geste 8) : même clé que la salle d'où
     * cette fiche s'est ouverte, nulle quand on y arrive directement depuis une filmographie
     * (`Root.kt`, `DestinationFilm.Voyage`) — pas de grille dont partir dans ce cas.
     */
    volante: AfficheVolante? = null,
) {
    val ui by vm.ui.collectAsState()
    val salle = ui.salles.firstOrNull { it.id == salleId }
    val film = salle?.films?.firstOrNull { it.id == filmId }
    val monde = mondeDe(annee)
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    val contexte = LocalContext.current
    var choisirMarche by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = monde.fond,
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        if (film == null) {
            // La fiche s'ouvre le plus souvent depuis une affiche déjà affichée par `AnneeScreen`,
            // salles déjà chargées — mais aussi, depuis le brief du 21 septembre 2026 (« la page
            // réalisateur »), directement depuis une filmographie sans être jamais passé par
            // `Screen.Annee` : le `AnneeViewModel` de cette année-là peut alors être encore à
            // `NON_CONFIGURE`/`EN_PREPARATION`, ses salles pas encore là. On attend dans ce cas
            // (`Root.kt` relit l'année à l'entrée sur cet écran aussi) plutôt que de rebondir tout
            // de suite ; un état terminal (prête, verrouillée, abandon) sans ce film, lui, est bien
            // une absence réelle — retour plutôt qu'un écran muet.
            if (ui.etat == EtatAnnee.EN_PREPARATION || ui.etat == EtatAnnee.NON_CONFIGURE) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                LaunchedEffect(Unit) { onBack() }
            }
            return@Scaffold
        }

        val etat = etatFilmVoyage(film.etat, film.programme?.bobines ?: emptyList())
        val boutons = boutonsFicheVoyage(etat, film.plexUrl)

        Box(Modifier.fillMaxSize()) {
            // Le fond héros (geste 4 du brief du 23 septembre 2026 soir) : l'affiche déjà
            // reçue, derrière l'en-tête — nulle tant qu'elle n'a pas chargé, sans rien réserver.
            FondHeros(film.coverUrl, hauteur = 240.dp, fond = monde.fond)
            Column(
                Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        IconeTabler("arrow-left", "Retour")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // L'affiche encadrée (habillage du 23 septembre 2026, geste 6).
                    Cover(
                        film.coverUrl,
                        film.title,
                        96.dp,
                        144.dp,
                        modifier = Modifier.voler(volante).border(1.5.dp, MaterialTheme.colorScheme.secondary, MaterialTheme.shapes.small),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(film.title, style = MaterialTheme.typography.titleLarge)
                        titreOriginalAffiche(film.title, film.originalTitle)?.let { original ->
                            Text(original, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        NomRealisateurTouchable(
                            filmTmdbId = film.tmdbId,
                            nomConnu = film.realisateur,
                            resolveur = realisateurResolveur,
                            onOuvrirRealisateur = onOuvrirRealisateur,
                        )
                        film.programme?.let { programme ->
                            Text(
                                "${programme.dureeMin} min",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                // La salle en étiquette, pas dans une boîte vide (point 9 de la revue du
                // 24 septembre 2026) : avant elle, `CartoucheSalleEtRaison` posait un cartouche
                // papier jauni plein écran même quand `raison` manquait, un cadre quasiment vide
                // pour une seule ligne (constat de la revue, capture 13).
                Text(
                    "Salle · ${salle.nom}",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.secondary,
                )
                film.raison?.let { raison ->
                    Text(raison, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                if (etat == "vu" && journalItem != null) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        // La note porte son libellé (point 9) : « Ta note · 5 », plus un chiffre nu
                        // dans un cercle sans légende.
                        journalItem.entry.rating?.let { note ->
                            Text(
                                "Ta note · $note",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.secondary,
                            )
                        }
                    }
                    if (journalItem.carnet.reactions.isNotEmpty()) {
                        // Les réactions en pastilles rondes pleines (fond papier 12 %, texte or) —
                        // remplace l'unique ligne d'emojis groupés.
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            journalItem.carnet.reactions.forEach { cle ->
                                Box(
                                    Modifier
                                        .background(PapierJauni.copy(alpha = 0.12f), RoundedCornerShape(50))
                                        .padding(horizontal = 10.dp, vertical = 5.dp),
                                ) {
                                    Text(
                                        Reactions.label(cle),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                    )
                                }
                            }
                        }
                    }
                    // La remarque sur papier, filet gauche or (habillage du 23 septembre 2026, geste 6)
                    // — un filet sur un seul bord, pas un `border()` (qui les dessinerait sur les
                    // quatre), posé en `drawBehind` avant le padding du texte.
                    journalItem.carnet.comment?.takeIf { it.isNotBlank() }?.let { commentaire ->
                        val or = MaterialTheme.colorScheme.secondary
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .background(PapierJauni, RoundedCornerShape(4.dp))
                                .drawBehind { drawRect(or, size = size.copy(width = 3.dp.toPx())) }
                                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 12.dp),
                        ) {
                            Text(
                                commentaire,
                                style = MaterialTheme.typography.bodyMedium.copy(fontStyle = androidx.compose.ui.text.font.FontStyle.Italic),
                                color = TextePapier,
                            )
                        }
                    }
                }

                BoutonLeFilm(carton, titreConnu = film.title)

                film.programme?.let { programme ->
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Les bobines", style = MaterialTheme.typography.titleMedium)
                        programme.bobines.forEach { bobine ->
                            LigneBobine(bobine, onClick = { onOpenForm(bobine.versSearchResult(annee)) })
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    boutons.forEach { bouton ->
                        when (bouton) {
                            BoutonFicheVoyage.VOIR_SUR_LE_PLEX -> OutlinedButton(
                                onClick = { ouvrirPlex(contexte, film.plexUrl) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Voir sur le Plex") }
                            BoutonFicheVoyage.JE_L_AI_VU -> Button(
                                onClick = { onOpenForm(film.versSearchResult(annee)) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Je l’ai vu") }
                            BoutonFicheVoyage.DEMANDER -> OutlinedButton(
                                onClick = { vm.demander(film.tmdbId) },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Demander sur Sir") }
                            BoutonFicheVoyage.MARQUER_INTROUVABLE -> TextButton(onClick = { vm.marquerIntrouvable(film.tmdbId) }) {
                                Text("Introuvable")
                            }
                            BoutonFicheVoyage.RETIRER_INTROUVABLE -> TextButton(onClick = { vm.retirerIntrouvable(film.tmdbId) }) {
                                Text("Le remettre à voir")
                            }
                            BoutonFicheVoyage.METTRE_SUR_LE_PODIUM -> OutlinedButton(
                                onClick = { choisirMarche = true },
                                modifier = Modifier.fillMaxWidth(),
                            ) { Text("Mettre sur le podium") }
                        }
                    }
                    if (etat == "demande") {
                        Text("demandé", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }

        if (choisirMarche) {
            // Un programme se pose par `programme_id` (la ligne de salle), un film ordinaire par
            // `tmdb_id` : jamais les deux (`corpsPodium`, jumeau de `lignesChoixMarche` ci-dessous).
            val candidat = if (film.programme != null) {
                CandidatPodium.Programme(film.id, film.title, film.coverUrl)
            } else {
                CandidatPodium.Film(film.tmdbId, film.title, film.coverUrl, note = journalItem?.entry?.rating)
            }
            ChoisirMarcheSheet(
                lignes = lignesChoixMarche(
                    ui.podium,
                    tmdbId = (candidat as? CandidatPodium.Film)?.tmdbId,
                    programmeId = (candidat as? CandidatPodium.Programme)?.programmeId,
                ),
                onChoisir = { place -> choisirMarche = false; vm.poserPodium(place, candidat, onPodiumChange) },
                onDismiss = { choisirMarche = false },
            )
        }
    }
}

/** La feuille « Mettre sur le podium » (décision 3 du brief du 21 septembre 2026) : trois lignes, une par marche. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoisirMarcheSheet(lignes: List<LigneChoixMarche>, onChoisir: (place: Int) -> Unit, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text("Mettre sur le podium", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            lignes.forEach { ligne ->
                Row(
                    Modifier.fillMaxWidth().clickable { onChoisir(ligne.place) }.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Marche ${ligne.place} · ${ligne.occupantActuel ?: "libre"}",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    AnimatedVisibility(
                        visible = ligne.estCeFilm,
                        enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.6f, animationSpec = tween(150)),
                        exit = fadeOut(tween(150)) + scaleOut(targetScale = 0.6f, animationSpec = tween(150)),
                    ) {
                        IconeTabler("check", "Marche actuelle", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneBobine(bobine: BobineUi, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(bobine.coverUrl, bobine.title, 40.dp, 60.dp)
        Column(Modifier.weight(1f)) {
            Text(bobine.title, style = MaterialTheme.typography.bodyLarge)
            Text("${bobine.dureeMin} min", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        etiquetteEtatFilm(bobine.etat)?.let { etiquette ->
            Text(etiquette, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * `plex://` en premier s'il commence ainsi, sinon le lien web — `lienPlexVoyage` choisit,
 * `Intent.ACTION_VIEW` ouvre. Partagée avec `AnneeScreen.kt` (« Voir sur le Plex » de la carte de
 * soirée, décision 2 du brief du 21 septembre 2026, « la séance ») : même paquet, jamais copiée.
 */
fun ouvrirPlex(contexte: android.content.Context, plexUrl: String?) {
    val lien = lienPlexVoyage(plexUrl) ?: return
    runCatching { contexte.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(lien.uri))) }
}

/**
 * Le même formulaire pré-rempli qu'un « à voir » du Plex (`PlexFilm.toSearchResult()`) — public
 * (point 6 de la revue du 24 septembre 2026) : `Root.kt` le réutilise pour « les films non vus de
 * tes salles du Voyage de l'année en cours », section d'avant-saisie de la recherche.
 */
fun FilmSalleUi.versSearchResult(annee: Int): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdbId.toString(),
    type = "movie",
    title = title,
    year = year ?: annee,
    cover_url = coverUrl,
    metadata = SearchMetadata(director = realisateur),
    original_title = originalTitle,
)

/** Jumeau de `FilmSalleUi.versSearchResult` pour une bobine — sans réalisateur, le contrat ne le sert pas par bobine. */
private fun BobineUi.versSearchResult(annee: Int): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdbId.toString(),
    type = "movie",
    title = title,
    year = annee,
    cover_url = coverUrl,
    metadata = SearchMetadata(director = null),
    original_title = title,
)
