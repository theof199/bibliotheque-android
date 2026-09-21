package fr.mediatheque.journal.ui

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val MONTH_YEAR: DateTimeFormatter = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.FRENCH)

private fun formatLocalDate(date: LocalDate): String {
    val jour = if (date.dayOfMonth == 1) "1er" else date.dayOfMonth.toString()
    return "$jour ${date.format(MONTH_YEAR)}"
}

/**
 * `2026-09-03` → « 3 septembre 2026 », mais `2026-09-01` → « 1er septembre 2026 » : le français
 * ordinalise le premier jour du mois, jamais les suivants (revue de la vague finale, mineur 6).
 */
fun formatDate(iso: String): String = formatLocalDate(LocalDate.parse(iso))

/**
 * Jumeau de `formatDate` pour un instant complet (`2026-09-21T21:30:00.000Z` → « 21 septembre
 * 2026 ») : la date d'écriture d'un paragraphe de la chronique (brief du 21 septembre 2026, « la
 * chronique et les salles »), en UTC — le jour affiché ne dépend pas du fuseau du téléphone.
 */
fun formatDateTime(iso: String): String = formatLocalDate(Instant.parse(iso).atZone(ZoneOffset.UTC).toLocalDate())

/** « Hayao Miyazaki, 2001 » — design §3. */
fun subtitle(director: String?, year: Int?): String =
    listOfNotNull(director?.takeIf { it.isNotBlank() }, year?.toString()).joinToString(", ")

/**
 * `"2026-08"` → « Août 2026 » (décision 2 du brief du 21 septembre 2026, « les dépenses ») : le
 * mois en toutes lettres, en français, capitalisé — seul mot en tête d'une ligne du profil, jumeau
 * de `MONTH_YEAR` avec la capitale en plus.
 */
fun formatMoisAnnee(isoAnneeMois: String): String =
    YearMonth.parse(isoAnneeMois).atDay(1).format(MONTH_YEAR).replaceFirstChar { it.titlecase(Locale.FRENCH) }

/**
 * `12.7` → « 12,7 » (décision 2 du brief du 21 septembre 2026, « les dépenses ») : une décimale,
 * virgule à la française — jamais un point, jamais deux décimales.
 */
fun formatCentimes(centimes: Double): String = String.format(Locale.FRANCE, "%.1f", centimes)
