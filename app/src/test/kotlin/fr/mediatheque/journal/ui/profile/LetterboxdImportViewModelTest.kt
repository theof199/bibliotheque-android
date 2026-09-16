package fr.mediatheque.journal.ui.profile

import fr.mediatheque.journal.FakeJournalApi
import fr.mediatheque.journal.MainDispatcherRule
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.dto.ImportLetterboxdCandidate
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.api.dto.ImportLetterboxdUnrecognized
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

private const val DIARY_HEADER = "Date,Name,Year,Letterboxd URI,Rating,Rewatch,Tags,Watched Date"

/** Un diary.csv à une ligne : Voyage Test (2011), notée 4.5, vue le 9 (Watched Date), journalisée le 10 (Date). */
private fun diaryCsv() = "$DIARY_HEADER\n2026-01-10,Voyage Test,2011,https://x,4.5,false,,2026-01-09\n"

private fun zipAvecDiary(contenu: String): ByteArray {
    val out = ByteArrayOutputStream()
    ZipOutputStream(out).use { zip ->
        zip.putNextEntry(ZipEntry("diary.csv"))
        zip.write(contenu.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }
    return out.toByteArray()
}

/** Jumeau de `LetterboxdDiaryTest.zipDiaryTronque()` : une entrée `diary.csv` STORED, coupée en plein milieu de ses données. */
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
    return complet.copyOf(100)
}

@OptIn(ExperimentalCoroutinesApi::class)
class LetterboxdImportViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    @get:Rule val main = MainDispatcherRule(dispatcher)
    private val api = FakeJournalApi()
    private var expire = 0

    private fun vm() = LetterboxdImportViewModel(api) { expire++ }

    @Test
    fun `succes rend le rapport`() = runTest(dispatcher) {
        val rapport = ImportLetterboxdResponse(importes = 1, deja_presents = 0)
        api.onImportLetterboxd = { rapport }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()

        assertEquals(rapport, vm.ui.value.rapport)
        assertNull(vm.ui.value.error)
    }

    @Test
    fun `un 400 du back affiche son message, pas de rapport`() = runTest(dispatcher) {
        api.onImportLetterboxd = {
            throw ApiError("VALIDATION", "Ce fichier n’a pas les en-têtes de diary.csv.", retryable = false, status = 400)
        }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()

        assertEquals("Ce fichier n’a pas les en-têtes de diary.csv.", vm.ui.value.error?.message)
        assertNull(vm.ui.value.rapport)
    }

    @Test
    fun `une panne reseau affiche le message d indisponibilite`() = runTest(dispatcher) {
        api.onImportLetterboxd = { throw FakeJournalApi.network() }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()

        assertEquals("L’API est injoignable.", vm.ui.value.error?.message)
    }

    @Test
    fun `un 401 previent la session, sans message d erreur affiche`() = runTest(dispatcher) {
        api.onImportLetterboxd = { throw FakeJournalApi.unauthorized() }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()

        assertEquals(1, expire)
    }

    // Un ZIP sans diary.csv ne doit jamais atteindre le reseau : le message est local, avant tout
    // appel. Mutation : ne pas distinguer `DiaryCsvMissingException` de `IOException` ferait
    // tomber le message attendu (celui, generique, du fichier illisible, prendrait sa place).
    @Test
    fun `un zip sans diary csv dit lequel manque, sans appeler le reseau`() = runTest(dispatcher) {
        api.onImportLetterboxd = { error("ne doit pas etre appele") }
        val zipSansDiary = run {
            val out = ByteArrayOutputStream()
            ZipOutputStream(out).use { zip ->
                zip.putNextEntry(ZipEntry("watched.csv"))
                zip.write("Date,Name,Year,Letterboxd URI\n".toByteArray())
                zip.closeEntry()
            }
            out.toByteArray()
        }
        val vm = vm()

        vm.start(zipSansDiary)
        testScheduler.advanceUntilIdle()

        assertEquals("Ce ZIP ne contient pas diary.csv", vm.ui.value.error?.message)
        assertEquals(emptyList<String>(), api.calls)
    }

    // La signature ZIP (`PK`) suivie de rien de valide : `diaryCsvFrom` le reconnait comme un ZIP
    // (§`looksLikeZip`) et l'extraction leve une `IOException` que le `ViewModel` doit distinguer
    // de `DiaryCsvMissingException` — message generique, faute de savoir ce qui a precisement
    // echoue dans un flux corrompu.
    @Test
    fun `un zip invalide affiche un message generique, sans appeler le reseau`() = runTest(dispatcher) {
        api.onImportLetterboxd = { error("ne doit pas etre appele") }
        val vm = vm()

        vm.start(zipDiaryTronque())
        testScheduler.advanceUntilIdle()

        assertEquals("Ce fichier n’a pas pu être lu.", vm.ui.value.error?.message)
        assertEquals(emptyList<String>(), api.calls)
    }

    // Le pre-remplissage d'un candidat choisi dans le rapport : la date et la note viennent de la
    // ligne du fichier envoye, pas d'une valeur par defaut. Mutation : renvoyer (null, null) sans
    // relire `lignes` fait tomber les deux assertions.
    @Test
    fun `prefillFor porte la date et la note de la ligne envoyee`() = runTest(dispatcher) {
        api.onImportLetterboxd = {
            ImportLetterboxdResponse(
                importes = 0,
                deja_presents = 0,
                non_reconnus = listOf(
                    ImportLetterboxdUnrecognized(
                        ligne = 2,
                        name = "Voyage Test",
                        year = 2011,
                        candidats = listOf(ImportLetterboxdCandidate("611", "Voyage Test", 2011)),
                    ),
                ),
            )
        }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()

        val (date, note) = vm.prefillFor(2)
        assertEquals(LocalDate.parse("2026-01-09"), date) // Watched Date, prioritaire sur Date
        assertEquals(9, note) // 4.5 * 2
    }

    @Test
    fun `prefillFor rend deux nuls pour une ligne inconnue`() = runTest(dispatcher) {
        api.onImportLetterboxd = { ImportLetterboxdResponse(importes = 1, deja_presents = 0) }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()

        assertEquals(null to null, vm.prefillFor(999))
    }

    // Fonctionne aussi depuis un ZIP, pas seulement un diary.csv nu.
    @Test
    fun `fonctionne aussi depuis un zip`() = runTest(dispatcher) {
        val rapport = ImportLetterboxdResponse(importes = 1, deja_presents = 0)
        api.onImportLetterboxd = { csvRecu -> assertEquals(diaryCsv(), csvRecu); rapport }
        val vm = vm()

        vm.start(zipAvecDiary(diaryCsv()))
        testScheduler.advanceUntilIdle()

        assertEquals(rapport, vm.ui.value.rapport)
    }

    // Jumeau de `ProfileViewModelTest` : un second `start()` avant la fin du premier annule celui-
    // ci plutot que de laisser les deux en vol sans ordre garanti. Mutation : retirer
    // `enCours?.cancel()` fait echouer cette assertion (le premier resultat, perime, gagnerait).
    @Test
    fun `un second start annule le premier`() = runTest(dispatcher) {
        val porte = CompletableDeferred<ImportLetterboxdResponse>()
        api.onImportLetterboxd = { porte.await() }
        val vm = vm()

        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        testScheduler.advanceUntilIdle()
        vm.start(diaryCsv().toByteArray(Charsets.UTF_8))
        porte.complete(ImportLetterboxdResponse(importes = 5, deja_presents = 0))
        testScheduler.advanceUntilIdle()

        assertEquals(5, vm.ui.value.rapport?.importes)
    }
}
