package fr.mediatheque.journal.ui.frise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * La Frise et « Ensuite » (chantier du 15 septembre 2026) : le cinéma du
 * propriétaire dans l'ordre chronologique, année par année. Ses films vus
 * viennent du journal (`GET /me/journal`, toutes les pages) ; ce qu'il lui
 * reste à voir vient de son Plex, alimenté par Seerr (`GET /reference/plex`).
 * Rien de tout ça n'est écrit en base côté back — une lecture composée ici.
 */

/** Une année de la Frise : ses films vus, et ceux du Plex pas encore vus. `annee` nul = « Sans année ». */
data class AnneeFrise(val annee: Int?, val vus: List<JournalItem>, val aVoir: List<PlexFilm>)

data class Frise(
    val annees: List<AnneeFrise> = emptyList(),
    /** La plus ancienne année qui a au moins un « à voir » — nulle si tout est vu, ou Plex non configuré. */
    val anneeEnCours: Int? = null,
    /** Le plus ancien « à voir », toutes années confondues (année puis titre) — nul s'il n'y a rien à voir. */
    val ensuite: PlexFilm? = null,
)

/**
 * Construit la Frise à partir du journal complet et du Plex — fonction pure,
 * testée en JVM sans réseau ni `ViewModel`.
 *
 * Un film du Plex déjà dans le journal (`tmdb_id == media.external_id`) est
 * écarté : ce n'est plus « à voir ». Les années sans rien (ni vu, ni à voir)
 * sont omises plutôt que de combler un trou dans la frise. Les films sans
 * année (vus ou à voir) rejoignent un groupe « Sans année » en fin de liste,
 * qui ne peut jamais être `anneeEnCours`.
 */
fun construireFrise(journal: List<JournalItem>, plex: PlexResponse): Frise {
    val dejaVus = journal.mapNotNull { it.media.external_id.toIntOrNull() }.toSet()
    val aVoir = plex.films.filterNot { it.tmdb_id in dejaVus }

    val vusParAnnee: Map<Int?, List<JournalItem>> = journal.groupBy { it.entry.finished_at.take(4).toIntOrNull() }
    val aVoirParAnnee: Map<Int?, List<PlexFilm>> = aVoir.groupBy { it.year }

    val anneesConnues = (vusParAnnee.keys + aVoirParAnnee.keys).filterNotNull().distinct().sorted()
    val groupes = anneesConnues.map { annee ->
        AnneeFrise(
            annee = annee,
            vus = vusParAnnee[annee].orEmpty().sortedByDescending { it.entry.finished_at },
            aVoir = aVoirParAnnee[annee].orEmpty().sortedBy { it.title },
        )
    }
    val sansAnnee = if (vusParAnnee.containsKey(null) || aVoirParAnnee.containsKey(null)) {
        listOf(
            AnneeFrise(
                annee = null,
                vus = vusParAnnee[null].orEmpty().sortedByDescending { it.entry.finished_at },
                aVoir = aVoirParAnnee[null].orEmpty().sortedBy { it.title },
            ),
        )
    } else {
        emptyList()
    }

    val anneeEnCours = groupes.firstOrNull { it.aVoir.isNotEmpty() }?.annee
    val ensuite = aVoir.minWithOrNull(compareBy({ it.year ?: Int.MAX_VALUE }, { it.title }))

    return Frise(annees = groupes + sansAnnee, anneeEnCours = anneeEnCours, ensuite = ensuite)
}

/** Le même formulaire pré-rempli que « Au ciné » (`SortieFilm.toSearchResult()`) : mêmes tuiles, même geste. */
fun PlexFilm.toSearchResult(): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdb_id.toString(),
    type = "movie",
    title = title,
    year = year,
    cover_url = cover_url,
    metadata = SearchMetadata(director = null),
    original_title = original_title,
)

data class FriseUi(
    val annees: List<AnneeFrise> = emptyList(),
    val anneeEnCours: Int? = null,
    val ensuite: PlexFilm? = null,
    /** Faux si Seerr n'est pas configuré côté back : la Frise ne montre alors que les vus. */
    val plexConfigure: Boolean = false,
    val loading: Boolean = false,
    val error: ApiError? = null,
)

class FriseViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(FriseUi())
    val ui: StateFlow<FriseUi> = _ui
    private var job: Job? = null

    // Pas d'`init { refresh() }` (jumeau de `FilmsViewModel`/`AuCineViewModel`) : `Root.kt`
    // déclenche le premier chargement par `LaunchedEffect(Unit)` à l'entrée sur l'écran.

    fun refresh() {
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val journal = try {
                journalComplet()
            } catch (e: ApiError) {
                _ui.update { it.copy(loading = false, error = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }

            // Une panne du Plex (Seerr injoignable, ou pas encore configuré) ne doit pas priver
            // la Frise des films déjà vus : elle se dégrade en Frise « vus seuls », jamais un
            // `ErrorBlock` bloquant (brief du 15 septembre 2026).
            val plex = try {
                api.plex()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                PlexResponse()
            }

            val frise = construireFrise(journal, plex)
            _ui.update {
                FriseUi(
                    annees = frise.annees,
                    anneeEnCours = frise.anneeEnCours,
                    ensuite = frise.ensuite,
                    plexConfigure = plex.configure,
                    loading = false,
                )
            }
        }
    }

    /** Toutes les pages de `GET /me/journal`, chargées à la suite — la Frise veut le journal entier, pas une page. */
    private suspend fun journalComplet(): List<JournalItem> {
        val items = mutableListOf<JournalItem>()
        var cursor: String? = null
        do {
            val page = api.journal(cursor)
            items += page.items
            cursor = page.next_cursor
        } while (cursor != null)
        return items
    }
}
