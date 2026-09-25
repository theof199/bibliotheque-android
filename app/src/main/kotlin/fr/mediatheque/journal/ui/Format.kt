package fr.mediatheque.journal.ui

import java.text.Normalizer
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
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

/**
 * Le temps écoulé depuis `iso` jusqu'à `aujourdHui`, en mots (Suivis, rétrospectives et cycles,
 * 25 septembre 2026) : « aujourd’hui », « hier », « il y a 3 jours » (jusqu'à 6), « il y a
 * 2 semaines » (1 à 4), puis « il y a 5 mois » (au moins 1). `iso` est une date (`2026-09-22`) ou un
 * instant complet (`2026-09-15T18:22:41.000Z`) : seuls ses dix premiers caractères comptent, le jour
 * tel que le back l'a écrit, sans conversion de fuseau. Une date à venir se lit « aujourd’hui ».
 */
fun formatRelatif(iso: String, aujourdHui: LocalDate): String {
    val date = LocalDate.parse(iso.take(10))
    val jours = ChronoUnit.DAYS.between(date, aujourdHui)
    return when {
        jours <= 0 -> "aujourd’hui"
        jours == 1L -> "hier"
        jours < 7 -> "il y a $jours jours"
        jours < 35 -> (jours / 7).let { if (it == 1L) "il y a 1 semaine" else "il y a $it semaines" }
        else -> "il y a ${ChronoUnit.MONTHS.between(date, aujourdHui).coerceAtLeast(1)} mois"
    }
}

/** « Hayao Miyazaki, 2001 » — design §3. */
fun subtitle(director: String?, year: Int?): String =
    listOfNotNull(director?.takeIf { it.isNotBlank() }, year?.toString()).joinToString(", ")

/**
 * Le titre original à afficher sous le titre, ou `null` s'il ne doit pas l'être (revue du
 * 24 septembre 2026, point 4, « titres sans doublon ») : `null` lui-même, ou identique au titre
 * (même chaîne exacte — un film sans titre distinct porte souvent le même `title` et
 * `original_title` côté back). Fonction pure, extraite de la même condition écrite en double dans
 * `FicheVoyageScreen` et `FicheFilmScreen` avant cette revue, testée ici une fois pour les deux.
 */
fun titreOriginalAffiche(titre: String, titreOriginal: String?): String? =
    titreOriginal?.takeIf { it != titre }

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

/**
 * Le texte tel qu'on le compare : décomposé (NFD), marques diacritiques retirées, en minuscules,
 * sans espaces aux bords. « Léon » et « leon », « MIYAZAKI » et « miyazaki » deviennent la même
 * chaîne : on tape sur un téléphone, sans chercher l'accent ni la majuscule.
 *
 * Sortie de `FilmsEtats.kt` (« Au ciné · le guichet », 25 septembre 2026) : la recherche de Mes
 * films et le sceau « réalisateur suivi » d'Au ciné (`AuCineEtats.kt`) comparent des noms de la
 * même façon, un seul exemplaire pour les deux.
 */
fun normaliser(s: String): String =
    Normalizer.normalize(s, Normalizer.Form.NFD).replace(DIACRITIQUES, "").lowercase().trim()

private val DIACRITIQUES = Regex("\\p{M}+")
