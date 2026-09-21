package fr.mediatheque.journal.ui.frise

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.journalComplet
import fr.mediatheque.journal.api.dto.PlexFilm
import fr.mediatheque.journal.api.dto.PlexResponse
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.dto.VoyageResponse
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

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

    val vusParAnnee: Map<Int?, List<JournalItem>> = journal.groupBy { it.media.year }
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

/** Le compte de vus et d'à-voir d'une année, à l'intérieur d'une décennie (`DecennieFrise.annees`). */
data class AnneeDecennie(val annee: Int, val vus: Int, val aVoir: Int)

/** Un film d'une décennie, vu ou à voir — porte son année pour trier l'étagère (année puis titre). */
sealed interface FilmDecennie {
    val annee: Int
    val titre: String

    data class Vu(val item: JournalItem, override val annee: Int) : FilmDecennie {
        override val titre: String get() = item.media.title
    }

    data class AVoir(val film: PlexFilm, override val annee: Int) : FilmDecennie {
        override val titre: String get() = film.title
    }
}

/**
 * Les agrégats d'une décennie (« Le rayon », `Screen.Decennie`) : `construireDecennies` en
 * dessous. Nommée `DecennieFrise`, jumelle d'`AnneeFrise` ci-dessus, pour ne pas entrer en
 * conflit avec `Screen.Decennie` — un nom de classe imbriquée l'emporterait sur cet import dans
 * `Navigation.kt`, rendant `Screen.Decennie` récursif sur lui-même.
 */
data class DecennieFrise(
    val decennie: Int,
    val vus: Int,
    val aVoir: Int,
    /** Les films de la décennie, dans l'ordre de l'étagère : année puis titre. */
    val films: List<FilmDecennie>,
    /** Les dix années de la décennie, dans l'ordre, pour les puces du rayon et les cases du calendrier. */
    val annees: List<AnneeDecennie>,
)

/**
 * Les agrégats par décennie de la Frise (brief du 16 septembre 2026), pour le calendrier
 * (`FriseScreen`) et le rayon (`Screen.Decennie`) — fonction pure, testée en JVM.
 *
 * De la première décennie qui a au moins un vu ou un à-voir jusqu'à la décennie courante,
 * décennies intermédiaires vides comprises : le calendrier ne comble pas de trou dans les
 * années (comme `construireFrise`), mais ne saute aucune décennie non plus, pour que sa grille
 * garde une ligne par décennie sans exception. Une année postérieure à `anneeActuelle` (un
 * à-voir du Plex pour un film pas encore sorti, par exemple) n'entre dans aucun compte.
 */
fun construireDecennies(frise: Frise, anneeActuelle: Int = LocalDate.now().year): List<DecennieFrise> {
    val annees = frise.annees.filter { it.annee != null && it.annee <= anneeActuelle }
    if (annees.isEmpty()) return emptyList()

    val decennieDebut = (annees.minOf { it.annee!! } / 10) * 10
    val decennieCourante = (anneeActuelle / 10) * 10

    return (decennieDebut..decennieCourante step 10).map { decennie ->
        val anneesDeLaDecennie = annees.filter { it.annee!! in decennie until decennie + 10 }
        val films = anneesDeLaDecennie
            .flatMap { groupe ->
                val an = groupe.annee!!
                groupe.vus.map { FilmDecennie.Vu(it, an) } + groupe.aVoir.map { FilmDecennie.AVoir(it, an) }
            }
            .sortedWith(compareBy({ it.annee }, { it.titre }))
        val comptesAnnees = (decennie until decennie + 10).map { n ->
            val groupe = anneesDeLaDecennie.firstOrNull { it.annee == n }
            AnneeDecennie(n, groupe?.vus?.size ?: 0, groupe?.aVoir?.size ?: 0)
        }
        DecennieFrise(
            decennie = decennie,
            vus = anneesDeLaDecennie.sumOf { it.vus.size },
            aVoir = anneesDeLaDecennie.sumOf { it.aVoir.size },
            films = films,
            annees = comptesAnnees,
        )
    }
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
    /** Les agrégats par décennie (brief du 16 septembre 2026), pour le calendrier et le rayon. */
    val decennies: List<DecennieFrise> = emptyList(),
    /** Faux si Seerr n'est pas configuré côté back : la Frise ne montre alors que les vus. */
    val plexConfigure: Boolean = false,
    /** Ma progression dans le Voyage (brief du 16 septembre 2026) — la carte tout entière en dépend. */
    val voyage: VoyageUi = VoyageUi(),
    /** Les décennies déjà bouclées (brief du 16 septembre 2026, phase 2), pour le passeport du profil et les génériques de fin — toujours vide à l'étape 1 du brief du 21 septembre 2026 (`tamponsPasseport`). */
    val passeport: List<TamponDecennie> = emptyList(),
    val loading: Boolean = false,
    val error: ApiError? = null,
)

class FriseViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(FriseUi())
    val ui: StateFlow<FriseUi> = _ui
    private var job: Job? = null

    /**
     * L'année en cours du chargement précédent. Nulle au premier chargement, et c'est voulu :
     * `detecterFrontiereAvancee` ne boucle alors rien — sans quoi la première ouverture de l'écran
     * fêterait une année qu'on n'a pas finie pendant qu'on regardait.
     */
    private var anneeEnCoursPrecedente: Int? = null

    /** Ce qu'une année en cours qui avance vient de boucler : la snackbar, le claquement, le générique. */
    private val _avancees = Channel<FrontiereAvancee>(Channel.BUFFERED)
    val avancees: Flow<FrontiereAvancee> = _avancees.receiveAsFlow()

    /** La relecture du ticket après un enregistrement (décision 2 du brief du 21 septembre 2026), en cours au plus une à la fois. */
    private var ticketPollJob: Job? = null

    // Pas d'`init { refresh() }` (jumeau de `FilmsViewModel`/`AuCineViewModel`) : `Root.kt`
    // déclenche le premier chargement par `LaunchedEffect(Unit)` à l'entrée sur l'écran.

    fun refresh() {
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val journal = try {
                api.journalComplet()
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

            // Comme le Plex : une panne ou un serveur sans clé Anthropic ne doit pas priver la
            // Frise de son calendrier — elle se dégrade sans la phrase de tête ni les cadenas/étoiles.
            val voyage = try {
                api.voyage()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                VoyageResponse()
            }

            val frise = construireFrise(journal, plex)
            val voyageUi = voyage.toVoyageUi()
            _ui.update {
                FriseUi(
                    annees = frise.annees,
                    anneeEnCours = frise.anneeEnCours,
                    ensuite = frise.ensuite,
                    decennies = construireDecennies(frise),
                    plexConfigure = plex.configure,
                    voyage = voyageUi,
                    passeport = tamponsPasseport(voyageUi, journal),
                    loading = false,
                )
            }

            // Après la mise à jour de l'état, jamais avant : l'écran qui reçoit l'avancée doit
            // trouver la décennie bouclée déjà dans `passeport` quand il ouvre son générique.
            detecterFrontiereAvancee(anneeEnCoursPrecedente, voyageUi.anneeEnCours)?.let { _avancees.trySend(it) }
            anneeEnCoursPrecedente = voyageUi.anneeEnCours
        }
    }

    /**
     * La relecture après un enregistrement réussi (décision 2 du brief du 21 septembre 2026,
     * « le ticket ») : seulement si le film vient de l'année en cours (`doitRelireApresCreation`) —
     * sinon aucun ticket n'a pu naître de cette écriture. Relit `GET /me/voyage` toutes les cinq
     * secondes, s'arrête dès que `ticket_a_montrer` est là, abandon au bout de douze essais (une
     * minute au plus). Une seule relecture à la fois : un second film de l'année en cours pendant
     * qu'une relecture court déjà relance la sienne plutôt que de s'empiler.
     */
    fun relireApresCreation(anneeFilm: Int?) {
        if (!doitRelireApresCreation(anneeFilm, _ui.value.voyage.anneeEnCours)) return
        ticketPollJob?.cancel()
        ticketPollJob = viewModelScope.launch {
            var essais = 0
            while (true) {
                delay(TICKET_RELECTURE_INTERVAL_MS)
                val reponse = try {
                    api.voyage()
                } catch (e: ApiError) {
                    if (e.isUnauthenticated) onUnauthenticated()
                    return@launch
                }
                val ticket = reponse.toVoyageUi().ticketAMontrer
                if (ticket != null) {
                    _ui.update { it.copy(voyage = it.voyage.copy(ticketAMontrer = ticket)) }
                }
                val (etat, prochainEssai) = etatRelectureTicketSuivant(ticketTrouve = ticket != null, essaisPrecedents = essais)
                essais = prochainEssai
                if (etat != EtatRelectureTicket.EN_COURS) return@launch
            }
        }
    }

    /**
     * « Garder dans le portefeuille » sur le calque du ticket (décision 2) : marque seulement le
     * ticket comme montré — il ne se remontrera plus — sans l'encaisser.
     */
    fun garderTicket(annee: Int) {
        viewModelScope.launch {
            try {
                api.montrerTicket(annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                return@launch
            }
            _ui.update { it.copy(voyage = it.voyage.copy(ticketAMontrer = null)) }
        }
    }

    /**
     * « Utiliser maintenant » sur le calque du ticket (décision 2) : encaissé et montré tous les
     * deux, puis `refresh()` — « Tu es ici » avance et le clap claque par la mécanique existante
     * (`detecterFrontiereAvancee`).
     */
    fun utiliserTicketAMontrer(annee: Int) {
        viewModelScope.launch {
            try {
                api.utiliserTicket(annee)
                api.montrerTicket(annee)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _ui.update { it.copy(error = e) }
                return@launch
            }
            _ui.update { it.copy(voyage = it.voyage.copy(ticketAMontrer = null)) }
            refresh()
        }
    }

    companion object {
        /** La relecture du ticket après un enregistrement (décision 2) : cinq secondes l'essai, comme la chronique d'une année. */
        const val TICKET_RELECTURE_INTERVAL_MS = 5_000L
    }
}
