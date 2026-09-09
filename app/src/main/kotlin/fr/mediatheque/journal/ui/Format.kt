package fr.mediatheque.journal.ui

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LONG_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH)

/** `2026-09-03` → « 3 septembre 2026 ». */
fun formatDate(iso: String): String = LocalDate.parse(iso).format(LONG_DATE)

/** « Hayao Miyazaki, 2001 » — design §3. */
fun subtitle(director: String?, year: Int?): String =
    listOfNotNull(director?.takeIf { it.isNotBlank() }, year?.toString()).joinToString(", ")
