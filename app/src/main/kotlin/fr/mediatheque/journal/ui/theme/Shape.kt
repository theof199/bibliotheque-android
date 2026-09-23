package fr.mediatheque.journal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// docs/design.md §4, retouché par l'habillage « papier et pellicule » du 23 septembre 2026 : les
// coins nets de la maquette (2-4 px) sont arrondis sur demande du propriétaire — `small` pour les
// affiches, `medium` pour les champs, les boutons et les blocs de message, `large` pour les
// cartouches et calques de célébration. Les pastilles et les réactions restent rondes
// (`CircleShape`), dites à l'endroit où elles se dessinent.
val JournalShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
)
