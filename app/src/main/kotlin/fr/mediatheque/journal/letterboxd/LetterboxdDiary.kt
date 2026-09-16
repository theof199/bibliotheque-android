package fr.mediatheque.journal.letterboxd

import java.io.ByteArrayInputStream
import java.io.IOException
import java.time.LocalDate
import java.util.zip.ZipInputStream

/**
 * Lecture de l'export Letterboxd (brief du 16 septembre 2026), en JVM pur :
 * ni `Context` ni bibliothèque, pour rester testable sans Android ni réseau —
 * `ui/profile/LetterboxdImportViewModel.kt` est seul à en dépendre.
 *
 * Le CSV et la règle de date/note sont un miroir de `src/csv.ts` et de
 * `routes/carnet.ts` (`POST /me/journal/import/letterboxd`) côté back : la
 * même conversion, pour que le pré-remplissage d'un candidat choisi dans le
 * rapport corresponde exactement à ce que le back a lu sur cette ligne — lui
 * seul décide de l'import, l'appli ne fait ici que relire ce qu'elle vient
 * d'envoyer.
 */

private val ZIP_SIGNATURE = byteArrayOf(0x50, 0x4B, 0x03, 0x04) // "PK", l'en-tête d'entrée locale d'un ZIP

/** Vrai si les octets commencent par la signature locale d'un ZIP. Letterboxd exporte soit ce ZIP, soit `diary.csv` seul. */
fun looksLikeZip(bytes: ByteArray): Boolean =
    bytes.size >= ZIP_SIGNATURE.size && ZIP_SIGNATURE.indices.all { bytes[it] == ZIP_SIGNATURE[it] }

/** Levée par `extractDiaryCsv` quand le ZIP est lisible mais ne contient pas `diary.csv`. */
class DiaryCsvMissingException : Exception("Ce ZIP ne contient pas diary.csv")

/**
 * `diary.csv`, où qu'il vive dans l'arborescence du ZIP (Letterboxd l'exporte
 * parfois sous un dossier). Lève `IOException` (`java.util.zip.ZipException`
 * comprise) si le flux n'est pas un ZIP lisible, `DiaryCsvMissingException`
 * s'il l'est mais sans ce fichier.
 */
@Throws(IOException::class)
fun extractDiaryCsv(zipBytes: ByteArray): String {
    ZipInputStream(ByteArrayInputStream(zipBytes)).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.substringAfterLast('/') == "diary.csv") {
                return zip.readBytes().toString(Charsets.UTF_8)
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }
    throw DiaryCsvMissingException()
}

/** Le CSV à envoyer au back : extrait du ZIP si c'en est un (signature `PK`), lu tel quel sinon. */
@Throws(IOException::class)
fun diaryCsvFrom(bytes: ByteArray): String =
    if (looksLikeZip(bytes)) extractDiaryCsv(bytes) else bytes.toString(Charsets.UTF_8)

// -----------------------------------------------------------------------------
// Le CSV lui-même — lecteur minimal, miroir de `apps/api/src/csv.ts`
// -----------------------------------------------------------------------------

/** Guillemets et virgules dans un champ, guillemet échappé en le doublant (RFC 4180). */
fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    val n = text.length

    fun endField() {
        row.add(field.toString())
        field.clear()
    }
    fun endRow() {
        endField()
        rows.add(row)
        row = mutableListOf()
    }

    while (i < n) {
        val c = text[i]
        when {
            inQuotes && c == '"' && i + 1 < n && text[i + 1] == '"' -> {
                field.append('"')
                i++
            }
            inQuotes && c == '"' -> inQuotes = false
            inQuotes -> field.append(c)
            c == '"' -> inQuotes = true
            c == ',' -> endField()
            c == '\r' -> Unit // toujours suivi de \n (CRLF) dans un CSV bien forme
            c == '\n' -> endRow()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()

    // Une ligne vide (fin de fichier, ligne blanche) ne porte aucune donnee.
    return rows.filterNot { it.size == 1 && it[0].isEmpty() }
}

private val LETTERBOXD_HEADERS = listOf("Date", "Name", "Year", "Letterboxd URI", "Rating", "Rewatch", "Tags", "Watched Date")

/** Une ligne de `diary.csv`, telle que le back la lit : date deja resolue (Watched Date sinon Date), note deja convertie. */
data class DiaryRow(val ligne: Int, val name: String, val year: Int?, val date: LocalDate?, val rating: Int?)

/** `Rating` : etoiles Letterboxd (0.5 a 5, parfois vide) -> note sur 10. Miroir de `noteLetterboxd` cote back. */
private fun noteLetterboxd(brut: String): Int? {
    val valeur = brut.trim()
    if (valeur.isEmpty()) return null
    val etoiles = valeur.toDoubleOrNull() ?: return null
    return Math.round(etoiles * 2).toInt()
}

private val DATE_PATTERN = Regex("""^\d{4}-\d{2}-\d{2}$""")

/** `AAAA-MM-JJ` bien forme, sinon nul. Miroir de `dateLetterboxd` cote back. */
private fun dateLetterboxd(brut: String): LocalDate? {
    val valeur = brut.trim()
    if (!DATE_PATTERN.matches(valeur)) return null
    return runCatching { LocalDate.parse(valeur) }.getOrNull()
}

/**
 * Les lignes de `diary.csv`, indexees par leur numero de ligne (l'en-tete
 * vaut 1, comme `ligne` dans la reponse du back) : sert au pre-remplissage
 * d'un candidat choisi dans le rapport. Carte vide si l'en-tete n'est pas
 * celui de `diary.csv` — rien a indexer, le back aura de toute facon repondu
 * 400.
 */
fun parseDiaryRows(csv: String): Map<Int, DiaryRow> {
    val rows = parseCsv(csv)
    val entete = rows.firstOrNull() ?: return emptyMap()
    val index = entete.withIndex().associate { (i, nom) -> nom to i }
    if (!LETTERBOXD_HEADERS.all { index.containsKey(it) }) return emptyMap()

    fun champ(row: List<String>, nom: String): String = index[nom]?.let { row.getOrNull(it) } ?: ""

    return rows.drop(1).mapIndexed { i, row ->
        val ligne = i + 2 // l'en-tete est la ligne 1, la premiere donnee la ligne 2
        val name = champ(row, "Name").trim()
        val yearBrut = champ(row, "Year").trim()
        val year = if (yearBrut.length == 4) yearBrut.toIntOrNull() else null
        val date = dateLetterboxd(champ(row, "Watched Date")) ?: dateLetterboxd(champ(row, "Date"))
        val rating = noteLetterboxd(champ(row, "Rating"))
        ligne to DiaryRow(ligne, name, year, date, rating)
    }.toMap()
}
