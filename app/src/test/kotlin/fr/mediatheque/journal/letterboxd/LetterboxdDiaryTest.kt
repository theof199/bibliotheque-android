package fr.mediatheque.journal.letterboxd

import java.io.ByteArrayOutputStream
import java.io.IOException
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Un ZIP en mémoire, une entrée par paire (nom, contenu) — sert à composer les fixtures ci-dessous. */
private fun zipOf(vararg entries: Pair<String, String>): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        for ((name, content) in entries) {
            zip.putNextEntry(ZipEntry(name))
            zip.write(content.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }
    return out.toByteArray()
}

/**
 * Un ZIP dont l'entrée `diary.csv` est tronquée en plein milieu de ses données — pas juste après
 * la dernière entrée, où `ZipInputStream` ne lit jamais (il ignore le répertoire central, écrit
 * après coup, et s'arrête dès qu'il a lu la dernière entrée locale). `STORED`, sans compression :
 * la taille déclarée ne correspond alors plus aux octets réellement présents, ce que
 * `ZipInputStream` détecte en lisant l'entrée, pas en l'ouvrant.
 */
private fun zipDiaryTronque(): ByteArray {
    val contenu = "A".repeat(5_000).toByteArray()
    val entry = ZipEntry("diary.csv").apply {
        method = ZipEntry.STORED
        size = contenu.size.toLong()
        compressedSize = contenu.size.toLong()
        crc = java.util.zip.CRC32().apply { update(contenu) }.value
    }
    val complet = ByteArrayOutputStream().also { out ->
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(entry)
            zip.write(contenu)
            zip.closeEntry()
        }
    }.toByteArray()

    // Coupe bien avant la fin des 5000 octets annoncés, mais après l'en-tête local : la lecture
    // de l'entrée s'arrêtera en plein milieu, faute d'octets.
    return complet.copyOf(100)
}

private const val DIARY_HEADER = "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date"

class LetterboxdDiaryTest {
    // --- looksLikeZip -----------------------------------------------------------------------

    @Test
    fun `reconnait la signature PK d un vrai zip`() {
        val zip = zipOf("diary.csv" to "$DIARY_HEADER\n")
        assertTrue(looksLikeZip(zip))
    }

    @Test
    fun `refuse un texte brut, meme s il commence par PK en apparence`() {
        assertFalse(looksLikeZip("Date,Name,Year".toByteArray()))
        assertFalse(looksLikeZip(ByteArray(2))) // trop court pour porter la signature
    }

    // --- extractDiaryCsv ---------------------------------------------------------------------

    @Test
    fun `extrait diary csv quand il est present`() {
        val contenu = "$DIARY_HEADER\n2026-01-10,Voyage Test,2011,https://x,4.5,false,,2026-01-09\n"
        val zip = zipOf("watched.csv" to "Date,Name,Year,Letterboxd URI\n", "diary.csv" to contenu)

        assertEquals(contenu, extractDiaryCsv(zip))
    }

    // Letterboxd exporte parfois sous un dossier ("letterboxd-export/diary.csv") : seul le nom du
    // fichier compte, pas son chemin complet.
    @Test
    fun `retrouve diary csv sous un dossier`() {
        val contenu = "$DIARY_HEADER\n"
        val zip = zipOf("letterboxd-export/diary.csv" to contenu)

        assertEquals(contenu, extractDiaryCsv(zip))
    }

    @Test
    fun `leve DiaryCsvMissingException quand le zip n a pas diary csv`() {
        val zip = zipOf("watched.csv" to "Date,Name,Year,Letterboxd URI\n", "ratings.csv" to "Date,Name,Year\n")

        assertThrows(DiaryCsvMissingException::class.java) { extractDiaryCsv(zip) }
    }

    @Test
    fun `leve IOException sur un zip invalide`() {
        assertThrows(IOException::class.java) { extractDiaryCsv(zipDiaryTronque()) }
    }

    // --- diaryCsvFrom --------------------------------------------------------------------------

    @Test
    fun `diaryCsvFrom lit un diary csv seul, sans zip`() {
        val contenu = "$DIARY_HEADER\n2026-01-10,Voyage Test,2011,https://x,,false,,\n"
        assertEquals(contenu, diaryCsvFrom(contenu.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun `diaryCsvFrom extrait le zip quand c en est un`() {
        val contenu = "$DIARY_HEADER\n"
        val zip = zipOf("diary.csv" to contenu)
        assertEquals(contenu, diaryCsvFrom(zip))
    }

    // --- parseCsv : guillemets et virgule ------------------------------------------------------

    @Test
    fun `lit un champ entre guillemets avec virgule et guillemet double`() {
        val ligne = """2026-01-13,"Le Film, ""Test"" Ultime",2019,https://x,,false,,"""
        val rows = parseCsv("$DIARY_HEADER\n$ligne\n")

        assertEquals(listOf("2026-01-13", "Le Film, \"Test\" Ultime", "2019", "https://x", "", "false", "", ""), rows[1])
    }

    // --- parseDiaryRows : la date et la note, comme le back --------------------------------

    @Test
    fun `Watched Date l emporte sur Date quand les deux sont presentes`() {
        val csv = "$DIARY_HEADER\n2026-01-10,Voyage Test,2011,https://x,4.5,false,,2026-01-09\n"
        val ligne = parseDiaryRows(csv)[2]!!

        assertEquals(LocalDate.parse("2026-01-09"), ligne.date)
        assertEquals(9, ligne.rating) // 4.5 * 2
    }

    @Test
    fun `retombe sur Date quand Watched Date est vide`() {
        val csv = "$DIARY_HEADER\n2026-01-12,Voyage Test,2011,https://x,3,false,,\n"
        val ligne = parseDiaryRows(csv)[2]!!

        assertEquals(LocalDate.parse("2026-01-12"), ligne.date)
        assertEquals(6, ligne.rating) // 3 * 2
    }

    @Test
    fun `une note vide ne porte aucune note`() {
        val csv = "$DIARY_HEADER\n2026-01-12,Voyage Test,2011,https://x,,false,,2026-01-12\n"
        val ligne = parseDiaryRows(csv)[2]!!

        assertNull(ligne.rating)
    }

    @Test
    fun `carte vide sur un en-tete qui n est pas celui de diary csv`() {
        val csv = "Date,Name,Year,Letterboxd URI\n2026-01-01,Voyage Test,2011,https://x\n"
        assertEquals(emptyMap<Int, DiaryRow>(), parseDiaryRows(csv))
    }
}
