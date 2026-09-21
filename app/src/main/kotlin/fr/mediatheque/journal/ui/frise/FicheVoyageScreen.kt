package fr.mediatheque.journal.ui.frise

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.form.CartonCard
import fr.mediatheque.journal.ui.form.CartonViewModel
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier

/**
 * La fiche d'un film du Voyage (nouvel écran, `Screen.FicheVoyage`, spec du 19 septembre 2026, §3,
 * brief du 21 septembre 2026 §4) : affiche, titre, réalisateur, durée d'un programme s'il y en a
 * une ; la salle et la raison en évidence ; ma note et mes réactions si je l'ai déjà vu (depuis le
 * journal déjà chargé par `FriseViewModel`) ; le carton « Et pendant ce temps… » existant ; pour un
 * programme, ses bobines, chacune ouvrant le formulaire pré-rempli ; puis les boutons selon l'état.
 *
 * Le podium s'y ajoute le 21 septembre 2026 (décision 3 du brief « le podium ») : « Mettre sur le
 * podium » ouvre le choix d'une marche (`lignesChoixMarche`). « Ajouter à la chronique » (décision 1
 * du brief « la chronique et les salles », étape 4 de la spec, même jour) s'ajoute en dernier :
 * absente si le film n'est pas vu, sinon « Ajouter », « Le chroniqueur écrit… » pendant la
 * relecture, ou « Dans la chronique » avec le paragraphe affiché dessous.
 *
 * Lit `vm` (le même `AnneeViewModel` que l'année d'où elle s'est ouverte, `Root.kt`) plutôt que de
 * recharger quoi que ce soit : `salleId` et `filmId` désignent le film dans son état déjà connu.
 */
@Composable
fun FicheVoyageScreen(
    annee: Int,
    vm: AnneeViewModel,
    salleId: String,
    filmId: String,
    journalItem: JournalItem?,
    carton: CartonViewModel,
    onBack: () -> Unit,
    onOpenForm: (SearchResult) -> Unit,
    onPodiumChange: () -> Unit,
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
            // La fiche s'ouvre toujours depuis une affiche déjà affichée par `AnneeScreen` : ce
            // film est donc déjà dans `vm.ui` sauf coup de malchance (retour système pendant un
            // rechargement des salles). `salle` est alors nécessairement nul aussi (`film` en
            // dérive par appel sûr) — un simple retour plutôt qu'un écran d'erreur muet.
            LaunchedEffect(Unit) { onBack() }
            return@Scaffold
        }

        val etat = etatFilmVoyage(film.etat, film.programme?.bobines ?: emptyList())
        val boutons = boutonsFicheVoyage(etat, film.plexUrl)

        Column(
            Modifier.fillMaxWidth().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour")
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Cover(film.coverUrl, film.title, 96.dp, 144.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(film.title, style = MaterialTheme.typography.titleLarge)
                    if (film.originalTitle != null && film.originalTitle != film.title) {
                        Text(film.originalTitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(film.realisateur, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    film.programme?.let { programme ->
                        Text(
                            "${programme.dureeMin} min",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            CartoucheSalleEtRaison(salle.nom, film.raison)

            if (etat == "vu" && journalItem != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    journalItem.entry.rating?.let { note ->
                        Text("$note", style = MaterialTheme.typography.titleMedium)
                    }
                    if (journalItem.carnet.reactions.isNotEmpty()) {
                        Text(
                            journalItem.carnet.reactions.joinToString(" ") { Reactions.emoji(it) },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            val cartonUi by carton.ui.collectAsState()
            CartonCard(cartonUi, attente = false, onDismiss = {})

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

            // « Ajouter à la chronique » (décision 1 du brief du 21 septembre 2026, « la chronique
            // et les salles ») : absente si le film n'est pas vu (`etatBoutonChronique`), sinon
            // « Ajouter », « Le chroniqueur écrit… », ou « Dans la chronique » avec le paragraphe
            // affiché dessous, dès que `ui.paragraphes` le porte.
            val cleFilm: Pair<Int?, String?> = if (film.programme != null) null to film.id else film.tmdbId to null
            val paragrapheDeCeFilm = ui.paragraphes.firstOrNull { (it.tmdbId to it.programmeId) == cleFilm }
            val etatChronique = etatBoutonChronique(etat, paragrapheDeCeFilm != null, cleFilm in ui.paragraphesEnCours)
            if (etatChronique != EtatBoutonChronique.ABSENT) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (etatChronique) {
                        EtatBoutonChronique.AJOUTER -> OutlinedButton(
                            onClick = { vm.ajouterChronique(cleFilm.first, cleFilm.second) },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Ajouter à la chronique") }
                        EtatBoutonChronique.ECRIT_EN_COURS -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("Le chroniqueur écrit…")
                        }
                        EtatBoutonChronique.DANS_LA_CHRONIQUE -> OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                            Text("Dans la chronique")
                        }
                        EtatBoutonChronique.ABSENT -> {}
                    }
                    paragrapheDeCeFilm?.let { paragraphe ->
                        Text(paragraphe.texte, style = MaterialTheme.typography.bodyMedium)
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
                    if (ligne.estCeFilm) {
                        Icon(Icons.Filled.Check, contentDescription = "Marche actuelle", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
private fun CartoucheSalleEtRaison(nom: String, raison: String?) {
    val shape = RoundedCornerShape(8.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .background(PapierJauni, shape)
            .border(1.dp, CadrePapier, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(nom, style = MaterialTheme.typography.titleMedium, color = TextePapier)
        if (raison != null) {
            Text(raison, style = MaterialTheme.typography.bodyMedium, color = TextePapier)
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

/** Le même formulaire pré-rempli qu'un « à voir » du Plex (`PlexFilm.toSearchResult()`). */
private fun FilmSalleUi.versSearchResult(annee: Int): SearchResult = SearchResult(
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
