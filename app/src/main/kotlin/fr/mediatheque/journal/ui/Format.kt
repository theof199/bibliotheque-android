package fr.mediatheque.journal.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

/**
 * `2026-09-03` → « 3 septembre 2026 », mais `2026-09-01` → « 1er septembre 2026 » : le français
 * ordinalise le premier jour du mois, jamais les suivants (revue de la vague finale, mineur 6).
 */
fun formatDate(iso: String): String {
    val date = LocalDate.parse(iso)
    val jour = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()
    return "$jour ${date.format(MONTH_YEAR)}"
}

/** « Hayao Miyazaki, 2001 » — design §3. */
fun subtitle(director: String?, year: Int?): String =
    listOfNotNull(director?.takeIf { it.isNotBlank() }, year?.toString()).joinToString(", ")
