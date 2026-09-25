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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.AfficheVolante
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.PlexBadge
import fr.mediatheque.journal.ui.fiche.EnTeteFiche
import fr.mediatheque.journal.ui.fiche.FORME_BOUTON_FICHE
import fr.mediatheque.journal.ui.fiche.PucesReactions
import fr.mediatheque.journal.ui.fiche.anneeEtDuree
import fr.mediatheque.journal.ui.fiche.formatDuree
import fr.mediatheque.journal.ui.fiche.tailleBoutonFiche
import fr.mediatheque.journal.ui.form.BoutonLeFilm
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.lisereOr
import fr.mediatheque.journal.ui.realisateur.NomRealisateurTouchable
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.titreOriginalAffiche

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
 * Reprise sur l'en-tête commun le 25 septembre 2026 (« la fiche · trois visages », reprise
 * validée) : avant, un fond héros flouté derrière une affiche 96 × 144, « Ta note · 5 » en texte et
 * le commentaire sur papier jauni ; désormais l'affiche en héros d'`EnTeteFiche` avec « Salle ·
 * nom » en étiquette, la raison sous un filet de l'accent, les réactions en puces, le programme et
 * ses bobines, puis la pile de boutons où « Corriger » rejoint un film vu. Le commentaire n'apparaît
 * plus, comme sur les deux autres fiches.
 *
 * Lit `vm` (le même `AnneeViewModel` que l'année d'où elle s'est ouverte, `Root.kt`) plutôt que de
 * recharger quoi que ce soit : `salleId` et `filmId` désignent le film dans son état déjà connu.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
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
    /** « Corriger » d'un film vu dont l'entrée est connue (`journalItem`) : le formulaire de correction. */
    onCorriger: () -> Unit,
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
        val boutons = boutonsFicheVoyage(etat, film.plexUrl, entreeConnue = journalItem != null)
        // Ma note et mes réactions seulement sur un film vu : un programme n'est « vu » qu'une fois
        // toutes ses bobines vues (`etatFilmVoyage`), même si l'entrée du long existe déjà.
        val entreeVue = journalItem?.takeIf { etat == "vu" }

        // Pas de marge du haut : l'affiche passe sous la barre d'état, le disque du retour s'y range
        // lui-même (`statusBarsPadding`, `EnTeteFiche`) — jumeau de `FicheEntreeScreen`.
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = padding.calculateBottomPadding()),
        ) {
            EnTeteFiche(
                affiche = film.coverUrl,
                titre = film.title,
                titreOriginal = titreOriginalAffiche(film.title, film.originalTitle),
                note = entreeVue?.entry?.rating,
                realisateur = {
                    NomRealisateurTouchable(
                        filmTmdbId = film.tmdbId,
                        nomConnu = film.realisateur,
                        resolveur = realisateurResolveur,
                        onOuvrirRealisateur = onOuvrirRealisateur,
                        color = MaterialTheme.colorScheme.onSurface,
                        chevron = true,
                    )
                },
                anneeEtDuree = anneeEtDuree(film.year, film.programme?.dureeMin),
                etiquette = "Salle · ${salle.nom}",
                couleurEtiquette = monde.accent,
                fond = monde.fond,
                onBack = onBack,
                volante = volante,
            )
            Column(
                Modifier.fillMaxWidth().padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                film.raison?.let { RaisonSalle(it, monde.accent) }
                entreeVue?.let { PucesReactions(it.carnet.reactions) }
                film.programme?.let { programme ->
                    ProgrammeBobines(programme, monde, onOuvrirBobine = { bobine -> onOpenForm(bobine.versSearchResult(annee)) })
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    boutons.forEach { bouton ->
                        when (bouton) {
                            BoutonFicheVoyage.CORRIGER -> Button(
                                onClick = onCorriger,
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Corriger") }
                            BoutonFicheVoyage.VOIR_SUR_LE_PLEX -> OutlinedButton(
                                onClick = { ouvrirPlex(contexte, film.plexUrl) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Voir sur le Plex") }
                            BoutonFicheVoyage.JE_L_AI_VU -> Button(
                                onClick = { onOpenForm(film.versSearchResult(annee)) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Je l’ai vu") }
                            BoutonFicheVoyage.DEMANDER -> OutlinedButton(
                                onClick = { vm.demander(film.tmdbId) },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Demander sur Sir") }
                            // En texte gris comme sur la fiche simple (« Introuvable » texte gris,
                            // brief de la fiche · trois visages) : le corail reste au seul bouton plein.
                            BoutonFicheVoyage.MARQUER_INTROUVABLE -> TextButton(
                                onClick = { vm.marquerIntrouvable(film.tmdbId) },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
                            ) { Text("Introuvable") }
                            BoutonFicheVoyage.RETIRER_INTROUVABLE -> TextButton(onClick = { vm.retirerIntrouvable(film.tmdbId) }) {
                                Text("Le remettre à voir")
                            }
                            BoutonFicheVoyage.METTRE_SUR_LE_PODIUM -> OutlinedButton(
                                onClick = { choisirMarche = true },
                                modifier = Modifier.tailleBoutonFiche(),
                                shape = FORME_BOUTON_FICHE,
                            ) { Text("Mettre sur le podium") }
                        }
                    }
                    if (etat == "demande") {
                        Text("demandé", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    // « Le film » en dernier, filet or : le même bas de pile sur les trois fiches.
                    BoutonLeFilm(
                        carton,
                        titreConnu = film.title,
                        modifier = Modifier.tailleBoutonFiche(),
                        bord = MaterialTheme.colorScheme.secondary,
                        shape = FORME_BOUTON_FICHE,
                        sousTitre = listOfNotNull(film.year?.toString(), "Salle « ${salle.nom} »").joinToString(" · "),
                    )
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

/**
 * Pourquoi ce film est dans cette salle, sous un filet vertical de 1 dp dans l'accent du monde :
 * la raison se lit comme une note en marge de l'étiquette « Salle · nom » juste au-dessus, plutôt
 * que comme un paragraphe de plus. `IntrinsicSize.Min` donne au filet la hauteur du texte.
 */
@Composable
private fun RaisonSalle(raison: String, accent: Color) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min)) {
        Box(Modifier.width(1.dp).fillMaxHeight().background(accent))
        Text(
            raison,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp),
        )
    }
}

/**
 * « PROGRAMME · 2 h 03 » puis une ligne par bobine : la durée totale en étiquette de l'accent, comme
 * « Salle · nom » dans l'en-tête, à la place du titre « Les bobines » d'avant la reprise.
 */
@Composable
private fun ProgrammeBobines(programme: ProgrammeUi, monde: Monde, onOuvrirBobine: (BobineUi) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            "Programme · ${formatDuree(programme.dureeMin)}".uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.16.em),
            color = monde.accent,
        )
        programme.bobines.forEach { bobine ->
            LigneBobine(bobine, monde.fond, onClick = { onOuvrirBobine(bobine) })
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

/**
 * Une bobine : sa durée et, s'il y en a un, son état en toutes lettres (« 12 min · sur ton plex »),
 * puis à droite une pastille or cochée si elle est vue, ou le badge Plex réduit à la même taille
 * si elle attend sur le Plex. Le tap ouvre toujours le formulaire pré-rempli.
 */
@Composable
private fun LigneBobine(bobine: BobineUi, fond: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(bobine.coverUrl, bobine.title, 40.dp, 60.dp, modifier = Modifier.lisereOr())
        Column(Modifier.weight(1f)) {
            Text(bobine.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                listOfNotNull(formatDuree(bobine.dureeMin), etiquetteEtatFilm(bobine.etat)?.lowercase()).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RepereBobine(bobine.etat, fond)
    }
}

/**
 * Le repère au bout d'une bobine, dans une case de 30 dp — la taille de `PlexBadge` avec sa marge —
 * pour que pastille et badge tombent sur le même axe. Rien du tout pour les autres états : leur
 * mot suffit, dans la ligne du dessous.
 */
@Composable
private fun RepereBobine(etat: String, fond: Color) {
    if (etat != "vu" && etat != "sur_le_plex") return
    Box(Modifier.size(30.dp), contentAlignment = Alignment.Center) {
        if (etat == "vu") {
            Box(
                Modifier.size(18.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                IconeTabler("check", "Vue", tint = fond, modifier = Modifier.size(12.dp))
            }
        } else {
            // Le disque de `PlexBadge` fait 22 dp : ramené à 18, celui de la pastille « vue ».
            PlexBadge(Modifier.scale(18f / 22f))
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
