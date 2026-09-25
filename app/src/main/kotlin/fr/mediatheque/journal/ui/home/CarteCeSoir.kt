package fr.mediatheque.journal.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import fr.mediatheque.journal.ui.Cover
import fr.mediatheque.journal.ui.frise.SeancePriseUi
import fr.mediatheque.journal.ui.lisereOr

/**
 * « Ce soir » en carte (« Accueil · la porte d'entrée », planche de Léon validée le 25 septembre
 * 2026) : avant, une `LigneEnsuite` comme les pages du carrousel, que rien ne distinguait d'une
 * suggestion. Une séance prise est un rendez-vous, pas une idée : elle a désormais sa carte, fond
 * `surfaceContainer`, coins de 16, liseré intérieur or à 28 %.
 *
 * La planche montre aussi l'affiche du court à côté du long, la durée du court, le nom de la salle
 * et l'année du long ; le back (`SeancePriseVoyage`, 25 septembre 2026) ne porte aucun des quatre.
 * La carte montre donc l'affiche du long seule, « + Titre · court » sans durée, et « VOYAGE 1962 »
 * (l'année du Voyage où la séance est prise) à la place de la salle.
 */
@Composable
fun CarteCeSoir(seance: SeancePriseUi, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val forme = RoundedCornerShape(16.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(forme)
            .background(MaterialTheme.colorScheme.surfaceContainer, forme)
            .border(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f), forme)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(seance.longCoverUrl, seance.longTitre, 64.dp, 96.dp, modifier = Modifier.lisereOr())
        Column(Modifier.padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                "CE SOIR",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.2.em),
                color = MaterialTheme.colorScheme.secondary,
            )
            Text(
                seance.longTitre,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            seance.courtTitre?.let { LigneDuCourt(it) }
            Text(
                "VOYAGE ${seance.annee}",
                style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.16.em),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** « + Un chien andalou · court » : le « + » en or, qui accroche le court au long au-dessus. */
@Composable
private fun LigneDuCourt(titreCourt: String) {
    val ligne = ligneCourt(titreCourt)
    Text(
        buildAnnotatedString {
            withStyle(SpanStyle(color = MaterialTheme.colorScheme.secondary)) { append(ligne.take(1)) }
            append(ligne.drop(1))
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
