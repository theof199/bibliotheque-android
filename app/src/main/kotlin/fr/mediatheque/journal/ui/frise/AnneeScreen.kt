package fr.mediatheque.journal.ui.frise

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier
import java.time.LocalDate

/**
 * La fiche d'une année du Voyage (brief du 21 septembre 2026, « l'année en étages », spec du
 * 19 septembre 2026, §2-§3) : le cartouche kitsch (ouverture repliée, faits), puis une salle par
 * bloc — titre, raison d'être, étagère horizontale d'affiches — jusqu'à « Année suivante »,
 * provisoire, sur l'année en cours seulement.
 *
 * Remplace entièrement l'écran « essentiels » du 16 septembre 2026 : plus de grille de vus ou
 * d'à-voir à part, les salles portent déjà tous les films de l'année. `annee` (le fragment du
 * journal/Plex chargé par `FriseViewModel`) ne sert plus qu'à connaître le millésime avant que
 * `GET /me/voyage/annees/{annee}` n'ait répondu.
 */
@Composable
fun AnneeScreen(
    annee: AnneeFrise,
    vm: AnneeViewModel,
    onBack: () -> Unit,
    onOpenFilm: (salleId: String, filmId: String) -> Unit,
    onAnneeSuivante: () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    LaunchedEffect(Unit) { vm.relire() }
    val millesime = annee.annee ?: LocalDate.now().year
    val monde = mondeDe(millesime)

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
                items(ui.salles, key = { it.id }) { salle ->
                    BlocSalle(salle, monde, onVoirPlus = { vm.voirPlus(salle.id) }, onOuvrirFilm = { filmId -> onOpenFilm(salle.id, filmId) })
                }
            }

            if (ui.statutVoyage == StatutAnneeVoyage.EN_COURS) {
                item {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        OutlinedButton(onClick = { vm.anneeSuivante(onAnneeSuivante) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Année suivante")
                        }
                        Text(
                            "provisoire, en attendant le ticket",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
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
                // La récompense de festival — nulle à cette étape, le back n'en sert aucune
                // (`VoyageCarte.kt`) ; le glyphe reste dans le code pour le jour où elle reviendra.
                ui.recompenseObtenue?.let { recompense ->
                    Box(
                        Modifier
                            .size(16.dp)
                            .drawBehind { glypheRecompense(recompense, TextePapier, PapierJauni) }
                            .clearAndSetSemantics { contentDescription = recompense.singulier },
                    )
                }
            }
            when (ui.etat) {
                EtatAnnee.PRETE -> ui.ouverture?.let { CartoucheOuverture(it, ui, onLireLaSuite) }
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

/** L'ouverture, repliée à trois lignes (« Lire la suite » la déplie), puis les faits (spec §3). */
@Composable
private fun CartoucheOuverture(ouverture: String, ui: AnneeUi, onLireLaSuite: () -> Unit) {
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

// --- Les salles ----------------------------------------------------------------------------------

private val LARGEUR_AFFICHE = 72.dp
private val HAUTEUR_AFFICHE = 108.dp

/** La teinte sépia d'un film pas encore vu (spec §3) : un voile posé sur une affiche désaturée. */
private val FiltreDesature = ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
private val TeinteSepia = Color(0xFF3A2C1E)

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
