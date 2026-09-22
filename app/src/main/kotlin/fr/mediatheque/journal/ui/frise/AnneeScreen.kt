package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.formatDateTime
import fr.mediatheque.journal.ui.realisateur.NomRealisateurTouchable
import fr.mediatheque.journal.ui.realisateur.RealisateurResolveur
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * La fiche d'une année du Voyage (brief du 21 septembre 2026, « l'année en étages », « le podium »,
 * puis « le ticket », spec du 19 septembre 2026, §2-§3, §5) : le cartouche kitsch (ouverture
 * repliée, faits), **le podium** — trois photogrammes sur un bout de pellicule, entre le cartouche
 * et les salles — **la séance** (brief du 21 septembre 2026, « la séance ») — bouton, carte
 * d'attente ou carte de soirée, sur l'année en cours seulement, entre le podium et les salles
 * (`BlocSeance`) — puis une salle par bloc — titre, raison d'être, étagère horizontale d'affiches —
 * jusqu'à la ligne du bas, sur l'année en cours seulement : le ticket qui attend, ou le verdict de
 * maturité, ou rien (`LigneBasAnneeEnCours`) — le bouton provisoire « Année suivante » a disparu
 * avec elle.
 *
 * Remplace entièrement l'écran « essentiels » du 16 septembre 2026 : plus de grille de vus ou
 * d'à-voir à part, les salles portent déjà tous les films de l'année. `annee` (le fragment du
 * journal/Plex chargé par `FriseViewModel`) ne sert plus qu'à connaître le millésime avant que
 * `GET /me/voyage/annees/{annee}` n'ait répondu, et à fournir mes films vus de l'année au podium
 * (`annee.vus`, décision 2 du brief du 21 septembre 2026).
 */
@Composable
fun AnneeScreen(
    annee: AnneeFrise,
    vm: AnneeViewModel,
    realisateurResolveur: RealisateurResolveur,
    onBack: () -> Unit,
    onOpenFilm: (salleId: String, filmId: String) -> Unit,
    onTicketChange: () -> Unit,
    onPodiumChange: () -> Unit,
    /** « Je l'ai vu » sur la carte de soirée (décision 2 du brief du 21 septembre 2026, « la séance »), même formulaire pré-rempli que la fiche d'un film. */
    onOpenForm: (SearchResult) -> Unit = {},
    /** « Prendre » relit `/me/voyage` (décision 2) : la ligne « Ce soir » de l'accueil en dépend, comme le podium et le ticket. */
    onSeanceChange: () -> Unit = {},
    /** Le nom du réalisateur est touchable sur la carte de soirée (décision 3 du brief du 21 septembre 2026, « la page réalisateur »). */
    onOuvrirRealisateur: (Int) -> Unit = {},
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    val contexte = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    LaunchedEffect(Unit) { vm.relire() }
    val millesime = annee.annee ?: LocalDate.now().year
    val monde = mondeDe(millesime)
    var marcheOuverte by remember { mutableStateOf<Int?>(null) }

    marcheOuverte?.let { place ->
        MarcheSheet(
            place = place,
            marcheActuelle = ui.podium.getOrNull(place - 1),
            candidats = candidatsPodium(annee.vus, millesime, ui.salles),
            onChoisir = { candidat -> marcheOuverte = null; vm.poserPodium(place, candidat, onPodiumChange) },
            onRetirer = { marcheOuverte = null; vm.retirerPodium(place, onPodiumChange) },
            onDismiss = { marcheOuverte = null },
        )
    }

    Scaffold(
        containerColor = monde.fond,
        snackbarHost = { SnackbarHost(snackbar) { data -> Snackbar(snackbarData = data) } },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxWidth().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                    }
                    Column {
                        Text(millesime.toString(), style = MaterialTheme.typography.titleLarge)
                        Text(
                            "${ui.profondeur} ${if (ui.profondeur <= 1) "film" else "films"}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        // Sous la profondeur (décision 2 du brief du 21 septembre 2026, « les
                        // récompenses ») : nulle (pas de ligne) tant que la progression n'est pas
                        // encore chargée — `ligneProgression` seule décide de son texte.
                        ligneProgression(ui.progression)?.let { ligne ->
                            Text(ligne, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(
                            "${monde.nom} · ${monde.sousTitre}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = monde.accent,
                        )
                    }
                }
            }

            item { Cartouche(millesime, ui, monde, onLireLaSuite = vm::deplierOuverture) }

            if (ui.etat == EtatAnnee.PRETE) {
                item {
                    BlocPodium(
                        podium = ui.podium,
                        monde = monde,
                        onTap = { place -> marcheOuverte = place },
                        onLongPress = { place -> vm.retirerPodium(place, onPodiumChange) },
                    )
                }
                // La séance (décision 1 du brief du 21 septembre 2026, « la séance ») : seulement
                // dans la fiche de l'année en cours, entre le podium et les salles.
                if (ui.statutVoyage == StatutAnneeVoyage.EN_COURS) {
                    item {
                        BlocSeance(
                            ui = ui,
                            monde = monde,
                            annee = millesime,
                            vm = vm,
                            realisateurResolveur = realisateurResolveur,
                            onOpenForm = onOpenForm,
                            onSeanceChange = onSeanceChange,
                            onOuvrirRealisateur = onOuvrirRealisateur,
                        )
                    }
                }
                items(ui.salles, key = { it.id }) { salle ->
                    BlocSalle(salle, monde, onVoirPlus = { vm.voirPlus(salle.id) }, onOuvrirFilm = { filmId -> onOpenFilm(salle.id, filmId) })
                }
                item {
                    BlocNouvelleSalle(
                        demandeSalle = ui.demandeSalle,
                        pistes = ui.pistes,
                        pistesEnCours = ui.pistesEnCours,
                        onDemander = { texte, piste -> vm.ouvrirNouvelleSalle(texte, piste) },
                        onDemanderPistes = vm::demanderPistes,
                    )
                }
            }

            if (ui.statutVoyage == StatutAnneeVoyage.EN_COURS) {
                item { LigneBasAnneeEnCours(ligneBasAnnee(ui.ticket, ui.maturite), onUtiliserTicket = { vm.utiliserTicket(onTicketChange) }) }
            }

            // Le carnet (décision 2 du brief du 22 septembre 2026, « le carnet »), sous la ligne du
            // ticket : toute année qui a une ouverture (etat PRETE, la seule condition qui gouverne
            // déjà le podium et les salles ci-dessus), pas seulement l'année en cours.
            if (ui.etat == EtatAnnee.PRETE) {
                item {
                    BlocCarnet(
                        carnet = ui.carnet,
                        carnetEnCours = ui.carnetEnCours,
                        onFaireCarnet = vm::fabriquerCarnet,
                        onOuvrirCarnet = {
                            scope.launch { ouvrirCarnet(contexte, millesime, vm::telechargerCarnetPdf) { message -> snackbar.showBriefly(message) } }
                        },
                    )
                }
            }
        }
    }
}

/**
 * « Faire le carnet »/« Refaire le carnet » (décision 2 du brief du 22 septembre 2026, « le
 * carnet »), désactivé et « Le carnet se fabrique… » pendant `carnetEnCours` ; en dessous, dès
 * qu'un carnet existe déjà, « Fabriqué le… » — un tap l'ouvre (décision 4).
 */
@Composable
private fun BlocCarnet(carnet: CarnetUi?, carnetEnCours: Boolean, onFaireCarnet: () -> Unit, onOuvrirCarnet: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedButton(onClick = onFaireCarnet, enabled = !carnetEnCours, modifier = Modifier.fillMaxWidth()) {
            Text(libelleBoutonCarnet(carnet, carnetEnCours))
        }
        carnet?.let {
            Text(
                ligneFabriqueLeCarnet(it),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.clickable(onClick = onOuvrirCarnet),
            )
        }
    }
}

/**
 * La ligne du bas de la fiche d'année en cours (décision 4 du brief du 21 septembre 2026, « le
 * ticket »), à la place de l'ancien bouton provisoire « Année suivante » : le ticket non utilisé
 * prime sur le verdict de maturité (`ligneBasAnnee`), rien sur la carte dans tous les autres cas.
 */
@Composable
private fun LigneBasAnneeEnCours(ligne: LigneBasAnnee, onUtiliserTicket: () -> Unit) {
    when (ligne) {
        is LigneBasAnnee.TicketEnAttente -> Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("Ton ticket pour ${ligne.anneeSuivante} t’attend", style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onUtiliserTicket) { Text("Utiliser") }
        }
        is LigneBasAnnee.PasEncoreMure -> Text(
            "Pas encore mûre : ${ligne.motif}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LigneBasAnnee.Rien -> {}
    }
}

/**
 * Le cartouche kitsch (papier jauni sur le fond du monde, cadre ornementé simple) : jamais muet
 * (spec §3) — une phrase pour chaque état, y compris la première visite (`202`) et l'abandon.
 */
@Composable
private fun Cartouche(millesime: Int, ui: AnneeUi, monde: Monde, onLireLaSuite: () -> Unit) {
    val shape = RoundedCornerShape(8.dp)
    if (ui.statutVoyage == StatutAnneeVoyage.VERROUILLEE) {
        CartonProchainement(ui, monde, shape)
        return
    }

    Box(
        Modifier
            .fillMaxWidth()
            .background(PapierJauni, shape)
            .border(1.dp, CadrePapier, shape)
            .ornemente()
            .padding(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(millesime.toString(), style = MaterialTheme.typography.titleLarge, color = TextePapier)
                // Le glyphe à côté du millésime (décision 2 du brief du 21 septembre 2026, « les
                // récompenses ») — nul sans aucun film vu.
                ui.recompense?.let { recompense ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .drawBehind { glypheRecompense(recompense, TextePapier, PapierJauni) }
                            .clearAndSetSemantics { contentDescription = recompense.singulier },
                    )
                }
            }
            when (ui.etat) {
                EtatAnnee.PRETE -> ui.ouverture?.let { CartoucheOuverture(it, ui, monde, onLireLaSuite) }
                EtatAnnee.EN_PREPARATION -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = TextePapier)
                    Text(
                        "$millesime s’écrit… ça prend une minute ou deux",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextePapier,
                    )
                }
                EtatAnnee.ABANDON -> Text(
                    "Le chroniqueur n’a pas répondu, reviens plus tard.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextePapier,
                )
                EtatAnnee.VERROUILLEE, EtatAnnee.NON_CONFIGURE -> {}
            }
        }
    }
}

/**
 * L'ouverture, repliée à trois lignes (« Lire la suite » la déplie), puis les faits, puis les
 * paragraphes de la chronique dans l'ordre (décision 2 du brief du 21 septembre 2026, « la
 * chronique et les salles ») : date, titre dans l'accent du monde, texte, puis « à propos de
 * *Titre* » — un filet fin entre deux paragraphes (spec §3).
 */
@Composable
private fun CartoucheOuverture(ouverture: String, ui: AnneeUi, monde: Monde, onLireLaSuite: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            ouverture,
            style = MaterialTheme.typography.bodyLarge,
            color = TextePapier,
            maxLines = if (ui.ouvertureDepliee) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        if (!ui.ouvertureDepliee) {
            TextButton(onClick = onLireLaSuite) { Text("Lire la suite") }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                ui.faits.forEach { fait -> Text("· $fait", style = MaterialTheme.typography.bodyMedium, color = TextePapier) }
            }
            ui.paragraphes.forEachIndexed { index, paragraphe ->
                if (index > 0) HorizontalDivider(color = CadrePapier, thickness = 0.5.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        formatDateTime(paragraphe.ecritLe),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(paragraphe.titre, style = MaterialTheme.typography.titleSmall, color = monde.accent)
                    Text(paragraphe.texte, style = MaterialTheme.typography.bodyMedium, color = TextePapier)
                    Text("à propos de ${paragraphe.filmTitle}", style = MaterialTheme.typography.bodySmall, color = TextePapier)
                }
            }
        }
    }
}

/**
 * Le carton « Prochainement » d'une année verrouillée : le cadre et le lettrage d'une bande-
 * annonce, dans la palette du monde. Sans affiche (spec du 21 septembre 2026 : le back n'en sert
 * plus l'aperçu depuis que les essentiels ont disparu) — seulement ce que `profondeur` dit déjà.
 */
@Composable
private fun CartonProchainement(ui: AnneeUi, monde: Monde, shape: Shape) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f), shape)
            .border(2.dp, monde.accent.copy(alpha = 0.7f), shape)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            "PROCHAINEMENT",
            style = MaterialTheme.typography.titleLarge.copy(letterSpacing = 4.sp, fontWeight = FontWeight.Bold),
            color = monde.accent,
            textAlign = TextAlign.Center,
        )
        Text(
            if (ui.profondeur > 0) {
                "${ui.profondeur} film${if (ui.profondeur > 1) "s" else ""} déjà vu${if (ui.profondeur > 1) "s" else ""}, en avance"
            } else {
                "Cette année n’est pas encore ouverte"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** Le cadre ornementé simple du cartouche : un second liseré, en retrait, dans le même ton que le premier. */
private fun Modifier.ornemente(): Modifier = drawWithContent {
    drawContent()
    drawRoundRect(
        color = CadrePapier,
        topLeft = androidx.compose.ui.geometry.Offset(4.dp.toPx(), 4.dp.toPx()),
        size = androidx.compose.ui.geometry.Size(size.width - 8.dp.toPx(), size.height - 8.dp.toPx()),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(width = 1.dp.toPx()),
    )
}

// --- Le podium (brief du 21 septembre 2026, « le podium ») ---------------------------------------

private val LARGEUR_PODIUM_GRAND = 84.dp
private val LARGEUR_PODIUM_PETIT = 64.dp

/**
 * Le podium (décision 1 du brief) : trois photogrammes posés sur un bout de pellicule, le n°1 plus
 * grand et au centre, le 2 à gauche, le 3 à droite. Toucher une marche ouvre `MarcheSheet` ; un
 * appui long sur une marche occupée la vide directement, sans feuille (décision 2).
 */
@Composable
private fun BlocPodium(podium: List<PodiumMarcheUi?>, monde: Monde, onTap: (Int) -> Unit, onLongPress: (Int) -> Unit) {
    Box(Modifier.fillMaxWidth()) {
        Canvas(Modifier.fillMaxWidth().height(LARGEUR_PELLICULE).align(Alignment.BottomCenter)) {
            bandeDePellicule(couleurBande = monde.accent.copy(alpha = 0.16f), couleurPerforation = monde.fond)
        }
        Row(
            Modifier.fillMaxWidth().padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Bottom,
        ) {
            PhotogrammePodium(2, podium.getOrNull(1), monde, LARGEUR_PODIUM_PETIT, onClick = { onTap(2) }, onLongClick = { onLongPress(2) })
            PhotogrammePodium(1, podium.getOrNull(0), monde, LARGEUR_PODIUM_GRAND, onClick = { onTap(1) }, onLongClick = { onLongPress(1) })
            PhotogrammePodium(3, podium.getOrNull(2), monde, LARGEUR_PODIUM_PETIT, onClick = { onTap(3) }, onLongClick = { onLongPress(3) })
        }
    }
}

/**
 * Un photogramme du podium : un cadre nu, sans texte d'invitation, quand la marche est vide ; sinon
 * l'affiche et le titre sur une ligne (décision 1). Le cadre prend la couleur du chapitre selon la
 * marche (`Monde.couleurPodium`) ; le numéro, en petit, se lit sous le cadre dans tous les cas.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PhotogrammePodium(
    place: Int,
    marche: PodiumMarcheUi?,
    monde: Monde,
    largeur: Dp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    val hauteur = largeur * 1.5f
    Column(
        Modifier
            .combinedClickable(onClick = onClick, onLongClick = if (marche != null) onLongClick else null)
            .clearAndSetSemantics {
                contentDescription = if (marche != null) "Marche $place, ${marche.title}" else "Marche $place, vide"
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(largeur, hauteur)
                .background(Color.Black, shape)
                .border(1.5.dp, monde.couleurPodium(place), shape),
        ) {
            marche?.let { Cover(it.coverUrl, it.title, largeur, hauteur) }
        }
        Text(
            "$place",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
        if (marche != null) {
            Text(
                marche.title,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(largeur),
            )
        }
    }
}

/**
 * La feuille « Marche *N* » (décision 2) : « Retirer du podium » en tête si la marche est occupée,
 * puis mes films vus de l'année et mes programmes entièrement vus (`candidatsPodium`), l'occupant
 * actuel coché.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MarcheSheet(
    place: Int,
    marcheActuelle: PodiumMarcheUi?,
    candidats: List<CandidatPodium>,
    onChoisir: (CandidatPodium) -> Unit,
    onRetirer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val lignes = lignesFeuillePodium(place, marcheActuelle, candidats)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Marche $place", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            if (candidats.isEmpty()) {
                Text(
                    "Rien à poser ici pour l’instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                items(lignes) { ligne ->
                    when (ligne) {
                        is LignePodiumFeuille.Retirer -> Row(
                            Modifier.fillMaxWidth().clickable(onClick = onRetirer).padding(vertical = 10.dp),
                        ) {
                            Text("Retirer du podium", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                        }
                        is LignePodiumFeuille.Candidat -> LigneCandidatPodium(ligne.candidat, ligne.estOccupant, onClick = { onChoisir(ligne.candidat) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LigneCandidatPodium(candidat: CandidatPodium, occupant: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box {
            Cover(candidat.coverUrl, candidat.title, 56.dp, 84.dp)
            val note = (candidat as? CandidatPodium.Film)?.note
            if (note != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                ) {
                    Text("$note", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
        Text(candidat.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (occupant) {
            Box(Modifier.background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "Occupant actuel",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}

// --- La séance (brief du 21 septembre 2026, « la séance ») ---------------------------------------

/**
 * La zone séance, entre le podium et les salles, sur l'année en cours seulement (décision 1) :
 * le bouton « Composer une séance », la carte d'attente pendant la composition, la carte de soirée
 * pour la séance la plus récente (`etatZoneSeance`), puis « Séances passées » sous tout ça.
 */
@Composable
private fun BlocSeance(
    ui: AnneeUi,
    monde: Monde,
    annee: Int,
    vm: AnneeViewModel,
    realisateurResolveur: RealisateurResolveur,
    onOpenForm: (SearchResult) -> Unit,
    onSeanceChange: () -> Unit,
    onOuvrirRealisateur: (Int) -> Unit,
) {
    var remplacement by remember { mutableStateOf<String?>(null) }
    val seanceCourante = seanceRecente(ui.seances)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        when (etatZoneSeance(ui.seanceEnCours, ui.seances)) {
            EtatZoneSeance.BOUTON -> OutlinedButton(onClick = vm::composerSeance, modifier = Modifier.fillMaxWidth()) {
                Text("Composer une séance")
            }
            EtatZoneSeance.EN_COURS -> CarteAttenteSeance()
            EtatZoneSeance.CARTE_PROPOSEE, EtatZoneSeance.CARTE_PRISE -> seanceCourante?.let { seance ->
                CarteSeance(
                    seance = seance,
                    monde = monde,
                    realisateurResolveur = realisateurResolveur,
                    onPrendre = { vm.prendreSeance(seance.id, onSeanceChange) },
                    onIgnorer = { vm.ignorerSeance(seance.id) },
                    onAutreLong = { remplacement = "long" },
                    onAutreCourt = { remplacement = "court" },
                    onDemander = vm::demander,
                    onOpenForm = { film -> onOpenForm(film.versSearchResult(annee)) },
                    onOuvrirRealisateur = onOuvrirRealisateur,
                )
            }
            EtatZoneSeance.RIEN -> {}
        }

        val passees = seancesPassees(ui.seances)
        if (passees.isNotEmpty()) {
            SeancesPasseesBloc(passees)
        }
    }

    remplacement?.let { morceau ->
        val seance = seanceCourante
        if (seance == null) {
            remplacement = null
        } else {
            val groupes = if (morceau == "long") candidatsSeanceLong(ui.salles) else candidatsSeanceCourt(ui.salles, seance.long.filmId)
            val occupantTmdbId = if (morceau == "long") seance.long.tmdbId else seance.court?.tmdbId
            RemplacementSeanceSheet(
                morceau = morceau,
                groupes = groupes,
                occupantTmdbId = occupantTmdbId,
                onChoisir = { candidat ->
                    remplacement = null
                    vm.remplacerSeance(seance.id, corpsRemplacementSeance(morceau, candidat))
                },
                onDismiss = { remplacement = null },
            )
        }
    }
}

@Composable
private fun CarteAttenteSeance() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, RoundedCornerShape(8.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
        Text("Le chroniqueur compose…", style = MaterialTheme.typography.bodyMedium)
    }
}

/**
 * La carte de soirée (décision 2) : sobre, dans la palette du monde — un fond neutre, un liseré
 * dans l'accent du monde, jamais le papier jauni kitsch du cartouche. Le long, puis le court en
 * plus petit s'il y en a un, puis l'anecdote en italique, puis les actions selon le statut.
 */
@Composable
private fun CarteSeance(
    seance: SeanceUi,
    monde: Monde,
    realisateurResolveur: RealisateurResolveur,
    onPrendre: () -> Unit,
    onIgnorer: () -> Unit,
    onAutreLong: () -> Unit,
    onAutreCourt: () -> Unit,
    onDemander: (Int) -> Unit,
    onOpenForm: (SeanceFilmUi) -> Unit,
    onOuvrirRealisateur: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, shape)
            .border(1.dp, monde.accent.copy(alpha = 0.4f), shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "CE SOIR",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 2.sp),
            color = monde.accent,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Cover(seance.long.coverUrl, seance.long.title, 56.dp, 84.dp)
            Column {
                Text(seance.long.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${seance.long.salle} · ${etiquetteEtatSeanceFilm(seance.long.etat)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Le nom du réalisateur, touchable (décision 3 du brief du 21 septembre 2026, «
                // la page réalisateur ») : sous le titre, comme sur la fiche du Voyage — nul ici
                // (`nomConnu`), résolu par `NomRealisateurTouchable` elle-même.
                NomRealisateurTouchable(
                    filmTmdbId = seance.long.tmdbId,
                    nomConnu = null,
                    resolveur = realisateurResolveur,
                    onOuvrirRealisateur = onOuvrirRealisateur,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        seance.court?.let { court ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Cover(court.coverUrl, court.title, 40.dp, 60.dp)
                Column {
                    Text("en ouverture", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    court.bobine?.let { bobine ->
                        Text(bobine.title, style = MaterialTheme.typography.bodyMedium)
                    }
                    NomRealisateurTouchable(
                        filmTmdbId = court.tmdbId,
                        nomConnu = null,
                        resolveur = realisateurResolveur,
                        onOuvrirRealisateur = onOuvrirRealisateur,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Column {
            Text("Pendant le générique", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(seance.anecdote, style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic))
        }
        when (seance.statut) {
            // Deux rangées de deux (constaté sur téléphone, 21 septembre 2026) : les quatre
            // actions sur une seule `Row` ne tenaient pas en largeur, « Ignorer » se cassait sur
            // plusieurs lignes. `BoutonSeance` fixe `maxLines = 1, softWrap = false` sur chaque
            // libellé plutôt que de réduire la police.
            "proposee" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BoutonSeance("Prendre", onPrendre)
                    BoutonSeance("Ignorer", onIgnorer)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    BoutonSeance("Autre long", onAutreLong)
                    BoutonSeance("Autre court", onAutreCourt)
                }
            }
            "prise" -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Prise", style = MaterialTheme.typography.labelMedium, color = monde.accent)
                LigneActionsFilmSeance(seance.long, onDemander, onOpenForm)
                seance.court?.let { LigneActionsFilmSeance(it, onDemander, onOpenForm) }
            }
        }
    }
}

/**
 * Un bouton de la carte de soirée : son libellé sur une seule ligne, jamais coupé (`maxLines = 1,
 * softWrap = false`), sans réduire la police — c'est la disposition en deux rangées de deux qui
 * garde la place, pas un texte rétréci.
 */
@Composable
private fun BoutonSeance(texte: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) { Text(texte, maxLines = 1, softWrap = false) }
}

/** « Voir sur le Plex » (lien `plex://` puis web, comme la fiche) ou « Demander sur Sir », et « Je l'ai vu » — sur chaque film (décision 2). */
@Composable
private fun LigneActionsFilmSeance(film: SeanceFilmUi, onDemander: (Int) -> Unit, onOpenForm: (SeanceFilmUi) -> Unit) {
    val contexte = LocalContext.current
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        if (film.plexUrl != null) {
            TextButton(onClick = { ouvrirPlex(contexte, film.plexUrl) }) { Text("Voir sur le Plex") }
        } else if (film.etat == "a_demander") {
            TextButton(onClick = { onDemander(film.tmdbId) }) { Text("Demander sur Sir") }
        }
        if (film.etat != "vu") {
            TextButton(onClick = { onOpenForm(film) }) { Text("Je l’ai vu") }
        }
    }
}

/**
 * « Séances passées » (décision 2) : titre du long · date, repliées sous ce titre — un tap sur une
 * ligne ne fait rien. Une séance prise dont le long est vu porte la mention « · vue » (corrigé le
 * 21 septembre 2026 : elle est terminée, comme une ignorée, plutôt que de tenir la carte pour
 * toujours).
 */
@Composable
private fun SeancesPasseesBloc(passees: List<SeanceUi>) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Séances passées", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        passees.forEach { seance ->
            val vue = seance.statut == "prise" && seance.long.etat == "vu"
            Text(
                "${seance.long.title} · ${formatDateTime(seance.composeeLe)}" + if (vue) " · vue" else "",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * La feuille « Autre long » / « Autre court » (décision 3) : un choix local, sans appel — les
 * candidats viennent des salles déjà chargées (`candidatsSeanceLong`/`candidatsSeanceCourt`),
 * groupés par salle, une bobine en retrait sous son programme. L'occupant actuel coché.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RemplacementSeanceSheet(
    morceau: String,
    groupes: List<GroupeCandidatsSeance>,
    occupantTmdbId: Int?,
    onChoisir: (CandidatSeance) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                if (morceau == "long") "Un autre long" else "Un autre court",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            if (groupes.isEmpty()) {
                Text(
                    "Rien à proposer pour l’instant.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            LazyColumn(Modifier.weight(1f, fill = false)) {
                groupes.forEach { groupe ->
                    item {
                        Text(
                            groupe.salle,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
                        )
                    }
                    items(groupe.candidats) { candidat ->
                        val retrait = candidat is CandidatSeance.Bobine
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = { onChoisir(candidat) })
                                .padding(start = if (retrait) 24.dp else 0.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Cover(candidat.coverUrl, candidat.title, 40.dp, 60.dp)
                            Text(candidat.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            if (candidat.tmdbId == occupantTmdbId) {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = "Occupant actuel",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Le même formulaire pré-rempli qu'un film ou une bobine de la fiche du Voyage. */
private fun SeanceFilmUi.versSearchResult(annee: Int): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdbId.toString(),
    type = "movie",
    title = title,
    year = annee,
    cover_url = coverUrl,
    metadata = SearchMetadata(director = null),
    original_title = title,
)

// --- Les salles ----------------------------------------------------------------------------------

private val LARGEUR_AFFICHE = 72.dp
private val HAUTEUR_AFFICHE = 108.dp

/** La teinte sépia d'un film pas encore vu (spec §3) : un voile posé sur une affiche désaturée. */
private val FiltreDesature = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })

/** Interne au paquet, pas seulement au fichier : `Mondes.kt` reprend ce même sépia pour la marche 3 du podium (brief du 21 septembre 2026). */
internal val TeinteSepia = Color(0xFF3A2C1E)

@Composable
private fun BlocSalle(salle: SalleUi, monde: Monde, onVoirPlus: () -> Unit, onOuvrirFilm: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(salle.nom, style = MaterialTheme.typography.titleMedium)
        Text(salle.raisonDEtre, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            contentPadding = PaddingValues(vertical = 4.dp),
        ) {
            items(salle.films, key = { it.id }) { film ->
                AfficheFilm(film, onClick = { onOuvrirFilm(film.id) })
            }
            item {
                TuileEtagere(
                    epuisee = salle.epuisee,
                    fourneeEnCours = salle.fourneeEnCours,
                    onClick = onVoirPlus,
                )
            }
        }
    }
}

@Composable
private fun AfficheFilm(film: FilmSalleUi, onClick: () -> Unit) {
    val etat = etatFilmVoyage(film.etat, film.programme?.bobines ?: emptyList())
    val vu = etat == "vu"
    val etiquette = etiquetteEtatFilm(etat)

    Column(
        Modifier.clickable(onClick = onClick).width(LARGEUR_AFFICHE),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Cover(film.coverUrl, film.title, LARGEUR_AFFICHE, HAUTEUR_AFFICHE, colorFilter = if (vu) null else FiltreDesature)
            if (!vu) {
                Box(Modifier.size(LARGEUR_AFFICHE, HAUTEUR_AFFICHE).background(TeinteSepia.copy(alpha = 0.35f)))
            }
            if (vu && film.note != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                        .clearAndSetSemantics { contentDescription = "Note ${film.note} sur 10" },
                ) {
                    Text("${film.note}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurface)
                }
            }
            film.programme?.let { programme ->
                Text(
                    "${programme.bobines.size} bobines · ${programme.dureeMin} min",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f))
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                )
            }
        }
        if (etiquette != null) {
            Text(
                etiquette,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** La tuile en bout d'étagère : « En voir plus » (cadre pointillé corail), « Salle épuisée » (grisée, inerte), ou « La salle se remplit… » (indicateur). */
@Composable
private fun TuileEtagere(epuisee: Boolean, fourneeEnCours: Boolean, onClick: () -> Unit) {
    val etiquette = etiquetteEtagere(epuisee, fourneeEnCours)
    val remplit = fourneeEnCours
    Box(
        Modifier
            .size(LARGEUR_AFFICHE, HAUTEUR_AFFICHE)
            .let {
                if (epuisee || remplit) {
                    it.background(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.shapes.small)
                } else {
                    it.dashedBorder(MaterialTheme.colorScheme.primary, cornerRadius = 8.dp).clickable(onClick = onClick)
                }
            }
            .padding(8.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (remplit) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.primary)
                Text(etiquette, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
        } else {
            Text(
                etiquette,
                style = MaterialTheme.typography.labelSmall,
                color = if (epuisee) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// --- « Ouvrir une nouvelle salle » (décision 3 du brief du 21 septembre 2026, « la chronique et les salles ») ---

/**
 * Le bouton « Ouvrir une nouvelle salle » sous la dernière étagère, l'étagère fantôme pendant que
 * la demande s'écrit, ou le motif du refus sous le bouton (`etatZoneSalleVoyage`) — jamais les deux
 * à la fois. `creee` (la salle apparue dans `salles`, `demandeSalle` retombé à `null`) retombe sur
 * le bouton, sans rien de plus à dire ici. `pistes`/`pistesEnCours` ne font que traverser vers la
 * feuille (brief du 22 septembre 2026, « les pistes ») : rien sur la fiche d'année hors d'elle.
 */
@Composable
private fun BlocNouvelleSalle(
    demandeSalle: DemandeSalleUi?,
    pistes: List<PisteUi>,
    pistesEnCours: Boolean,
    onDemander: (demande: String, piste: String?) -> Unit,
    onDemanderPistes: () -> Unit,
) {
    var sheetOuverte by remember { mutableStateOf(false) }

    when (etatZoneSalleVoyage(demandeSalle?.statut)) {
        EtatZoneSalleVoyage.FANTOME -> EtagereFantome()
        EtatZoneSalleVoyage.BOUTON -> OutlinedButton(onClick = { sheetOuverte = true }, modifier = Modifier.fillMaxWidth()) {
            Text("Ouvrir une nouvelle salle")
        }
        EtatZoneSalleVoyage.REFUS -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            OutlinedButton(onClick = { sheetOuverte = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Ouvrir une nouvelle salle")
            }
            Text(
                demandeSalle?.motif ?: "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (sheetOuverte) {
        NouvelleSalleSheet(
            pistes = pistes,
            pistesEnCours = pistesEnCours,
            onDemander = { texte, piste -> sheetOuverte = false; onDemander(texte, piste) },
            onDemanderPistes = onDemanderPistes,
            onDismiss = { sheetOuverte = false },
        )
    }
}

/** « La salle s'ouvre… » en italique, une rangée de trois cadres vides pointillés — la même forme qu'une salle, sans film à montrer encore. */
@Composable
private fun EtagereFantome() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            "La salle s’ouvre…",
            style = MaterialTheme.typography.titleMedium.copy(fontStyle = FontStyle.Italic),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .size(LARGEUR_AFFICHE, HAUTEUR_AFFICHE)
                        .dashedBorder(MaterialTheme.colorScheme.onSurfaceVariant, cornerRadius = 8.dp),
                )
            }
        }
    }
}

/**
 * La feuille « Quelle salle ? » (décision 3 du brief du 21 septembre 2026, décision 1 et 3 du
 * brief du 22 septembre 2026, « les pistes ») : au-dessus du champ, une pastille par piste
 * (`AssistChip`, le `nom`) — un tap la touche (`nouvelleSalleSuivant`), remplit le champ avec son
 * nom et affiche sa `raison` en dessous, sous la rangée (choix le plus simple entre elle et un
 * tooltip, décision 1). Le champ reste éditable ensuite : retoucher le texte ne défait jamais la
 * pastille touchée, c'est elle que « Demander » envoie en `piste`. Sans aucune piste, « D'autres
 * pistes » (décision 3) : désactivé et « Le chroniqueur cherche… » pendant l'appel synchrone.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NouvelleSalleSheet(
    pistes: List<PisteUi>,
    pistesEnCours: Boolean,
    onDemander: (demande: String, piste: String?) -> Unit,
    onDemanderPistes: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var etat by remember { mutableStateOf(NouvelleSalleEtat()) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Quelle salle ?", style = MaterialTheme.typography.titleMedium)
            if (pistes.isNotEmpty()) {
                // FlowRow, pas Row : trois noms de salle ne tiennent pas sur une ligne, la
                // troisième pastille se retrouvait coupée à trois lettres (retouche du 22 septembre 2026).
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    pistes.forEach { piste ->
                        AssistChip(
                            onClick = { etat = nouvelleSalleSuivant(etat, NouvelleSalleEvenement.ToucherPiste(piste)) },
                            label = { Text(piste.nom) },
                        )
                    }
                }
                pistes.firstOrNull { it.nom == etat.pisteTouchee }?.let { touchee ->
                    Text(
                        touchee.raison,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (autresPistesVisible(pistes)) {
                TextButton(onClick = onDemanderPistes, enabled = !pistesEnCours) {
                    Text(if (pistesEnCours) "Le chroniqueur cherche…" else "D’autres pistes")
                }
            }
            OutlinedTextField(
                value = etat.texte,
                onValueChange = { if (it.length <= 200) etat = nouvelleSalleSuivant(etat, NouvelleSalleEvenement.Ecrire(it)) },
                singleLine = true,
                supportingText = { Text("une phrase : la comédie italienne cette année-là") },
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = { onDemander(etat.texte.trim(), etat.pisteTouchee) },
                enabled = etat.texte.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Demander") }
        }
    }
}

/**
 * Le liseré pointillé qui distingue une tuile « à voir » d'une tuile vue (brief du 15 septembre
 * 2026) : pas de note, pas de coche, juste ce contour. Repris tel quel (couleur libre) par
 * l'étagère du rayon d'une décennie (`DecennieScreen`) et par la tuile « En voir plus » ci-dessus.
 */
internal fun Modifier.dashedBorder(color: Color, cornerRadius: Dp, strokeWidth: Dp = 1.5.dp): Modifier =
    drawWithContent {
        drawContent()
        drawRoundRect(
            color = color,
            cornerRadius = CornerRadius(cornerRadius.toPx()),
            style = Stroke(
                width = strokeWidth.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f), 0f),
            ),
        )
    }
