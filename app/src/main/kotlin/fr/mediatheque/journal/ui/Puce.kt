package fr.mediatheque.journal.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.ui.theme.IconeTabler

/**
 * Une puce : 36 dp de haut, coins 18, bordure `outline` et texte `onSurface` au repos ; bordure et
 * texte or (`secondary`) quand elle est active. `minimumInteractiveComponentSize()` d'abord, qui
 * réserve la cible tactile de 48 dp, la hauteur visible ensuite (même idiome que les puces de
 * réaction du formulaire). `heightIn(min = 36.dp)` plutôt qu'une hauteur fixe : à la taille de
 * police maximale, le libellé ne se fait pas couper.
 *
 * Née dans la rangée de « Mes films · le hall » (`films/FiltresSheets.kt`), partagée depuis les
 * Suivis (rétrospectives et cycles, 25 septembre 2026), où deux puces remplacent les segments.
 */
@Composable
fun Puce(
    libelle: String,
    active: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icone: String? = null,
    description: String? = null,
) {
    val trait = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.outline
    val texte = if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface
    val forme = RoundedCornerShape(18.dp)
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .heightIn(min = 36.dp)
            .border(1.dp, trait, forme)
            .clip(forme)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                selected = active
                if (description != null) contentDescription = description
            }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            libelle,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold),
            color = texte,
        )
        if (icone != null) IconeTabler(icone, null, tint = texte, modifier = Modifier.size(16.dp))
    }
}
