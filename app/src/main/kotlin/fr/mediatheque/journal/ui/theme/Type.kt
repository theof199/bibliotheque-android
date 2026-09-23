package fr.mediatheque.journal.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import fr.mediatheque.journal.R

/**
 * Manrope, en fichiers statiques (`res/font/`) : pas de police
 * téléchargeable, le téléphone n'a pas à dépendre des services Google pour
 * afficher un titre (docs/design.md §3).
 */
val Manrope = FontFamily(
    Font(R.font.manrope_regular, FontWeight.Normal),
    Font(R.font.manrope_medium, FontWeight.Medium),
    Font(R.font.manrope_semibold, FontWeight.SemiBold),
    Font(R.font.manrope_bold, FontWeight.Bold),
)

/**
 * Fraunces (SIL OFL, `undercasetype/Fraunces`, `fonts/ttf/Fraunces144pt-*`), embarquée comme
 * Manrope — l'habillage « papier et pellicule » du 23 septembre 2026 (docs/design.md §3) : les
 * titres d'écran passent en serif d'affiche, le corps reste Manrope.
 */
val Fraunces = FontFamily(
    Font(R.font.fraunces_regular, FontWeight.Normal),
    Font(R.font.fraunces_semibold, FontWeight.SemiBold),
    Font(R.font.fraunces_bold, FontWeight.Bold),
)

/**
 * Limelight (SIL OFL, `google/fonts` → `ofl/limelight`), un seul style statique — réservée aux
 * titres de célébration (`ui/celebrations/`) : l'année en lettres de marquise, « DANS LA BOÎTE »,
 * les titres du générique de décennie. Ni Manrope ni Fraunces n'y servent : Fraunces reste pour
 * les titres d'écran ordinaires.
 */
val Limelight = FontFamily(Font(R.font.limelight_regular, FontWeight.Normal))

private fun style(size: Int, lineHeight: Int, weight: FontWeight, famille: FontFamily = Manrope) = TextStyle(
    fontFamily = famille,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
    // Centre l'excedent d'interligne au lieu de le poser sous la ligne, comme le font les styles Material par defaut.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

// docs/design.md §3 — six styles, et l'application n'en utilise aucun autre. `displaySmall`,
// `titleLarge` et `titleMedium` passent en Fraunces (23 septembre 2026) : le reste du corps garde
// Manrope, jamais les deux mélangés dans un même style.
val JournalTypography = Typography(
    displaySmall = style(40, 44, FontWeight.Bold, Fraunces),
    titleLarge = style(22, 28, FontWeight.SemiBold, Fraunces),
    titleMedium = style(16, 22, FontWeight.SemiBold, Fraunces),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold),
)
