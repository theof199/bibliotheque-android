package fr.mediatheque.journal.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val JournalColors = darkColorScheme(
    background = Noir,
    surface = Noir,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    onSurface = Texte,
    onBackground = Texte,
    onSurfaceVariant = TexteSecondaire,
    outline = Filet,
    primary = Corail,
    onPrimary = Noir,
    secondaryContainer = ReactionFond,
    onSecondaryContainer = ReactionTexte,
    error = Ambre,
)

/**
 * Sombre uniquement, palette fixe : pas de `dynamicDarkColorScheme`, pas de
 * branche claire, pas d'`isSystemInDarkTheme()`. C'est le §1 du design.
 */
@Composable
fun JournalTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = JournalColors,
        typography = JournalTypography,
        shapes = JournalShapes,
        content = content,
    )
}
