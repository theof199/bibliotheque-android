package fr.mediatheque.journal.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

// docs/design.md §4 — `small` pour les affiches, `medium` pour les champs, les
// boutons et les blocs de message. Les pastilles et les réactions sont rondes
// (`CircleShape`), dites à l'endroit où elles se dessinent.
val JournalShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
)
