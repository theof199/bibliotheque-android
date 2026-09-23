package fr.mediatheque.journal.ui.theme

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

private val JournalColors = darkColorScheme(
    background = Fond,
    surface = Fond,
    surfaceContainer = Surface1,
    surfaceContainerHigh = Surface2,
    onSurface = Texte,
    onBackground = Texte,
    onSurfaceVariant = TexteSecondaire,
    outline = Filet,
    primary = Corail,
    onPrimary = Fond,
    // « Papier et pellicule » (23 septembre 2026) : l'or devient un jeton du thème — cadres, notes,
    // filets s'y réfèrent par `MaterialTheme.colorScheme.secondary`/`.tertiary`, jamais une couleur
    // en dur. `onSecondary`/`onTertiary` reprennent `TextePapier`, déjà pensé pour un texte sombre
    // sur un fond clair — sans couleur nouvelle, comme le reste de ce bloc.
    secondary = Or,
    onSecondary = TextePapier,
    tertiary = Or,
    onTertiary = TextePapier,
    secondaryContainer = ReactionFond,
    onSecondaryContainer = ReactionTexte,
    error = Ambre,
    // Le design (§2) ne liste pas ces cinq jetons, laissés au défaut Material — un violet ou un
    // gris clair qui jure sur le fond noir. Un `TextField` rempli lit `surfaceContainerHighest`,
    // un `Snackbar` lit `inverseSurface`/`inverseOnSurface`/`inversePrimary`, un
    // `HorizontalDivider` lit `outlineVariant` : on les pose depuis la palette déjà retenue, sans
    // couleur nouvelle, pour que le premier écran qui les touche n'en découvre pas la couleur.
    surfaceContainerHighest = Surface2,
    inverseSurface = Surface2,
    inverseOnSurface = Texte,
    inversePrimary = Corail,
    outlineVariant = Filet,
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
    ) {
        // `MaterialTheme` seul ne pose pas `LocalContentColor` : sans ce
        // `Surface`, un `Text` hérite du noir par défaut de Material 3 sur
        // le fond noir du design, invisible. Posé ici, pas dans un écran :
        // les tâches suivantes en héritent toutes.
        Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
            // Le grain (habillage « papier et pellicule », 23 septembre 2026) : posé une fois ici,
            // sous tout le contenu, plutôt que dans chaque écran.
            Box(Modifier.fillMaxSize()) {
                Grain(Modifier.fillMaxSize())
                content()
            }
        }
    }
}

/**
 * Un grain très léger sur le fond — la maquette pose un `radial-gradient` de points blancs à 2,5 %
 * d'opacité, tuilé tous les 3 dp. Compose n'a pas de `background-image` répété : la tuile (un seul
 * point, sur un petit `ImageBitmap`) est dessinée une fois dans `drawWithCache` — recalculée
 * seulement quand la taille de l'écran change, jamais à chaque frame — puis répétée par un
 * `ShaderBrush` en `TileMode.Repeated`. Aucune image embarquée : la tuile est procédurale.
 */
@Composable
fun Grain(modifier: Modifier = Modifier) {
    Box(
        modifier.drawWithCache {
            val cote = 3.dp.toPx().roundToInt().coerceAtLeast(1)
            val tuile = ImageBitmap(cote, cote)
            val centre = cote / 2f
            Canvas(tuile).drawCircle(
                center = Offset(centre, centre),
                radius = centre * 0.6f,
                paint = Paint().apply { color = Color.White.copy(alpha = 0.025f) },
            )
            val pinceau = ShaderBrush(ImageShader(tuile, TileMode.Repeated, TileMode.Repeated))
            onDrawBehind {
                drawRect(pinceau)
            }
        },
    )
}
