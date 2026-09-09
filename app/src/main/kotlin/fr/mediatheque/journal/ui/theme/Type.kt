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

private fun style(size: Int, lineHeight: Int, weight: FontWeight) = TextStyle(
    fontFamily = Manrope,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = 0.sp,
    // Centre l'excedent d'interligne au lieu de le poser sous la ligne, comme le font les styles Material par defaut.
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

// docs/design.md §3 — six styles, et l'application n'en utilise aucun autre.
val JournalTypography = Typography(
    displaySmall = style(40, 44, FontWeight.Bold),
    titleLarge = style(22, 28, FontWeight.SemiBold),
    titleMedium = style(16, 22, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    labelLarge = style(15, 20, FontWeight.SemiBold),
)
