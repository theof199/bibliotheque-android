package fr.mediatheque.journal.ui.films

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.formatDate
import fr.mediatheque.journal.ui.lisereOr
import fr.mediatheque.journal.ui.subtitle
import fr.mediatheque.journal.ui.theme.IconeTabler
import kotlin.math.roundToInt

/** Les deux positions d'une ligne : au repos, ou glissée à gauche sur ses deux actions. */
private enum class PositionLigne { Ferme, Ouvert }

/** Une action fait 88 dp, les deux ensemble 176 : l'ancre `Ouvert` est à −176 dp. */
private val LARGEUR_ACTION = 88.dp

/**
 * Une ligne de « Mes films · le hall » (spec de Léon, décisions du propriétaire du 24 septembre
 * 2026), reprise telle quelle, glissement compris, par « Tes séances » d'« Au ciné » depuis « le
 * guichet » (décision du propriétaire du 25 septembre 2026), où elle remplace l'ancienne ligne.
 *
 * Glissée vers la gauche (`AnchoredDraggable`, ancres Fermé = 0 et Ouvert = −176 dp), elle
 * découvre « Corriger » et « Supprimer ». Une seule ligne ouverte à la fois : l'écran tient
 * l'identifiant de la ligne ouverte et le redescend ici par `ouverte` ; une ligne qui n'est plus
 * celle-là se referme d'elle-même. Un tap sur une ligne ouverte la referme au lieu d'ouvrir le
 * film (`onClick` n'est appelé que sur une ligne fermée).
 *
 * Accessibilité : les deux actions restent dans l'arbre sémantique (atteignables au lecteur
 * d'écran sans glisser), et toucher la ligne ouvre la fiche de l'entrée (« la fiche · trois
 * visages », 25 septembre 2026), d'où « Corriger » mène au formulaire et à son propre
 * « Supprimer » — aucune `customActions` n'est nécessaire.
 */
@Composable
fun LigneFilm(
    item: JournalItem,
    ouverte: Boolean,
    onOuvrir: () -> Unit,
    onFermer: () -> Unit,
    onClick: () -> Unit,
    onCorriger: () -> Unit,
    onSupprimer: () -> Unit,
    /** Faux le temps d'un chargement de page ou d'une suppression : voir `FilmsScreen`. */
    supprimerActif: Boolean,
    modifier: Modifier = Modifier,
    coverModifier: Modifier = Modifier,
) {
    val largeurOuverte = with(LocalDensity.current) { (LARGEUR_ACTION * 2).toPx() }
    val etat = remember(largeurOuverte) {
        AnchoredDraggableState(
            initialValue = if (ouverte) PositionLigne.Ouvert else PositionLigne.Ferme,
            anchors = DraggableAnchors {
                PositionLigne.Ferme at 0f
                PositionLigne.Ouvert at -largeurOuverte
            },
        )
    }
    val haptique = LocalHapticFeedback.current
    val ouverteActuelle by rememberUpdatedState(ouverte)
    val onOuvrirActuel by rememberUpdatedState(onOuvrir)
    val onFermerActuel by rememberUpdatedState(onFermer)

    // Une autre ligne s'est ouverte (ou l'écran a tout refermé) : celle-ci revient au repos.
    LaunchedEffect(ouverte) {
        if (!ouverte) etat.animateTo(PositionLigne.Ferme)
    }
    // L'arrivée sur une ancre, après un glissement : Ouvert prévient l'écran (qui refermera
    // l'ancienne ligne ouverte) avec l'haptique `SegmentTick` ; Fermé à la main libère la place.
    // La première valeur (celle de départ) n'est pas une arrivée : ni haptique, ni rappel.
    LaunchedEffect(etat) {
        var precedente = etat.settledValue
        snapshotFlow { etat.settledValue }.collect { position ->
            if (position == precedente) return@collect
            precedente = position
            when (position) {
                PositionLigne.Ouvert -> {
                    haptique.performHapticFeedback(HapticFeedbackType.SegmentTick)
                    onOuvrirActuel()
                }
                PositionLigne.Ferme -> if (ouverteActuelle) onFermerActuel()
            }
        }
    }
    val deplacee by remember { derivedStateOf { etat.offset < 0f } }

    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth()) {
            // Les actions, derrière la ligne, calées à droite. `matchParentSize()` plutôt qu'une
            // hauteur intrinsèque : l'affiche est un `SubcomposeAsyncImage`, qui refuse les
            // mesures intrinsèques.
            Row(Modifier.matchParentSize(), horizontalArrangement = Arrangement.End) {
                ActionLigne(
                    libelle = "Corriger",
                    icone = "pencil",
                    couleur = MaterialTheme.colorScheme.secondary,
                    fond = MaterialTheme.colorScheme.surfaceContainerHigh,
                    actif = true,
                    onClick = { onFermer(); onCorriger() },
                )
                ActionLigne(
                    libelle = "Supprimer",
                    icone = "trash",
                    couleur = MaterialTheme.colorScheme.error,
                    fond = MaterialTheme.colorScheme.surfaceContainer,
                    actif = supprimerActif,
                    onClick = onSupprimer,
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .offset { IntOffset(etat.offset.takeUnless { it.isNaN() }?.roundToInt() ?: 0, 0) }
                    .anchoredDraggable(etat, Orientation.Horizontal)
                    // Ombre à droite de la ligne déplacée, là où elle recouvre encore les actions ;
                    // aucune au repos, où elle soulignerait chaque ligne pour rien.
                    .then(if (deplacee) Modifier.shadow(8.dp, clip = false) else Modifier)
                    .background(MaterialTheme.colorScheme.background)
                    .clickable { if (etat.currentValue == PositionLigne.Ouvert || ouverte) onFermer() else onClick() },
            ) {
                ContenuLigne(item, coverModifier)
            }
        }
        HorizontalDivider(
            thickness = 1.dp,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun ContenuLigne(item: JournalItem, coverModifier: Modifier) {
    val secondaire = MaterialTheme.colorScheme.onSurfaceVariant
    // Le thème n'a que six styles (design §3) : 13/18 et 12/16 sont des `copy()` locaux de
    // `bodyMedium`, voulus ; les faire entrer dans la typographie du thème viendra plus tard.
    val style13 = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp)
    val style12 = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp, lineHeight = 16.sp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(
            item.media.cover_url,
            item.media.title,
            56.dp,
            84.dp,
            // Liseré intérieur or à 22 % (gabarit adopté par le propriétaire le 24 septembre
            // 2026) : un trait de 1 dp, jamais un cadre orné.
            modifier = coverModifier.lisereOr(),
        )
        Column(Modifier.weight(1f)) {
            Text(item.media.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sousTitre = subtitle(item.media.director, item.media.year)
            if (sousTitre.isNotBlank()) Text(sousTitre, style = style13, color = secondaire)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatDate(item.entry.finished_at), style = MaterialTheme.typography.bodyMedium, color = secondaire)
                if (auCinema(item)) {
                    Spacer(Modifier.width(4.dp))
                    IconeTabler("ticket", "Vu au cinéma", tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(14.dp))
                }
            }
            val mots = motsReactions(item)
            if (mots.isNotEmpty()) Text(mots, style = style12, color = secondaire)
        }
        // Le cercle or de la note, recopié de celui de l'ancienne ligne de « Tes séances »
        // (retirée avec « le guichet », 25 septembre 2026) : mêmes 26 dp, même trait, même chiffre.
        item.entry.rating?.let { note ->
            Box(
                Modifier
                    .size(26.dp)
                    .border(1.dp, MaterialTheme.colorScheme.secondary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$note", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
    }
}

/** Une des deux actions découvertes par le glissement : 88 dp de large, toute la hauteur. */
@Composable
private fun ActionLigne(libelle: String, icone: String, couleur: Color, fond: Color, actif: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .width(LARGEUR_ACTION)
            .fillMaxHeight()
            .background(fond)
            .clickable(enabled = actif, role = Role.Button, onClick = onClick)
            // Désactivée : l'opacité standard de Material (38 %, design §6).
            .alpha(if (actif) 1f else 0.38f),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        IconeTabler(icone, null, tint = couleur)
        Text(libelle, style = MaterialTheme.typography.labelLarge, color = couleur)
    }
}
