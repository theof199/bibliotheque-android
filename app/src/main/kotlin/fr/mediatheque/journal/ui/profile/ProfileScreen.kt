package fr.mediatheque.journal.ui.profile

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.R
import fr.mediatheque.journal.api.dto.User
import fr.mediatheque.journal.ui.ErrorBlock
import fr.mediatheque.journal.ui.frise.TamponDecennie
import fr.mediatheque.journal.ui.frise.TamponPasseport
import fr.mediatheque.journal.ui.suivis.SuiviState
import fr.mediatheque.journal.ui.suivis.SuivisViewModel
import fr.mediatheque.journal.ui.suivis.pret

@Composable
fun ProfileScreen(
    user: User,
    vm: ProfileViewModel,
    senscritique: SensCritiqueViewModel,
    bilan: BilanViewModel,
    suivis: SuivisViewModel,
    /** Le passeport du Voyage (brief du 16 septembre 2026, phase 2) : une ligne par décennie bouclée. */
    passeport: List<TamponDecennie>,
    onBack: () -> Unit,
    onFilms: () -> Unit,
    onSensCritique: () -> Unit,
    onSignOut: () -> Unit,
    onOuvrirGenerique: (TamponDecennie) -> Unit,
    onImportLetterboxd: (ByteArray) -> Unit,
    bottomBar: @Composable () -> Unit,
) {
    val ui by vm.ui.collectAsState()
    val senscritiqueUi by senscritique.ui.collectAsState()
    val bilanUi by bilan.ui.collectAsState()
    val suivisUi by suivis.ui.collectAsState()

    // Sélecteur de fichiers système (brief « importer Letterboxd », 16 septembre 2026) : le ZIP de
    // l'export ou `diary.csv` seul, `*/*` en repli pour les lecteurs qui ne déclarent aucun des deux
    // types MIME correctement. `null` (retour sans choix) ne fait rien. La lecture du contenu se
    // fait ici, seul endroit de l'écran qui touche un `Context` — `onImportLetterboxd` ne reçoit que
    // des octets, jamais l'`Uri`.
    val context = LocalContext.current
    val choisirFichier = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        context.contentResolver.openInputStream(uri)?.use { flux -> onImportLetterboxd(flux.readBytes()) }
    }

    // Jumeau du `LaunchedEffect(Unit) { vm.retry() }` ci-dessous : ce `ViewModel` est lui aussi
    // indexé sur l'Activité, clé fixe — sans ce rafraîchissement, revenir du profil depuis l'écran
    // SensCritique (connexion ou déconnexion) laisserait la ligne affichée avant le changement.
    LaunchedEffect(Unit) { senscritique.refresh() }
    val chiffre = MaterialTheme.typography.displaySmall.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurface)
    val mots = MaterialTheme.typography.bodyLarge.toSpanStyle().copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    // Ce `ViewModel` est indexé sur l'Activité, clé fixe (jumeau du piège réglé sur
    // `LoginViewModel`/`SearchViewModel` dans `Root`, revues des tâches 4 et 5) : sans ce
    // rechargement à chaque entrée, la même instance reviendrait avec les chiffres de la
    // première ouverture, en retard d'un film après un enregistrement, une correction ou
    // une suppression faits depuis « Mes films ».
    LaunchedEffect(Unit) { vm.retry() }

    // `Scaffold` plutôt que `safeDrawingPadding()` : son `bottomBar` (la barre du 14 septembre
    // 2026, « Profil » sélectionnée) réserve sa propre place dans le `padding` reçu ci-dessous,
    // comme les insets système que `safeDrawingPadding()` réservait seul avant elle.
    Scaffold(containerColor = MaterialTheme.colorScheme.background, bottomBar = bottomBar) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Retour") }
            }
            // Le reste défile en un seul bloc (jumeau de `FormScreen`) : à la taille de police
            // maximale, les deux chiffres et la liste s'étirent, et sans ce `verticalScroll` le
            // bouton « Se déconnecter » et la mention TMDB sortaient de l'écran (design §8,
            // revue du tour de correction 1).
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(user.pseudo, style = MaterialTheme.typography.titleMedium)
                    when {
                        ui.error != null -> ErrorBlock(ui.error!!.message ?: "", retryable = ui.error!!.retryable, onRetry = vm::retry)
                        ui.total != null -> Text(buildAnnotatedString {
                            withStyle(chiffre) { append("${ui.total}") }
                            withStyle(mots) { append(" films vus, ") }
                            withStyle(chiffre) { append("${ui.thisYear}") }
                            withStyle(mots) { append(" cette année") }
                        })
                        // Ni l'un ni l'autre : le premier chargement n'a pas encore répondu. Rien
                        // ne s'affiche — jamais un zéro, qui serait un compte, pas une absence de
                        // réponse (décision 3 de la tâche 7).
                    }
                    BilanCard(bilanUi.journal, suivisUi.realisateurs, suivisUi.sagas)
                    PasseportCard(passeport, onOuvrirGenerique)
                    ListItem(
                        headlineContent = { Text("Mes films", style = MaterialTheme.typography.titleMedium) },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable(onClick = onFilms),
                    )
                    ListItem(
                        headlineContent = { Text("SensCritique", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = {
                            val pseudo = senscritiqueUi.connectedPseudo
                            Text(if (pseudo != null) "Connecté : $pseudo" else "Non connecté")
                        },
                        trailingContent = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable(onClick = onSensCritique),
                    )
                    ListItem(
                        headlineContent = { Text("Importer Letterboxd", style = MaterialTheme.typography.titleMedium) },
                        supportingContent = { Text("Le fichier d’export de Letterboxd, ZIP ou diary.csv") },
                        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                        modifier = Modifier.clickable {
                            choisirFichier.launch(
                                arrayOf("application/zip", "text/csv", "text/comma-separated-values", "*/*"),
                            )
                        },
                    )
                    TextButton(onClick = onSignOut) { Text("Se déconnecter", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
                // La mention TMDB : une condition de leurs conditions d'utilisation de l'API
                // (§3 « Attribution », https://www.themoviedb.org/api-terms-of-use), pas une
                // politesse. Le logo vient de leur kit de marque
                // (https://www.themoviedb.org/about/logos-attribution, lu le 9 septembre 2026) :
                // à cette date la page n'offre que des logos « blue » en SVG, aucune version dédiée
                // au fond sombre ni aucun PNG — « Alt short (blue) » choisi, converti en PNG,
                // fond transparent. La phrase ci-dessous est celle qu'imposent les conditions à
                // cette même date, « application » remplaçant leur liste de mots-clés entre
                // crochets ([website, program, service, application, product]).
                Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(24.dp))
                    Image(painterResource(R.drawable.tmdb_logo), contentDescription = "TMDB", modifier = Modifier.width(88.dp))
                    Text(
                        "This application uses TMDB and the TMDB APIs but is not endorsed, certified, or otherwise approved by TMDB.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/**
 * La carte « Bilan » (brief du 15 septembre 2026), sous les deux chiffres du
 * haut : sept lignes, chacune apparaissant quand sa donnée arrive — « … »
 * avant, jamais un chiffre provisoire. Les cinq premières viennent du
 * journal (`bilanJournal`, calculé une fois par `BilanViewModel`) ; les deux
 * dernières viennent des deux sources suivies (`bilanSuivi`, **la même
 * fonction pour les deux** : elle ne sait pas si elle compte des
 * réalisateurs ou des sagas), tant qu'elles n'ont pas fini de charger leurs
 * filmographies.
 */
@Composable
private fun BilanCard(journal: BilanJournal?, realisateurs: SuiviState, sagas: SuiviState) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text("Bilan", style = MaterialTheme.typography.titleMedium)

        LigneBilan(if (journal == null) "…" else "${journal.filmsVus} films vus, dont ${journal.filmsVusCetteAnnee} cette année")
        LigneBilan(if (journal == null) "…" else "${journal.seancesEnSalle} séances en salle, dont ${journal.seancesEnSalleCetteAnnee} cette année")
        LigneBilan(
            when {
                journal == null -> "…"
                journal.noteMoyenne == null -> "Aucun film noté"
                else -> "Note moyenne : ${journal.noteMoyenne}/10"
            },
        )
        LigneBilan(
            when {
                journal == null -> "…"
                journal.decennies == null -> "Aucune année connue"
                else -> {
                    val d = journal.decennies
                    "${d.premiere} → ${d.derniere}, ${d.couvertes} décennies sur ${d.total}"
                }
            },
        )
        LigneBilan(
            when {
                journal == null -> "…"
                journal.plusAncien == null -> "Le plus ancien : inconnu"
                else -> "Le plus ancien : ${journal.plusAncien.titre} (${journal.plusAncien.annee})"
            },
        )
        LigneBilan(
            if (!realisateurs.pret()) {
                "…"
            } else {
                val b = bilanSuivi(realisateurs.entites, realisateurs.filmographies)
                "${b.suivis} réalisateurs suivis, dont ${b.termines} terminés"
            },
        )
        LigneBilan(
            if (!sagas.pret()) {
                "…"
            } else {
                val b = bilanSuivi(sagas.entites, sagas.filmographies)
                "${b.suivis} sagas suivies, dont ${b.termines} terminées"
            },
        )
    }
}

/**
 * Le passeport (brief du 16 septembre 2026, phase 2, item 10), sous le Bilan : un tampon par
 * décennie bouclée, qui rejoue son générique de fin. « Aucun tampon encore » tant qu'aucune
 * décennie n'est bouclée — jamais une carte vide, jamais une carte absente : le passeport se voit
 * avant d'être rempli, sinon on ne sait pas qu'il existe.
 */
@Composable
private fun PasseportCard(passeport: List<TamponDecennie>, onOuvrirGenerique: (TamponDecennie) -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer, MaterialTheme.shapes.medium)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Passeport", style = MaterialTheme.typography.titleMedium)
        if (passeport.isEmpty()) {
            LigneBilan("Aucun tampon encore")
        } else {
            passeport.forEach { tampon ->
                TamponPasseport(tampon) { onOuvrirGenerique(tampon) }
            }
        }
    }
}

@Composable
private fun LigneBilan(texte: String) {
    Text(texte, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
