package fr.mediatheque.journal.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.CadrePapier
import fr.mediatheque.journal.ui.theme.Fraunces
import fr.mediatheque.journal.ui.theme.IconeTabler
import fr.mediatheque.journal.ui.theme.PapierJauni
import fr.mediatheque.journal.ui.theme.TextePapier
import fr.mediatheque.journal.ui.theme.animationsReduites
import kotlinx.coroutines.delay
import java.util.concurrent.atomic.AtomicInteger

/**
 * Ce qu'une feuille de lecture montre (décision 6 du brief du 24 septembre 2026, « le voyage
 * revu ») : le texte une fois là, un chargement tant qu'il n'y est pas encore, ou le message du
 * back avec son bouton « Réessayer » quand il en a un.
 */
sealed interface EtatFeuilleDeLecture {
    data object Chargement : EtatFeuilleDeLecture
    data class Texte(val texte: String) : EtatFeuilleDeLecture
    data class Erreur(val message: String, val retryable: Boolean) : EtatFeuilleDeLecture
}

/**
 * Le numéro du pied de page (« FEUILLE N° 41 ») : un compteur des feuilles ouvertes depuis le
 * lancement de l'appli. Décision du propriétaire du 25 septembre 2026 : aucune feuille n'a
 * d'identifiant stable à montrer (le carton ne remonte pas le sien à l'écran, l'ouverture et le
 * générique n'en ont pas, celui d'une salle est opaque) ; le numéro est donc décoratif, il repart
 * de 1 à chaque lancement et ne désigne rien d'autre que l'ordre d'ouverture.
 */
private val compteurFeuilles = AtomicInteger(0)

/** Le texte lu (« la feuille de lecture · le chroniqueur ») : Fraunces 16 / 26 sp, plus de Manrope. */
@Composable
private fun styleCorps(): TextStyle =
    MaterialTheme.typography.bodyLarge.copy(fontFamily = Fraunces, fontSize = 16.sp, lineHeight = 26.sp)

/**
 * Le composant unique des quatre textes du chroniqueur (décision 6 du brief du 24 septembre 2026,
 * « le voyage revu », reprise par « la feuille de lecture · le chroniqueur », planche de Léon du
 * 25 septembre 2026) : le carton d'un film (`espece` « Le film »), le contexte d'une salle
 * (« Salle »), l'ouverture d'une année (« Ouverture ») et le générique de fin (« Générique »).
 * Une feuille modale à 92 % de l'écran (Material 3), sur le papier jauni du ticket, fermable par
 * geste ou par la croix, seule action.
 *
 * En-tête : l'espèce en petites capitales espacées au-dessus du titre (Fraunces `titleLarge`),
 * puis `sousTitre` s'il y en a un (l'année, et la salle d'où l'on vient pour un film du Voyage),
 * sous un filet `CadrePapier`. `titre` reste affiché pendant le chargement (le titre d'un film,
 * connu avant que son carton ne le soit). Le corps, en Fraunces 16 / 26 sp coupé en paragraphes
 * (`paragraphes`), se lit comme une page ; `onRetry` n'est appelé que sur une erreur `retryable`.
 * Pied de page hors du défilement, toujours visible : « LE CHRONIQUEUR » et le numéro de la
 * feuille (`compteurFeuilles`).
 *
 * La machine à écrire remplace la bobine Lottie, qui tournait sans rien dire : tant que le texte
 * n'est pas là, « Le chroniqueur écrit… » se tape lettre à lettre, s'efface et recommence
 * (`MachineAEcrire`) ; le texte arrivé se tape à son tour — ses deux premières lignes à peu près
 * (`partieTapee`), le reste posé d'un coup (`TexteTape`), pour qu'on voie la main sans attendre la
 * page. Un texte déjà là à l'ouverture (l'ouverture d'une année, le générique en boîte) fait la
 * même entrée en scène. Quand le téléphone a coupé les animations (`animationsReduites`), tout est
 * posé d'un coup, sans curseur.
 *
 * Pas de perforations ni de pointillés latéraux : la planche les dit « inchangés », et la feuille
 * n'en a jamais porté — ils restent au ticket (`TicketVoyage`).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeuilleDeLecture(
    espece: String,
    titre: String,
    sousTitre: String?,
    etat: EtatFeuilleDeLecture,
    onDismiss: () -> Unit,
    onRetry: () -> Unit = {},
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // `rememberSaveable` : une rotation garde le numéro de la feuille ouverte ; la refermer puis la
    // rouvrir en tire un nouveau, puisque les appelants ne la composent que tant qu'elle est ouverte.
    val numero = rememberSaveable { compteurFeuilles.incrementAndGet() }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = PapierJauni,
        modifier = Modifier.fillMaxHeight(0.92f),
    ) {
        // Pas de `navigationBarsPadding` au pied : la feuille de Material 3 (1.4) décale déjà son
        // contenu des barres système du bas (`contentWindowInsets` par défaut).
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                EnTeteFeuille(espece, titre, sousTitre, onDismiss)
                HorizontalDivider(thickness = 1.dp, color = CadrePapier.copy(alpha = 0.6f))
                when (etat) {
                    EtatFeuilleDeLecture.Chargement -> MachineAEcrire(PHRASE_ATTENTE)
                    is EtatFeuilleDeLecture.Texte -> TexteTape(etat.texte)
                    is EtatFeuilleDeLecture.Erreur -> ErreurSurPapier(etat, onRetry)
                }
            }
            PiedFeuille(numero)
        }
    }
}

@Composable
private fun EnTeteFeuille(espece: String, titre: String, sousTitre: String?, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                espece.uppercase(),
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.em),
                color = CadrePapier,
            )
            Text(titre, style = MaterialTheme.typography.titleLarge, color = TextePapier)
            sousTitre?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = CadrePapier) }
        }
        IconButton(onClick = onDismiss) { IconeTabler("x", "Fermer", tint = TextePapier) }
    }
}

@Composable
private fun PiedFeuille(numero: Int) {
    val style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, letterSpacing = 0.2.em)
    Row(
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("LE CHRONIQUEUR", style = style, color = CadrePapier)
        Text("FEUILLE N° $numero", style = style, color = CadrePapier)
    }
}

/**
 * La phrase d'attente tapée à la machine : une lettre toutes les `CADENCE_ATTENTE_MS`, une pause
 * de `PAUSE_ATTENTE_MS` phrase finie, puis effacée d'un coup et retapée — jusqu'à ce que le texte
 * arrive et que la feuille quitte cet état, ce qui annule la boucle avec sa composition.
 */
@Composable
private fun MachineAEcrire(phrase: String) {
    val reduites = remember { animationsReduites() }
    if (reduites) {
        Text(phrase, style = styleCorps(), color = TextePapier)
        return
    }
    var lettres by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        while (true) {
            for (index in 1..phrase.length) {
                delay(CADENCE_ATTENTE_MS)
                lettres = index
            }
            delay(PAUSE_ATTENTE_MS)
            lettres = 0
        }
    }
    TexteAvecCurseur(tapes(phrase, lettres), curseur = true)
}

/**
 * Le texte arrivé : `partieTapee(texte)` caractères tapés un à un toutes les `CADENCE_TEXTE_MS`,
 * puis le reste d'un coup, le curseur retiré avec. Une recomposition toutes les 20 ms pendant
 * quelque 90 pas, moins de deux secondes : rien que la feuille ne supporte sans peine, et elle
 * cesse dès que le texte est entier.
 */
@Composable
private fun TexteTape(texte: String) {
    val reduites = remember { animationsReduites() }
    val cible = partieTapee(texte)
    var lettres by remember(texte) { mutableIntStateOf(if (reduites) texte.length else 0) }
    LaunchedEffect(texte) {
        if (reduites) return@LaunchedEffect
        for (index in 1..cible) {
            delay(CADENCE_TEXTE_MS)
            lettres = index
        }
        lettres = texte.length
    }
    val enCours = lettres < texte.length
    // Tant que rien n'est tapé, un paragraphe vide garde une place au curseur.
    val visibles = paragraphes(tapes(texte, lettres)).ifEmpty { if (enCours) listOf("") else emptyList() }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        visibles.forEachIndexed { index, paragraphe ->
            TexteAvecCurseur(paragraphe, curseur = enCours && index == visibles.lastIndex)
        }
    }
}

/**
 * Un paragraphe du corps, le curseur de la machine (2 × 18 dp, `TextePapier`, clignotant toutes
 * les `CLIGNOTEMENT_MS`) collé à sa dernière lettre. Posé en contenu en ligne plutôt qu'à côté du
 * texte dans une rangée : un paragraphe sur plusieurs lignes le garderait sinon au bord droit,
 * loin de la lettre qu'on vient de taper.
 */
@Composable
private fun TexteAvecCurseur(texte: String, curseur: Boolean) {
    if (!curseur) {
        Text(texte, style = styleCorps(), color = TextePapier)
        return
    }
    var allume by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(CLIGNOTEMENT_MS)
            allume = !allume
        }
    }
    val largeur = with(LocalDensity.current) { 2.dp.toSp() }
    val hauteur = with(LocalDensity.current) { 18.dp.toSp() }
    val contenu = mapOf(
        CLE_CURSEUR to InlineTextContent(Placeholder(largeur, hauteur, PlaceholderVerticalAlign.TextBottom)) {
            Box(Modifier.fillMaxSize().background(if (allume) TextePapier else Color.Transparent))
        },
    )
    Text(
        buildAnnotatedString {
            append(texte)
            appendInlineContent(CLE_CURSEUR, "▍")
        },
        style = styleCorps(),
        color = TextePapier,
        inlineContent = contenu,
    )
}

private const val CLE_CURSEUR = "curseur"

/** Le message du back sur le papier même, et « Réessayer » quand une nouvelle demande peut aboutir. */
@Composable
private fun ErreurSurPapier(etat: EtatFeuilleDeLecture.Erreur, onRetry: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(CadrePapier.copy(alpha = 0.12f), MaterialTheme.shapes.medium)
            .padding(16.dp),
    ) {
        Text(etat.message, style = MaterialTheme.typography.bodyLarge, color = TextePapier)
        if (etat.retryable) {
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = TextePapier),
                modifier = Modifier.align(Alignment.End),
            ) { Text("Réessayer") }
        }
    }
}
