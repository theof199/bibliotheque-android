package fr.mediatheque.journal.ui.suivis

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.EtatVide
import fr.mediatheque.journal.ui.Puce
import fr.mediatheque.journal.ui.showBriefly
import fr.mediatheque.journal.ui.theme.BarreProgressionOr
import fr.mediatheque.journal.ui.theme.EtiquetteEnsuite
import fr.mediatheque.journal.ui.theme.FiletOr
import fr.mediatheque.journal.ui.theme.IconeTabler
import java.time.LocalDate

/**
 * Ce que je suis — réalisateurs ou sagas (brief du 15 septembre 2026, l'onglet « Réalisateurs »
 * devient « Suivis »), lus depuis le 25 septembre 2026 comme des **rétrospectives** et des
 * **cycles** (canvas de Léon, « Suivis · les rétrospectives » et « Suivis · les cycles »).
 *
 * En-tête : « Suivis », le compte des deux sources à droite (`compteSuivis`), le filet or de Mes
 * films (`FiletOr`). Deux puces (`Puce`) choisissent la source, à la place des segments d'avant.
 * Le bouton rond corail en bas à droite ouvre la recherche de la source affichée — le seul corail
 * plein de l'écran, qui remplace le « + » de l'en-tête ; le retour de la recherche fait apparaître
 * « Ajouté » ici, en snackbar.
 *
 * Les cartes se lisent de la plus récemment active à la plus ancienne, les bouclées à part en bas
 * sous « Rétrospectives complètes » / « Cycles complets » (`repartirSuivis`) : un ordre d'affichage
 * seulement, les listes du `SuivisViewModel` restent dans l'ordre du back.
 */
@Composable
fun SuivisScreen(
    vm: SuivisViewModel,
    onAjouter: () -> Unit,
    onOuvrir: (SourceSuivi, Int) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val etat = if (ui.source == SourceSuivi.REALISATEURS) ui.realisateurs else ui.sagas
    val snackbar = remember { SnackbarHostState() }
    // Clé fixe, même raison que sur l'accueil : `vm.messages` est un événement à un coup, et une
    // clé qui bougerait à chaque message couperait la snackbar avant ses deux secondes.
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showBriefly(it) } }
    val (enCours, bouclees) = remember(etat.entites, etat.filmographies) {
        repartirSuivis(etat.entites, etat.filmographies)
    }
    // Le jour d'aujourd'hui, lu une fois par entrée sur l'écran : « vu il y a 3 jours » n'a pas à
    // se recalculer à minuit sous les yeux.
    val aujourdHui = remember { LocalDate.now() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = bottomBar,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAjouter,
                shape = CircleShape,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { IconeTabler("plus", ui.source.libelleAjouter, tint = MaterialTheme.colorScheme.onPrimary) }
        },
        floatingActionButtonPosition = FabPosition.End,
        snackbarHost = {
            // 84 dp, comme l'accueil : le bouton rond (56 dp) plus sa marge, et un peu d'air.
            SnackbarHost(snackbar, modifier = Modifier.padding(bottom = 84.dp)) { data ->
                Snackbar(
                    snackbarData = data,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                )
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(top = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Suivis", style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.weight(1f))
                // Absent tant qu'une des deux listes n'a jamais répondu : pas de « 0 rétrospective »
                // qui grimperait sous les yeux.
                if (ui.realisateurs.dejaLue() && ui.sagas.dejaLue()) {
                    Text(
                        compteSuivis(ui.realisateurs.entites.size, ui.sagas.entites.size),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FiletOr()
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SourceSuivi.entries.forEach { source ->
                    Puce(source.titre, active = ui.source == source, onClick = { vm.selectionnerSource(source) })
                }
            }

            etat.error?.let { e ->
                ErrorBlock(
                    e.message ?: "",
                    retryable = e.retryable,
                    onRetry = { vm.refresh(ui.source) },
                    modifier = Modifier.padding(16.dp),
                )
            }

            if (etat.entites.isEmpty() && etat.error == null && !etat.loading) {
                // État vide (point 16 de la revue du 24 septembre 2026) : une icône, la phrase
                // déjà là (`libelleVide`), une action — « Ajouter » ouvre la même recherche que le
                // bouton rond.
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    EtatVide(
                        icone = if (ui.source == SourceSuivi.REALISATEURS) "user" else "movie",
                        phrase = ui.source.libelleVide,
                        libelleAction = "Ajouter",
                        onAction = onAjouter,
                    )
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    // En bas, 88 dp plutôt que 16 : le bouton rond (56 dp et sa marge de 16) ne doit
                    // pas couvrir la dernière carte une fois la liste au bout.
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(enCours, key = { it.tmdbId }) { entite ->
                        CarteSuivi(
                            source = ui.source,
                            entite = entite,
                            etatFilmographie = etat.filmographies[entite.tmdbId] ?: EtatFilmographie.EnAttente,
                            aujourdHui = aujourdHui,
                            bouclee = false,
                            onClick = { onOuvrir(ui.source, entite.tmdbId) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (bouclees.isNotEmpty()) {
                        item(key = "complets") {
                            Text(
                                ui.source.titreComplets.uppercase(),
                                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.14.em),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.animateItem().padding(top = 16.dp),
                            )
                        }
                        items(bouclees, key = { it.tmdbId }) { entite ->
                            CarteSuivi(
                                source = ui.source,
                                entite = entite,
                                etatFilmographie = etat.filmographies[entite.tmdbId] ?: EtatFilmographie.EnAttente,
                                aujourdHui = aujourdHui,
                                bouclee = true,
                                onClick = { onOuvrir(ui.source, entite.tmdbId) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Vrai dès que cette source a répondu une fois (même vide) — le compte de l'en-tête l'attend. */
private fun SuiviState.dejaLue(): Boolean = !loading || entites.isNotEmpty()

/**
 * Une carte par rétrospective ou par cycle (canvas de Léon, 25 septembre 2026) : fond
 * `surfaceContainer`, coins 16.
 * - Ligne 1 : le portrait liseré (52 dp, rétrospectives seulement — un cycle attend sa bande en
 *   livraison 2), le nom, la sous-ligne (`sousLigneCarte`), à droite « 4/12 ».
 * - Ligne 2 : la barre or (`BarreProgressionOr`).
 * - Ligne 3 : « ENSUITE » et le prochain film à voir, son affiche liserée d'or à 22 %.
 * Bouclée (section du bas) : à 85 %, le compte en or, pas de ligne 3, le sceau sur le portrait.
 * Filmographie `EnAttente`/`Indisponible` : le nom, puis « … » / « indisponible » (`libelleLigne`)
 * à la place du reste.
 */
@Composable
private fun CarteSuivi(
    source: SourceSuivi,
    entite: EntiteSuivie,
    etatFilmographie: EtatFilmographie,
    aujourdHui: LocalDate,
    bouclee: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forme = RoundedCornerShape(16.dp)
    val films = (etatFilmographie as? EtatFilmographie.Pret)?.films
    val vus = films?.let(::filmsVus) ?: 0
    val total = films?.size ?: 0
    Column(
        modifier
            .fillMaxWidth()
            .alpha(if (bouclee) 0.85f else 1f)
            .clip(forme)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainer, forme)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (source == SourceSuivi.REALISATEURS) {
                Box {
                    Portrait(entite.imageUrl, entite.nom, 52.dp, lisere = true)
                    if (bouclee) SceauRetrospective(22.dp, anime = false, modifier = Modifier.align(Alignment.BottomEnd))
                }
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entite.nom, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (films != null) {
                    Text(
                        sousLigneCarte(source, vus, total, dernierVisionnage(films), entite.ajouteLe, aujourdHui, bouclee),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (films != null) CompteCarte(vus, total, bouclee)
        }
        if (films == null) {
            Text(
                libelleLigne(etatFilmographie),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        BarreProgressionOr(vus, total)
        if (!bouclee) {
            prochainAVoir(films)?.let { prochain ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(
                        prochain.cover_url,
                        prochain.title,
                        40.dp,
                        60.dp,
                        // Le liseré or à 22 % des affiches de Mes films (`LigneFilm.kt`).
                        modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.22f), MaterialTheme.shapes.small),
                    )
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        EtiquetteEnsuite(MaterialTheme.colorScheme.secondary)
                        Text(
                            titreEtAnnee(prochain),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

/** « 4/12 » : le nombre vu en `titleMedium`, le dénominateur en `labelSmall` ; en or une fois bouclée. */
@Composable
private fun CompteCarte(vus: Int, total: Int, bouclee: Boolean) {
    val couleur = if (bouclee) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurfaceVariant
    Text(
        buildAnnotatedString {
            append(vus.toString())
            withStyle(MaterialTheme.typography.labelSmall.toSpanStyle()) { append("/$total") }
        },
        style = MaterialTheme.typography.titleMedium,
        color = couleur,
        modifier = Modifier.clearAndSetSemantics { contentDescription = "$vus sur $total" },
    )
}

/**
 * La photo d'une personne, ou l'affiche d'une saga : ronde, ou l'initiale sur
 * la même pastille quand TMDB n'en a pas. Jumeau de `Cover` (`ui/Cover.kt`)
 * pour les affiches rectangulaires — un `contentDescription` toujours posé,
 * photo ou non (design §8) ; rien *pendant* le chargement (aucun indicateur,
 * aucun repli tant que la requête est en vol — le commentaire d'ici disait
 * « rien pendant le chargement » sans plus de précision, corrigé par le geste
 * 9 du peaufinage du 23 septembre 2026, qui ajoute le fondu ci-dessous),
 * l'initiale restant le geste de l'absence d'image, pas celui d'une attente.
 *
 * `lisere` (Suivis, rétrospectives et cycles, 25 septembre 2026) : un trait intérieur de 1 dp, or
 * à 35 %, sur la photo comme sur l'initiale — le jumeau rond du liseré à 22 % des affiches.
 */
@Composable
fun Portrait(url: String?, name: String, taille: Dp, modifier: Modifier = Modifier, lisere: Boolean = false) {
    val description = "Photo de $name"
    val trait = if (lisere) {
        Modifier.border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f), CircleShape)
    } else {
        Modifier
    }
    val initiale: @Composable () -> Unit = {
        Box(
            Modifier.size(taille).background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape).then(trait),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (url == null) {
        Box(modifier.clearAndSetSemantics { contentDescription = description }) { initiale() }
    } else {
        val contexte = LocalContext.current
        SubcomposeAsyncImage(
            // `crossfade(200)` (geste 9) : jumeau de `Cover`, la photo apparaît en fondu.
            model = ImageRequest.Builder(contexte).data(url).crossfade(200).build(),
            contentDescription = description,
            contentScale = ContentScale.Crop,
            loading = {},
            error = { initiale() },
            modifier = modifier.size(taille).clip(CircleShape).then(trait),
        )
    }
}
