package fr.mediatheque.journal.ui.realisateurs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.FilmDeRealisateur
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.Realisateur
import fr.mediatheque.journal.api.dto.SearchMetadata
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.api.journalComplet
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Les réalisateurs (brief du 15 septembre 2026) : ceux que le propriétaire suit, leur
 * filmographie, et ce qu'il lui reste à y voir.
 *
 * Une seule instance pour les trois écrans (la liste, la fiche) et pour la seconde ligne
 * « Ensuite » de l'accueil — `Root.kt` la tient sous la clé `"realisateurs"`. La liste vient
 * d'un appel (`GET /me/realisateurs`), les filmographies d'un appel **par réalisateur**, joués
 * à la suite les uns des autres : chaque ligne s'affiche avant de savoir ce qu'elle compte, et
 * dit « … » en attendant sa réponse.
 */

/**
 * Ce qu'on sait de la filmographie d'un réalisateur. `Indisponible` n'est pas une erreur
 * d'écran : une filmographie en panne ne doit priver ni la liste des autres, ni l'écran de son
 * affichage — la ligne dit « indisponible » et la suivante continue de se charger.
 */
sealed interface EtatFilmographie {
    data object EnAttente : EtatFilmographie
    data class Pret(val films: List<FilmDeRealisateur>) : EtatFilmographie
    data object Indisponible : EtatFilmographie
}

/** Le réalisateur « en cours » de l'accueil, et le film qu'il reste à voir de lui. */
data class RealisateurEnCours(val realisateur: Realisateur, val prochain: FilmDeRealisateur)

/** Combien de films de cette filmographie j'ai déjà journalisés. */
fun filmsVus(films: List<FilmDeRealisateur>): Int = films.count { it.vu != null }

/**
 * Le prochain film à voir : le premier que je n'ai pas vu, dans l'ordre où le back les rend —
 * de la plus ancienne sortie à la plus récente. Nul quand j'ai tout vu.
 */
fun prochainAVoir(films: List<FilmDeRealisateur>): FilmDeRealisateur? = films.firstOrNull { it.vu == null }

/** « Lolita (1962) », ou « Lolita » tout court si TMDB n'a pas d'année pour lui. */
fun titreEtAnnee(film: FilmDeRealisateur): String = film.year?.let { "${film.title} ($it)" } ?: film.title

/**
 * La ligne sous le nom, dans la liste : « 7 vus sur 13 · prochain : Lolita (1962) ». La part
 * « prochain » disparaît quand il n'y a plus rien à voir — le compte, lui, reste.
 */
fun resumeFilmographie(films: List<FilmDeRealisateur>): String {
    val compte = "${filmsVus(films)} vus sur ${films.size}"
    val prochain = prochainAVoir(films) ?: return compte
    return "$compte · prochain : ${titreEtAnnee(prochain)}"
}

/**
 * Ce que dit la ligne, quel que soit l'état de sa filmographie : « … » tant que sa réponse n'est
 * pas là, « indisponible » quand elle a échoué (l'écran, lui, reste entier).
 */
fun libelleLigne(etat: EtatFilmographie): String = when (etat) {
    EtatFilmographie.EnAttente -> "…"
    EtatFilmographie.Indisponible -> "indisponible"
    is EtatFilmographie.Pret -> resumeFilmographie(etat.films)
}

/**
 * Le « réalisateur en cours » de l'accueil : celui qui a le plus de films vus **parmi ceux qui
 * ont encore au moins un film non vu**. Nul si aucun réalisateur n'est dans ce cas — la ligne
 * « Ensuite » est alors absente de l'accueil, plutôt qu'affichée vide.
 *
 * Une filmographie encore en attente ou indisponible ne participe pas : on ne peut ni la
 * compter, ni en tirer un prochain film. À égalité de films vus, c'est le premier de la liste
 * qui gagne — `GET /me/realisateurs` la rend du plus récemment ajouté au plus ancien, donc le
 * dernier ajouté ; `maxByOrNull` garde le premier maximum rencontré, jamais le dernier.
 */
fun realisateurEnCours(
    realisateurs: List<Realisateur>,
    filmographies: Map<Int, EtatFilmographie>,
): RealisateurEnCours? {
    val candidats = realisateurs.mapNotNull { realisateur ->
        val films = (filmographies[realisateur.tmdb_id] as? EtatFilmographie.Pret)?.films ?: return@mapNotNull null
        val prochain = prochainAVoir(films) ?: return@mapNotNull null
        Triple(realisateur, prochain, filmsVus(films))
    }
    val gagnant = candidats.maxByOrNull { it.third } ?: return null
    return RealisateurEnCours(gagnant.first, gagnant.second)
}

/**
 * Le même formulaire pré-rempli que « Au ciné » et la Frise (`PlexFilm.toSearchResult()`) :
 * mêmes tuiles, même geste. `metadata.director` porte le nom du réalisateur — on le connaît
 * ici, à la différence de la Frise.
 */
fun FilmDeRealisateur.toSearchResult(nomDuRealisateur: String? = null): SearchResult = SearchResult(
    source = "tmdb",
    external_id = tmdb_id.toString(),
    type = "movie",
    title = title,
    year = year,
    cover_url = cover_url,
    metadata = SearchMetadata(director = nomDuRealisateur),
    original_title = original_title,
)

/**
 * Le formulaire pré-rempli derrière la ligne « Ensuite » du réalisateur en cours. Nommé à part
 * plutôt qu'appelé depuis `Root.kt` : celui-ci importe déjà un `toSearchResult` (celui de la
 * Frise, sur `PlexFilm`), et deux extensions homonymes dans un même fichier se lisent mal, même
 * quand le compilateur, lui, sait les distinguer par leur receveur.
 */
fun RealisateurEnCours.formulaire(): SearchResult = prochain.toSearchResult(realisateur.name)

data class RealisateursUi(
    val realisateurs: List<Realisateur> = emptyList(),
    /** Une entrée par réalisateur de `realisateurs`, dans l'un des trois états ci-dessus. */
    val filmographies: Map<Int, EtatFilmographie> = emptyMap(),
    /**
     * Mes entrées de journal par `entry_id`, pour ouvrir la correction d'un film vu depuis une
     * fiche. Le contrat n'expose aucun `GET /me/journal/{id}` (seulement `PATCH` et `DELETE`) :
     * la seule façon de retrouver une entrée entière — ses réactions et sa remarque comprises —
     * est de relire le journal. Chargée à l'entrée sur `Screen.Realisateurs` et pas avant : elle
     * ne sert qu'à la fiche, et l'accueil n'a pas à la payer.
     */
    val entrees: Map<String, JournalItem> = emptyMap(),
    val loading: Boolean = false,
    val error: ApiError? = null,
)

class RealisateursViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(RealisateursUi())
    val ui: StateFlow<RealisateursUi> = _ui

    /**
     * « Ajouté », « Retiré », ou le message du back quand l'un des deux échoue. Un `Channel`
     * plutôt qu'un `State`, pour la raison écrite sur `Navigator.messages` : un événement à un
     * coup ne se relit pas, et un `State` remis à `null` après lecture couperait la snackbar
     * avant ses deux secondes. Il porte aussi le message jusqu'à la liste quand l'ajout se
     * termine alors que l'écran de recherche vient à peine de se refermer.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var job: Job? = null

    // Pas d'`init { refresh() }` (jumeau de `FriseViewModel`/`FilmsViewModel`) : `Root.kt`
    // déclenche le premier chargement par `LaunchedEffect(Unit)` à l'entrée sur l'écran.

    fun refresh() {
        job?.cancel()
        _ui.update { it.copy(loading = true, error = null) }
        job = viewModelScope.launch {
            val liste = try {
                api.realisateurs()
            } catch (e: ApiError) {
                _ui.update { it.copy(loading = false, error = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }

            _ui.update { etat ->
                etat.copy(
                    realisateurs = liste,
                    // Les filmographies déjà connues restent affichées pendant leur
                    // rechargement : un rafraîchissement ne doit pas renvoyer toutes les lignes
                    // à « … ». Celles d'un réalisateur retiré disparaissent avec lui, la carte
                    // étant reconstruite sur la liste neuve et pas complétée sur l'ancienne.
                    filmographies = liste.associate { r ->
                        r.tmdb_id to (etat.filmographies[r.tmdb_id] ?: EtatFilmographie.EnAttente)
                    },
                    loading = false,
                )
            }

            // « Chargés à la suite les uns des autres » (brief du 15 septembre 2026) : une
            // filmographie après l'autre, chacune publiée dès son arrivée, plutôt que toutes
            // d'un coup — le back appelle TMDB derrière, et sa file sortante a déjà cédé une
            // fois à une rafale (correctif Allociné du 15 septembre 2026).
            liste.forEach { chargerUneFilmographie(it.tmdb_id) }
        }
    }

    /**
     * Le journal complet, pour la fiche. Appelé à l'entrée sur `Screen.Realisateurs`, d'où la
     * fiche s'ouvre — jamais depuis l'accueil, qui n'en a pas besoin. Une panne se tait : elle
     * ne coûte que l'ouverture d'une correction, pas l'écran.
     */
    fun chargerEntrees() {
        viewModelScope.launch {
            val journal = try {
                api.journalComplet()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }
            _ui.update { etat -> etat.copy(entrees = journal.associateBy { it.entry.id }) }
        }
    }

    /** Réessayer une filmographie restée « indisponible », depuis sa fiche. */
    fun rechargerFilmographie(tmdbId: Int) {
        viewModelScope.launch {
            _ui.update { it.copy(filmographies = it.filmographies + (tmdbId to EtatFilmographie.EnAttente)) }
            chargerUneFilmographie(tmdbId)
        }
    }

    fun ajouter(tmdbId: Int) {
        viewModelScope.launch {
            try {
                api.suivreRealisateur(tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "")
                return@launch
            }
            _messages.trySend("Ajouté")
            refresh()
        }
    }

    fun retirer(tmdbId: Int) {
        viewModelScope.launch {
            try {
                api.retirerRealisateur(tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "")
                return@launch
            }
            _messages.trySend("Retiré")
            refresh()
        }
    }

    private suspend fun chargerUneFilmographie(tmdbId: Int) {
        val etat = try {
            EtatFilmographie.Pret(api.filmographie(tmdbId))
        } catch (e: ApiError) {
            if (e.isUnauthenticated) onUnauthenticated()
            EtatFilmographie.Indisponible
        }
        _ui.update { it.copy(filmographies = it.filmographies + (tmdbId to etat)) }
    }
}
