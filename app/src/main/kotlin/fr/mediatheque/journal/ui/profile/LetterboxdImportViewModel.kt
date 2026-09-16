package fr.mediatheque.journal.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.ImportLetterboxdCandidate
import fr.mediatheque.journal.api.dto.ImportLetterboxdResponse
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.letterboxd.DiaryCsvMissingException
import fr.mediatheque.journal.letterboxd.DiaryRow
import fr.mediatheque.journal.letterboxd.diaryCsvFrom
import fr.mediatheque.journal.letterboxd.parseDiaryRows
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

// Ni l'un ni l'autre : encore en train d'importer (jumeau de `ProfileUi`, qui n'a pas non plus de
// `loading` — `RapportImportScreen` ne distingue rien de plus qu'« en cours » tant que les deux
// sont nuls).
data class LetterboxdImportUi(val rapport: ImportLetterboxdResponse? = null, val error: ApiError? = null)

private const val ZIP_ILLISIBLE_MESSAGE = "Ce fichier n’a pas pu être lu."

/**
 * L'import Letterboxd (brief du 16 septembre 2026), depuis le profil.
 *
 * Indexé sur l'Activité (`Root.kt`, clé `"letterboxd-import"`) : `Screen.RapportImport` ne porte
 * aucune donnée, elle relit cette instance. Le retour système pendant l'attente dépile l'écran
 * sans annuler la coroutine — `viewModelScope` survit à la navigation, seule l'attente *visible*
 * s'arrête, la requête continue derrière (design §6).
 *
 * Les lignes du CSV envoyé sont gardées (`lignes`), indexées par leur numéro : `prefillFor` les
 * relit pour pré-remplir le formulaire d'un candidat choisi dans le rapport, avec la date et la
 * note que *le back* a lues sur cette ligne-là — l'appli ne fait que rejouer sa propre lecture du
 * même fichier, jamais une seconde source de vérité.
 */
class LetterboxdImportViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(LetterboxdImportUi())
    val ui: StateFlow<LetterboxdImportUi> = _ui
    private var enCours: Job? = null
    private var lignes: Map<Int, DiaryRow> = emptyMap()

    /** `bytes` : le fichier choisi tel quel, ZIP ou `diary.csv` seul — `diaryCsvFrom` tranche. */
    fun start(bytes: ByteArray) {
        enCours?.cancel()
        lignes = emptyMap()
        _ui.value = LetterboxdImportUi()
        enCours = viewModelScope.launch {
            try {
                val csv = diaryCsvFrom(bytes)
                lignes = parseDiaryRows(csv)
                val rapport = api.importLetterboxd(csv)
                _ui.value = LetterboxdImportUi(rapport = rapport)
            } catch (e: DiaryCsvMissingException) {
                _ui.value = LetterboxdImportUi(error = erreurLocale("ZIP_SANS_DIARY", e.message ?: ""))
            } catch (e: IOException) {
                _ui.value = LetterboxdImportUi(error = erreurLocale("FICHIER_ILLISIBLE", ZIP_ILLISIBLE_MESSAGE))
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.value = LetterboxdImportUi(error = e)
            }
        }
    }

    /** Date et note de la ligne telle que le back les a lues — pour pré-remplir le formulaire d'un candidat choisi. */
    fun prefillFor(ligne: Int): Pair<LocalDate?, Int?> = lignes[ligne]?.let { it.date to it.rating } ?: (null to null)

    /** Une erreur lue avant tout réseau (ZIP invalide ou sans `diary.csv`) : jamais retryable, il n'y a rien à réessayer sans changer de fichier. */
    private fun erreurLocale(code: String, message: String) = ApiError(code, message, retryable = false, status = null)
}

/** Le même formulaire pré-rempli que partout ailleurs (`FilmSuivi.toSearchResult()`, `SortieFilm.toSearchResult()`) : mêmes tuiles, même geste. */
fun ImportLetterboxdCandidate.toSearchResult(): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdb_id,
    type = "movie",
    title = title,
    year = year,
)
