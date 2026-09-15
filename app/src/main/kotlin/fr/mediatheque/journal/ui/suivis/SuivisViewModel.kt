package fr.mediatheque.journal.ui.suivis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.FilmSuivi
import fr.mediatheque.journal.api.dto.JournalItem
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
 * Les réalisateurs et les sagas suivis (brief du 15 septembre 2026, jumeau du
 * brief des réalisateurs du même jour) : ceux que le propriétaire suit, à la
 * main, de deux sources différentes, et ce qu'il lui reste à voir de chacune.
 *
 * **Une seule instance pour les deux segments de l'écran Suivis, leurs
 * fiches, et les deux lignes « Ensuite » de l'accueil** — `Root.kt` la tient
 * sous la clé `"suivis"`. L'état de chaque source est tenu **séparément**
 * (`SuivisUi.realisateurs`, `SuivisUi.sagas`) : leurs espaces d'identifiants
 * TMDB se recoupent (une personne et une collection peuvent partager un même
 * entier), fusionner les deux cartes de filmographies ferait écraser l'une
 * par l'autre. C'est aussi ce qui permet à l'accueil de calculer les deux
 * « Ensuite » sans attendre que l'écran Suivis ait été ouvert.
 *
 * **Le seul endroit qui distingue les deux sources** est le petit bloc de
 * fonctions privées `*Pour(source, ...)`, en bas de ce fichier : quatre appels
 * réseau, un `when` chacun — le cinquième, la recherche, vit dans
 * `ChercherSuiviViewModel`, indépendant, comme l'était
 * `ChercherRealisateurViewModel`. Tout le reste — `refresh`, `ajouter`,
 * `retirer`, `marquerIntrouvable`, la fiche — est **un seul** code, comme le
 * veut le brief : « paramétré par une source, pas une copie ».
 *
 * Un film peut aussi être marqué introuvable (décision du propriétaire du
 * 15 septembre 2026) : `prochainAVoir` l'ignore, comme la pastille « à voir »
 * et le « Ensuite » de l'accueil qui s'en déduisent tous deux — pour les deux
 * sources, la marque est la même (`user_unfindable_films` côté back).
 *
 * Une saga suivie peut aussi recevoir des films ajoutés à la main (brief « les
 * films de saga ajoutés à la main », 15 septembre 2026 : une collection TMDB
 * n'est pas toujours complète) — `ajouterFilm`/`retirerFilm`, jumeaux de
 * `marquerIntrouvable`/`retirerIntrouvable` mais sans `source`, puisque cette
 * paire n'existe que pour les sagas.
 */

/**
 * Ce qu'on sait de la filmographie (ou des films d'une saga) d'une entité
 * suivie. `Indisponible` n'est pas une erreur d'écran : une panne ne doit
 * priver ni la liste des autres, ni l'écran de son affichage — la ligne dit
 * « indisponible » et la suivante continue de se charger.
 */
sealed interface EtatFilmographie {
    data object EnAttente : EtatFilmographie
    data class Pret(val films: List<FilmSuivi>) : EtatFilmographie
    data object Indisponible : EtatFilmographie
}

/** L'entité « en cours » de l'accueil (un réalisateur ou une saga), et le film qu'il reste à voir d'elle. */
data class EnCours(val source: SourceSuivi, val entite: EntiteSuivie, val prochain: FilmSuivi)

/** Combien de films de cette liste j'ai déjà journalisés. */
fun filmsVus(films: List<FilmSuivi>): Int = films.count { it.vu != null }

/** Combien de films de cette liste j'ai moi-même marqués introuvables. */
fun filmsIntrouvables(films: List<FilmSuivi>): Int = films.count { it.introuvable }

/**
 * Le prochain film à voir : le premier que je n'ai pas vu **et que je n'ai
 * pas marqué introuvable** (décision du propriétaire du 15 septembre 2026),
 * dans l'ordre où le back les rend — de la plus ancienne sortie à la plus
 * récente. Nul quand il n'y a plus rien à voir.
 */
fun prochainAVoir(films: List<FilmSuivi>): FilmSuivi? =
    films.firstOrNull { it.vu == null && !it.introuvable }

/** « Lolita (1962) », ou « Lolita » tout court si TMDB n'a pas d'année pour lui. */
fun titreEtAnnee(film: FilmSuivi): String = film.year?.let { "${film.title} ($it)" } ?: film.title

/**
 * « Retirer de la saga » n'a de sens que sur un film ajouté à la main, et
 * seulement dans une saga (brief « les films de saga ajoutés à la main »,
 * 15 septembre 2026) : rien de tout cela sur une fiche de réalisateur, qui
 * n'a pas cette route côté back — `film.ajoute` y est de toute façon toujours
 * faux (`FilmSuivi.ajoute` par défaut), mais la source est vérifiée en plus,
 * explicitement, plutôt que de s'y fier seule.
 */
fun peutRetirerDeSaga(source: SourceSuivi, film: FilmSuivi): Boolean =
    source == SourceSuivi.SAGAS && film.ajoute

/**
 * La ligne sous le nom, dans la liste : « 7 vus sur 13 · 2 introuvables ·
 * prochain : Lolita (1962) ». Le total compte tous les films, introuvables
 * compris ; la part « · N introuvables » n'apparaît que s'il y en a au moins
 * un ; la part « prochain » disparaît quand il n'y a plus rien à voir — le
 * compte, lui, reste toujours.
 */
fun resumeFilmographie(films: List<FilmSuivi>): String {
    val introuvables = filmsIntrouvables(films)
    val compte = "${filmsVus(films)} vus sur ${films.size}" + if (introuvables > 0) " · $introuvables introuvables" else ""
    val prochain = prochainAVoir(films) ?: return compte
    return "$compte · prochain : ${titreEtAnnee(prochain)}"
}

/**
 * Ce que dit la ligne, quel que soit l'état de sa liste : « … » tant que sa
 * réponse n'est pas là, « indisponible » quand elle a échoué (l'écran, lui,
 * reste entier).
 */
fun libelleLigne(etat: EtatFilmographie): String = when (etat) {
    EtatFilmographie.EnAttente -> "…"
    EtatFilmographie.Indisponible -> "indisponible"
    is EtatFilmographie.Pret -> resumeFilmographie(etat.films)
}

/**
 * L'entité « en cours » d'une source pour l'accueil : celle qui a le plus de
 * films vus **parmi celles qui ont encore au moins un film non vu**. Nulle si
 * aucune n'est dans ce cas — la ligne « Ensuite » correspondante est alors
 * absente de l'accueil, plutôt qu'affichée vide.
 *
 * Une liste encore en attente ou indisponible ne participe pas. À égalité de
 * films vus, c'est la première de la liste qui gagne — `GET /me/realisateurs`
 * et `GET /me/sagas` la rendent du plus récemment ajouté au plus ancien, donc
 * la dernière ajoutée ; `maxByOrNull` garde le premier maximum rencontré,
 * jamais le dernier.
 */
fun entiteEnCours(
    source: SourceSuivi,
    entites: List<EntiteSuivie>,
    filmographies: Map<Int, EtatFilmographie>,
): EnCours? {
    val candidats = entites.mapNotNull { entite ->
        val films = (filmographies[entite.tmdbId] as? EtatFilmographie.Pret)?.films ?: return@mapNotNull null
        val prochain = prochainAVoir(films) ?: return@mapNotNull null
        Triple(entite, prochain, filmsVus(films))
    }
    val gagnant = candidats.maxByOrNull { it.third } ?: return null
    return EnCours(source, gagnant.first, gagnant.second)
}

/**
 * Le même formulaire pré-rempli que « Au ciné » et la Frise
 * (`PlexFilm.toSearchResult()`) : mêmes tuiles, même geste. `metadata.director`
 * ne porte le nom que sur la source des réalisateurs — une saga n'en est pas
 * un, lui en prêter un donnerait « Réalisé par Alien (Saga) ».
 */
fun FilmSuivi.toSearchResult(nomDuRealisateur: String? = null): SearchResult = SearchResult(
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
 * Le formulaire pré-rempli derrière la ligne « Ensuite » de l'entité en
 * cours. Nommé à part plutôt qu'appelé depuis `Root.kt` : celui-ci importe
 * déjà un `toSearchResult` (celui de la Frise, sur `PlexFilm`), et deux
 * extensions homonymes dans un même fichier se lisent mal, même quand le
 * compilateur, lui, sait les distinguer par leur receveur.
 */
fun EnCours.formulaire(): SearchResult =
    prochain.toSearchResult(if (source == SourceSuivi.REALISATEURS) entite.nom else null)

/** L'état d'une des deux sources : sa liste, ses filmographies, son chargement, son erreur. */
data class SuiviState(
    val entites: List<EntiteSuivie> = emptyList(),
    /** Une entrée par élément de `entites`, dans l'un des trois états ci-dessus. */
    val filmographies: Map<Int, EtatFilmographie> = emptyMap(),
    val loading: Boolean = false,
    val error: ApiError? = null,
)

/**
 * Vrai une fois que toutes les filmographies connues de cette source ont
 * répondu (`Pret` ou `Indisponible`, jamais `EnAttente`) et que la liste
 * elle-même n'est plus en chargement — le repère dont se sert le Bilan du
 * profil pour savoir s'il peut compter les entités « terminées », plutôt que
 * d'afficher un compte provisoire qui grimperait sous les yeux.
 */
fun SuiviState.pret(): Boolean = !loading && filmographies.values.none { it is EtatFilmographie.EnAttente }

data class SuivisUi(
    val realisateurs: SuiviState = SuiviState(),
    val sagas: SuiviState = SuiviState(),
    /**
     * Mes entrées de journal par `entry_id`, pour ouvrir la correction d'un
     * film vu depuis une fiche — partagées entre les deux sources, un seul
     * appel. Le contrat n'expose aucun `GET /me/journal/{id}` (seulement
     * `PATCH` et `DELETE`) : la seule façon de retrouver une entrée entière —
     * ses réactions et sa remarque comprises — est de relire le journal.
     * Chargée à l'entrée sur `Screen.Suivis` et pas avant : elle ne sert
     * qu'aux fiches, et l'accueil n'a pas à la payer.
     */
    val entrees: Map<String, JournalItem> = emptyMap(),
    /**
     * L'interrupteur « Masquer les introuvables » de la fiche (décision du
     * propriétaire du 15 septembre 2026), activé par défaut, partagé entre
     * les deux sources — c'est un réglage d'affichage, pas une donnée de
     * l'une ou de l'autre. Ici et non dans un `remember` local à l'écran : il
     * doit survivre à une sortie puis un retour sur la fiche, tant que
     * l'application tourne.
     */
    val masquerIntrouvables: Boolean = true,
    /** Le segment sélectionné dans `SuivisScreen`, mémorisé pour la session (brief du 15 septembre 2026). */
    val source: SourceSuivi = SourceSuivi.REALISATEURS,
)

class SuivisViewModel(private val api: JournalApi, private val onUnauthenticated: () -> Unit) : ViewModel() {
    private val _ui = MutableStateFlow(SuivisUi())
    val ui: StateFlow<SuivisUi> = _ui

    /**
     * « Ajouté », « Retiré », ou le message du back quand l'un des deux
     * échoue. Un `Channel` plutôt qu'un `State`, pour la raison écrite sur
     * `Navigator.messages` : un événement à un coup ne se relit pas, et un
     * `State` remis à `null` après lecture couperait la snackbar avant ses
     * deux secondes. Il porte aussi le message jusqu'à la liste quand l'ajout
     * se termine alors que l'écran de recherche vient à peine de se refermer.
     */
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    private var jobRealisateurs: Job? = null
    private var jobSagas: Job? = null

    // Pas d'`init { refresh(...) }` (jumeau de `FriseViewModel`/`FilmsViewModel`) : `Root.kt`
    // déclenche le premier chargement par `LaunchedEffect(Unit)` à l'entrée sur l'écran.

    fun selectionnerSource(source: SourceSuivi) {
        _ui.update { it.copy(source = source) }
    }

    private fun etatDe(ui: SuivisUi, source: SourceSuivi): SuiviState =
        if (source == SourceSuivi.REALISATEURS) ui.realisateurs else ui.sagas

    private fun avecEtat(ui: SuivisUi, source: SourceSuivi, etat: SuiviState): SuivisUi =
        if (source == SourceSuivi.REALISATEURS) ui.copy(realisateurs = etat) else ui.copy(sagas = etat)

    fun refresh(source: SourceSuivi) {
        val job = viewModelScope.launch {
            _ui.update { avecEtat(it, source, etatDe(it, source).copy(loading = true, error = null)) }

            val liste = try {
                listePour(source)
            } catch (e: ApiError) {
                _ui.update {
                    avecEtat(it, source, etatDe(it, source).copy(loading = false, error = if (e.isUnauthenticated) null else e))
                }
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }

            _ui.update { ui ->
                val etat = etatDe(ui, source)
                avecEtat(
                    ui,
                    source,
                    etat.copy(
                        entites = liste,
                        // Les filmographies déjà connues restent affichées pendant leur
                        // rechargement : un rafraîchissement ne doit pas renvoyer toutes les
                        // lignes à « … ». Celles d'une entité retirée disparaissent avec elle,
                        // la carte étant reconstruite sur la liste neuve et pas complétée sur
                        // l'ancienne.
                        filmographies = liste.associate { e -> e.tmdbId to (etat.filmographies[e.tmdbId] ?: EtatFilmographie.EnAttente) },
                        loading = false,
                    ),
                )
            }

            // « Chargées à la suite les unes des autres » (brief du 15 septembre 2026) : une
            // filmographie après l'autre, chacune publiée dès son arrivée, plutôt que toutes
            // d'un coup — le back appelle TMDB derrière, et sa file sortante a déjà cédé une
            // fois à une rafale (correctif Allociné du 15 septembre 2026).
            liste.forEach { chargerUneFilmographie(source, it.tmdbId) }
        }
        if (source == SourceSuivi.REALISATEURS) {
            jobRealisateurs?.cancel()
            jobRealisateurs = job
        } else {
            jobSagas?.cancel()
            jobSagas = job
        }
    }

    /**
     * Le journal complet, pour les fiches des deux sources. Appelé à l'entrée
     * sur `Screen.Suivis`, d'où les fiches s'ouvrent — jamais depuis
     * l'accueil, qui n'en a pas besoin. Une panne se tait : elle ne coûte que
     * l'ouverture d'une correction, pas l'écran.
     */
    fun chargerEntrees() {
        viewModelScope.launch {
            val journal = try {
                api.journalComplet()
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated()
                return@launch
            }
            _ui.update { it.copy(entrees = journal.associateBy { item -> item.entry.id }) }
        }
    }

    /** Réessayer une filmographie restée « indisponible », depuis sa fiche. */
    fun rechargerFilmographie(source: SourceSuivi, tmdbId: Int) {
        viewModelScope.launch {
            _ui.update { avecEtat(it, source, etatDe(it, source).let { e -> e.copy(filmographies = e.filmographies + (tmdbId to EtatFilmographie.EnAttente)) }) }
            chargerUneFilmographie(source, tmdbId)
        }
    }

    fun ajouter(source: SourceSuivi, tmdbId: Int) {
        viewModelScope.launch {
            try {
                suivrePour(source, tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "")
                return@launch
            }
            _messages.trySend("Ajouté")
            refresh(source)
        }
    }

    fun retirer(source: SourceSuivi, tmdbId: Int) {
        viewModelScope.launch {
            try {
                retirerPour(source, tmdbId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend(e.message ?: "")
                return@launch
            }
            _messages.trySend("Retiré")
            refresh(source)
        }
    }

    fun basculerMasquerIntrouvables() {
        _ui.update { it.copy(masquerIntrouvables = !it.masquerIntrouvables) }
    }

    /**
     * Marquer ou retirer la marque « introuvable » (décision du propriétaire
     * du 15 septembre 2026), depuis une fiche. `PUT`/`DELETE`, puis la
     * filmographie de cette source et cette entité se recharge ; un échec ne
     * touche à rien d'autre qu'un bandeau — ni la marque affichée, ni le
     * reste de l'écran.
     */
    fun marquerIntrouvable(source: SourceSuivi, tmdbId: Int, filmId: Int) {
        viewModelScope.launch {
            try {
                api.marquerIntrouvable(filmId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend("Impossible pour l’instant")
                return@launch
            }
            chargerUneFilmographie(source, tmdbId)
        }
    }

    fun retirerIntrouvable(source: SourceSuivi, tmdbId: Int, filmId: Int) {
        viewModelScope.launch {
            try {
                api.retirerIntrouvable(filmId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend("Impossible pour l’instant")
                return@launch
            }
            chargerUneFilmographie(source, tmdbId)
        }
    }

    /**
     * Ajouter un film absent de la collection à une saga suivie (brief « les
     * films de saga ajoutés à la main », 15 septembre 2026) — sans `source`,
     * à la différence de `marquerIntrouvable`/`retirerIntrouvable` : la route
     * n'existe que pour les sagas, jamais pour un réalisateur.
     */
    fun ajouterFilm(tmdbId: Int, filmId: Int) {
        viewModelScope.launch {
            try {
                api.ajouterFilmSaga(tmdbId, filmId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend("Impossible pour l’instant")
                return@launch
            }
            _messages.trySend("Ajouté à la saga")
            chargerUneFilmographie(SourceSuivi.SAGAS, tmdbId)
        }
    }

    /** L'inverse de `ajouterFilm` — jumeau de `retirerIntrouvable`. */
    fun retirerFilm(tmdbId: Int, filmId: Int) {
        viewModelScope.launch {
            try {
                api.retirerFilmSaga(tmdbId, filmId)
            } catch (e: ApiError) {
                if (e.isUnauthenticated) onUnauthenticated() else _messages.trySend("Impossible pour l’instant")
                return@launch
            }
            _messages.trySend("Retiré de la saga")
            chargerUneFilmographie(SourceSuivi.SAGAS, tmdbId)
        }
    }

    private suspend fun chargerUneFilmographie(source: SourceSuivi, tmdbId: Int) {
        val etat = try {
            EtatFilmographie.Pret(filmsPour(source, tmdbId))
        } catch (e: ApiError) {
            if (e.isUnauthenticated) onUnauthenticated()
            EtatFilmographie.Indisponible
        }
        _ui.update { avecEtat(it, source, etatDe(it, source).let { e -> e.copy(filmographies = e.filmographies + (tmdbId to etat)) }) }
    }

    // -------------------------------------------------------------------------
    // Le seul endroit qui distingue les deux sources ici : quatre appels réseau — le cinquième,
    // la recherche, est dans `ChercherSuiviViewModel`, indépendant comme l'était
    // `ChercherRealisateurViewModel`.
    // -------------------------------------------------------------------------

    private suspend fun listePour(source: SourceSuivi): List<EntiteSuivie> = when (source) {
        SourceSuivi.REALISATEURS -> api.realisateurs().map { it.versEntite() }
        SourceSuivi.SAGAS -> api.sagas().map { it.versEntite() }
    }

    private suspend fun suivrePour(source: SourceSuivi, tmdbId: Int): EntiteSuivie = when (source) {
        SourceSuivi.REALISATEURS -> api.suivreRealisateur(tmdbId).versEntite()
        SourceSuivi.SAGAS -> api.suivreSaga(tmdbId).versEntite()
    }

    private suspend fun retirerPour(source: SourceSuivi, tmdbId: Int) {
        when (source) {
            SourceSuivi.REALISATEURS -> api.retirerRealisateur(tmdbId)
            SourceSuivi.SAGAS -> api.retirerSaga(tmdbId)
        }
    }

    private suspend fun filmsPour(source: SourceSuivi, tmdbId: Int): List<FilmSuivi> = when (source) {
        SourceSuivi.REALISATEURS -> api.filmographie(tmdbId)
        SourceSuivi.SAGAS -> api.filmsDeSaga(tmdbId)
    }
}
