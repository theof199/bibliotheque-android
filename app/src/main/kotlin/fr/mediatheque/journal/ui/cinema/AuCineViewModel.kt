package fr.mediatheque.journal.ui.cinema

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.SortieCinemaFilm
import fr.mediatheque.journal.api.dto.SortieFilm
import fr.mediatheque.journal.api.dto.SortiesEnCours
import fr.mediatheque.journal.api.dto.SortiesResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * L'écran « Au ciné » (brief du 14 septembre 2026) : mes séances, et les
 * sorties en salle de la semaine en cours et de la semaine prochaine.
 *
 * Deux appels réseau indépendants — `sorties()` et `seances()` — chacun avec
 * son chargement et son erreur : une panne TMDB (sorties) n'a aucune raison
 * d'effacer « Tes séances », qui vient d'une route différente et répond
 * toujours si le journal, lui, est joignable.
 */
data class AuCineUi(
    val sorties: SortiesResponse? = null,
    val sortiesLoading: Boolean = false,
    val sortiesError: ApiError? = null,
    val seances: List<JournalItem> = emptyList(),
    val seancesLoading: Boolean = false,
    val seancesEndReached: Boolean = false,
    val seancesError: ApiError? = null,
)

/**
 * Le nombre de séances de l'année en cours, parmi les entrées **déjà
 * chargées** : « Tes séances » se pagine comme « Mes films », et ce compte
 * n'est donc exact que si l'année entière tient dans ce qui a été chargé —
 * hypothèse raisonnable pour un usage personnel, jamais des dizaines de
 * séances par an.
 */
fun AuCineUi.seancesCetteAnnee(annee: Int = LocalDate.now().year): Int =
    seances.count { it.entry.finished_at.take(4).toIntOrNull() == annee }

/**
 * Vrai si ce film (par `tmdb_id`) figure déjà parmi les séances chargées —
 * la coche corail des grilles. Rapprochement par `media.external_id`, le
 * `tmdb_id` du film sur le carnet (`JournalMediaSchema`, depuis le
 * 14 septembre 2026), jamais par le titre.
 */
fun AuCineUi.dejaDansLeJournal(film: SortieFilm): Boolean =
    seances.any { it.media.external_id == film.tmdb_id.toString() }

/**
 * Le même rapprochement, pour une tuile « à l'affiche dans mes cinémas »
 * (brief du 15 septembre 2026). Une tuile sans `tmdb_id` ne peut jamais
 * porter la coche : rien à rapprocher, ce n'est de toute façon pas une
 * tuile touchable (`estOuvrable`, ci-dessous).
 */
fun AuCineUi.dejaDansLeJournal(film: SortieCinemaFilm): Boolean {
    val tmdbId = film.tmdb_id ?: return false
    return seances.any { it.media.external_id == tmdbId.toString() }
}

/**
 * Une tuile « à l'affiche dans mes cinémas » n'est touchable que si TMDB a
 * été retrouvé côté back — sans lui, rien à ouvrir dans le formulaire ni à
 * afficher dans une fiche (brief du 15 septembre 2026).
 */
fun SortieCinemaFilm.estOuvrable(): Boolean = tmdb_id != null

/**
 * Sous-titre d'une tuile « à l'affiche dans mes cinémas » : le premier
 * cinéma, puis « +N » s'il y en a d'autres (brief du 15 septembre 2026).
 * `cinemas` porte toujours au moins une entrée quand le film vient du back
 * (le contrat l'exige) ; une liste vide — un film construit à la main dans
 * un test, par exemple — rend une chaîne vide plutôt que de lever.
 */
fun SortieCinemaFilm.sousTitreCinemas(): String {
    val premier = cinemas.firstOrNull() ?: return ""
    val reste = cinemas.size - 1
    return if (reste > 0) "$premier +$reste" else premier
}

/**
 * Le message à afficher à la place de la grille « à l'affiche dans mes
 * cinémas », ou nul quand elle doit s'afficher (brief du 15 septembre 2026).
 *
 * Deux causes distinctes rendent « Pas encore de programme. » : aucun cinéma
 * configuré, ou la tâche de fond du back n'a **jamais** tourné (`films` vide
 * ET `calcule_le` nul) — à ne pas confondre avec une passe qui a bien eu
 * lieu et n'a simplement rien trouvé aujourd'hui, qui a son propre message.
 */
fun SortiesEnCours.messageAuCine(): String? = when {
    !cinemas_configures || (films.isEmpty() && calcule_le == null) -> "Pas encore de programme."
    films.isEmpty() -> "Rien à l’affiche aujourd’hui."
    else -> null
}

/** Fuseau fixe, comme côté back (`routes/reference.ts`) : une heure affichée qui ne varie pas avec l'appareil. */
private val FUSEAU_AU_CINE: ZoneId = ZoneId.of("Europe/Paris")

/**
 * « mis à jour à 14 h », depuis `calcule_le` (ISO 8601, UTC) — nul tant que
 * la tâche de fond n'a jamais tourné, ou si le back envoie une date
 * illisible plutôt que de faire échouer tout l'écran pour ça.
 */
fun SortiesEnCours.miseAJourAffichee(): String? {
    val instant = calcule_le?.let { runCatching { Instant.parse(it) }.getOrNull() } ?: return null
    val heure = instant.atZone(FUSEAU_AU_CINE).hour
    return "mis à jour à $heure h"
}

/**
 * Convertit une sortie en le `SearchResult` qu'attend `Screen.Form` — la même
 * cible que « toucher un résultat de recherche » (`Root.kt`), pour ne pas
 * dupliquer le formulaire de création.
 *
 * `director` reste nul : `SortieFilm` ne le porte plus depuis le correctif du
 * 14 septembre 2026 (la fiche détaillée par film saturait la file sortante
 * TMDB côté back). Le formulaire s'ouvre donc sans réalisateur pré-rempli.
 */
fun SortieFilm.toSearchResult(): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdb_id.toString(),
    type = "movie",
    title = title,
    year = year,
    cover_url = cover_url,
    metadata = SearchMetadata(director = null),
    original_title = original_title,
)

/**
 * Le même formulaire pré-rempli, depuis une tuile « à l'affiche dans mes
 * cinémas ». N'a de sens que pour une tuile touchable (`estOuvrable`) : lève
 * sinon, plutôt que d'ouvrir un formulaire sans identifiant TMDB.
 *
 * `director` reste nul, comme pour `SortieFilm.toSearchResult()` : mêmes
 * tuiles, même geste, même formulaire — la disponibilité de `directors` ici
 * ne justifie pas à elle seule de préremplir un champ que l'autre grille ne
 * préremplit pas.
 */
fun SortieCinemaFilm.toSearchResult(): SearchResult {
    val id = requireNotNull(tmdb_id) { "toSearchResult() sur une tuile sans tmdb_id (non touchable)" }
    return SearchResult(
        source = "tmdb",
        external_id = id.toString(),
        type = "movie",
        title = title,
        year = year,
        cover_url = cover_url,
        metadata = SearchMetadata(director = null),
        original_title = original_title,
    )
}

class AuCineViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(AuCineUi())
    val ui: StateFlow<AuCineUi> = _ui
    private var cursor: String? = null
    private var sortiesJob: Job? = null
    private var seancesJob: Job? = null

    // Pas d'`init { refresh() }` (jumeau de `FilmsViewModel`) : `Root.kt` déclenche le premier
    // chargement par `LaunchedEffect(Unit)` à l'entrée sur l'écran.

    fun refresh() {
        loadSorties()
        cursor = null
        _ui.update { it.copy(seancesEndReached = false) }
        loadMoreSeances()
    }

    /**
     * « Réessayer » côté sorties seul : ne touche ni au curseur ni aux séances déjà chargées,
     * à la différence de `refresh()`. Sans elle, l'`ErrorBlock` d'une panne TMDB relancerait
     * aussi « Tes séances » depuis sa première page.
     */
    fun retrySorties() = loadSorties()

    private fun loadSorties() {
        sortiesJob?.cancel()
        _ui.update { it.copy(sortiesLoading = true, sortiesError = null) }
        sortiesJob = viewModelScope.launch {
            try {
                val response = api.sorties()
                _ui.update { it.copy(sorties = response, sortiesLoading = false) }
            } catch (e: ApiError) {
                _ui.update { it.copy(sortiesLoading = false, sortiesError = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
            }
        }
    }

    fun loadMoreSeances() {
        if (seancesJob?.isActive == true || _ui.value.seancesEndReached) return
        _ui.update { it.copy(seancesLoading = true, seancesError = null) }
        // Jumeau de `FilmsViewModel.loadMore()` : une page 1 (`cursor == null`) REMPLACE la
        // liste, une page suivante l'ÉTEND — testé sur `cursor`, jamais sur un drapeau posé par
        // `refresh()` (même piège que celui réglé là-bas).
        val premierePage = cursor == null
        seancesJob = viewModelScope.launch {
            try {
                val page = api.seances(cursor)
                cursor = page.next_cursor
                _ui.update {
                    it.copy(
                        seances = if (premierePage) page.items else it.seances + page.items,
                        seancesLoading = false,
                        seancesEndReached = page.next_cursor == null,
                    )
                }
            } catch (e: ApiError) {
                _ui.update { it.copy(seancesLoading = false, seancesError = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
            }
        }
    }
}
