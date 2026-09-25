package fr.mediatheque.journal.ui.fiche

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.ui.theme.ReactionFond
import fr.mediatheque.journal.ui.theme.ReactionTexte

/**
 * Les réactions d'une fiche en puces emoji + mot (« la fiche · trois visages », reprise validée du
 * 25 septembre 2026) : « 😭 Ému » plutôt que l'emoji seul, qui ne se lisait qu'avec l'habitude.
 * Chaque puce penche et descend un peu selon sa place (`inclinaisonPuce`, `decalagePuce`) — le
 * seul désordre voulu de la fiche avec le chiffre de la note, sur un cycle fixe pour que la même
 * fiche se redessine toujours pareil. `en_salle` comprise : la fiche n'a pas de ticket pour la dire
 * autrement.
 *
 * Le lecteur d'écran lit la phrase, jamais l'emoji (design §8). Rien du tout sans réaction : pas
 * de rangée vide qui réserverait sa place.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PucesReactions(cles: List<String>, modifier: Modifier = Modifier) {
    if (cles.isEmpty()) return
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        cles.forEachIndexed { index, cle -> PuceReaction(cle, index) }
    }
}

/**
 * Une puce : 32 dp de haut au moins — `heightIn` plutôt qu'une hauteur fixe, pour qu'une police
 * agrandie par le téléphone ne rogne pas le mot.
 */
@Composable
private fun PuceReaction(cle: String, index: Int) {
    val phrase = Reactions.phrase(cle)
    Box(
        Modifier
            .offset(y = decalagePuce(index).dp)
            .graphicsLayer { rotationZ = inclinaisonPuce(index) }
            .heightIn(min = 32.dp)
            .background(ReactionFond, RoundedCornerShape(16.dp))
            .padding(horizontal = 12.dp)
            .clearAndSetSemantics { contentDescription = phrase },
        contentAlignment = Alignment.Center,
    ) {
        Text(Reactions.label(cle), style = MaterialTheme.typography.labelMedium, color = ReactionTexte)
    }
}
