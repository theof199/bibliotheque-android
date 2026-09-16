package fr.mediatheque.journal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.JournalMedia
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.reactions.Reactions
import fr.mediatheque.journal.senscritique.ExternalCandidate
import fr.mediatheque.journal.senscritique.GestureSyncResult
import fr.mediatheque.journal.senscritique.MatchableFilm
import fr.mediatheque.journal.senscritique.SensCritiqueSync
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate

/** Le plafond de la synchronisation SensCritique (important 4 de la revue du 14 septembre 2026). */
private const val SENSCRITIQUE_SYNC_TIMEOUT_MS = 6_000L

sealed interface FormMode {
    data class Create(val result: SearchResult) : FormMode
    data class Edit(val item: JournalItem) : FormMode
}

/** L'état de départ du brouillon pour un mode — celui du formulaire vide, ou celui du visionnage à corriger. */
private fun initialUi(mode: FormMode): FormUi = when (mode) {
    is FormMode.Create -> FormUi(date = LocalDate.now())
    is FormMode.Edit -> FormUi(
        date = LocalDate.parse(mode.item.entry.finished_at),
        rating = mode.item.entry.rating,
        reactions = mode.item.carnet.reactions.toSet(),
        comment = mode.item.carnet.comment ?: "",
    )
}

/**
 * Une feuille de choix SensCritique en attente : le geste local a déjà réussi, mais la résolution a
 * rendu zéro ou plusieurs candidats (brief du 14 septembre 2026). `ui.done` n'est posé qu'une fois
 * le choix fait — c'est ce qui garde l'utilisateur sur cet écran, feuille ouverte, plutôt que de
 * repartir à l'accueil avant qu'il ait tranché.
 */
data class PendingSensCritiqueChoice(
    val mediaId: String,
    val film: MatchableFilm,
    val rating: Int,
    val watchedOn: String,
    val baseMessage: String,
    val candidates: List<ExternalCandidate>,
)

data class FormUi(
    val date: LocalDate,
    val rating: Int? = null,
    val reactions: Set<String> = emptySet(),
    val comment: String = "",
    val busy: Boolean = false,
    val error: ApiError? = null,
    /**
     * Une ligne au-dessus du message du back, seulement quand `POST /media` a
     * réussi mais `POST /me/journal` a échoué ensuite (décision 3) : le
     * message du back reste le sien, cette ligne dit juste que le film, lui,
     * est bien ajouté.
     */
    val errorContext: String? = null,
    /**
     * Le message à montrer une fois l'action terminée (« Enregistré » /
     * « Corrigé » / « Supprimé »). `FormScreen` le consomme par
     * `nav.home(...)` puis appelle `doneConsumed()` : jamais le `ViewModel`
     * lui-même, qui garderait alors une référence à un `Navigator` mort avec
     * la composition qui l'a créé, tandis que lui survit à une recréation
     * d'Activité (correction 1 de la tâche 6).
     */
    val done: String? = null,
    /** Non nul le temps que la feuille « Lequel sur SensCritique ? » attende une réponse — voir `PendingSensCritiqueChoice`. */
    val pendingSensCritiqueChoice: PendingSensCritiqueChoice? = null,
)

/** Le contexte affiché quand le film est ajouté mais pas le visionnage (décision 3 de la tâche 6). */
private const val VIEWING_FAILED_CONTEXT = "Le film est ajouté, mais pas ton visionnage."

/**
 * Marque l'échec du second appel du geste création (`POST /me/journal`, après
 * un `POST /media` déjà réussi), pour le distinguer d'une panne ordinaire :
 * son message reste celui du back (`error`), `retryable` aussi ; seule la
 * ligne de contexte au-dessus change selon ce type (décision 3). Un `400`
 * (commentaire trop long, date incohérente) affiche donc le vrai message du
 * back, sans « Réessayer » s'il n'a pas de sens à rejouer.
 */
private class ViewingFailedAfterMediaAdded(val error: ApiError) : Exception(error.message, error)

/**
 * Un geste, deux appels pour un film nouveau : l'ajout à la bibliothèque
 * commune, puis « j'ai vu ». Les deux **au moment d'enregistrer**, jamais en
 * ouvrant le formulaire — feuilleter des résultats n'ajoute rien chez les
 * autres. Si le second échoue, l'identifiant reçu est gardé et « Réessayer »
 * ne relance que lui : le premier est idempotent, mais un client qui relance
 * tout est un client qu'on ne comprend plus.
 *
 * Après un succès local avec une note non nulle, `sensCritique.syncAfterSave` tente la poussée
 * SensCritique (brief du 14 septembre 2026) — jamais avant : un échec SensCritique ne doit jamais
 * faire échouer le geste local, ni le retarder (`launch`/`create`/`edit` gardent leur forme).
 */
class FormViewModel(
    private val api: JournalApi,
    val mode: FormMode,
    private val sensCritique: SensCritiqueSync,
    private val onUnauthenticated: () -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(initialUi(mode))
    val ui: StateFlow<FormUi> = _ui

    /** L'identifiant reçu de `POST /media`, gardé si `POST /me/journal` a échoué ensuite. */
    private var pendingMediaId: String? = null

    fun setDate(date: LocalDate) = _ui.update { it.copy(date = date) }

    fun toggleRating(n: Int) = _ui.update { it.copy(rating = if (it.rating == n) null else n) }

    fun toggleReaction(key: String) = _ui.update {
        when {
            key in it.reactions -> it.copy(reactions = it.reactions - key)
            it.reactions.size >= Reactions.MAX_SELECTED -> it
            else -> it.copy(reactions = it.reactions + key)
        }
    }

    fun setComment(value: String) = _ui.update { it.copy(comment = value) }

    fun save() = run { if (!_ui.value.busy) launch { when (mode) { is FormMode.Create -> create(mode); is FormMode.Edit -> edit(mode) } } }

    fun retry() = save()

    fun delete() = run {
        val mode = mode as? FormMode.Edit ?: return@run
        launch {
            api.deleteViewing(mode.item.entry.id)
            finish("Supprimé")
        }
    }

    /** `FormScreen` a affiché `ui.done` (par `nav.home(...)`) ; on efface le signal pour ne pas le rejouer. */
    fun doneConsumed() = _ui.update { it.copy(done = null) }

    /**
     * La feuille « Lequel sur SensCritique ? » a rendu son choix — `productId` du candidat touché,
     * ou `null` pour « Aucun de ceux-là ». Mémorisé par `sensCritique.choose` : ne sera plus
     * redemandé pour ce film (brief §2).
     */
    fun chooseSensCritiqueCandidate(productId: Long?) {
        val pending = _ui.value.pendingSensCritiqueChoice ?: return
        _ui.update { it.copy(pendingSensCritiqueChoice = null, busy = true) }
        viewModelScope.launch {
            val resultat = filetDeSecurite {
                withTimeoutOrNull(SENSCRITIQUE_SYNC_TIMEOUT_MS) {
                    sensCritique.choose(pending.mediaId, pending.film, pending.rating, pending.watchedOn, productId)
                } ?: sensCritique.abandon(pending.mediaId, pending.film, pending.rating, pending.watchedOn)
            }
            finish(pending.baseMessage + suffixFor(resultat))
        }
    }

    /**
     * La feuille a été quittée sans réponse (retour système — revue du 14 septembre 2026,
     * critique 2) : `ModalBottomSheet` la referme quoi qu'il arrive, `onDismissRequest` ne peut pas
     * l'en empêcher. Sans ce chemin, `pendingSensCritiqueChoice` restait non nul, `done` n'était
     * jamais posé, et le geste restait bloqué alors que l'entrée était déjà écrite au back. La
     * poussée part en file (sans `productId`, elle redemandera un choix à la prochaine correction).
     */
    fun abandonSensCritiqueChoice() {
        val pending = _ui.value.pendingSensCritiqueChoice ?: return
        _ui.update { it.copy(pendingSensCritiqueChoice = null, busy = true) }
        viewModelScope.launch {
            val resultat = filetDeSecurite { sensCritique.abandon(pending.mediaId, pending.film, pending.rating, pending.watchedOn) }
            finish(pending.baseMessage + suffixFor(resultat))
        }
    }

    /**
     * Filet de sécurité (important 5 de la revue du 14 septembre 2026) : `SensCritiqueSync` n'est
     * plus censé laisser fuiter d'exception, mais si l'une le faisait quand même, aucun appelant ne
     * doit planter — le geste finit avec le message de réessai habituel plutôt que de laisser
     * `pendingSensCritiqueChoice` déjà effacé sans jamais poser `done`.
     */
    private suspend fun filetDeSecurite(bloc: suspend () -> GestureSyncResult): GestureSyncResult =
        try {
            bloc()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            GestureSyncResult.QueuedForRetry
        }

    private suspend fun create(mode: FormMode.Create) {
        val mediaId = pendingMediaId ?: api.addMedia(mode.result).media.id.also { pendingMediaId = it }
        try {
            api.addViewing(JournalCreateBody(mediaId, date(), rating(), reactions(), comment()))
        } catch (e: ApiError) {
            if (e.isUnauthenticated) throw e
            throw ViewingFailedAfterMediaAdded(e)
        }
        syncSensCritique(mediaId, filmOf(mode.result), "Enregistré")
    }

    private suspend fun edit(mode: FormMode.Edit) {
        api.patchViewing(mode.item.entry.id, patchBodyOf(mode.item, _ui.value))
        syncSensCritique(mode.item.media.id, filmOf(mode.item.media), "Corrigé")
    }

    /**
     * Résout et pousse la note vers SensCritique si connecté et noté (brief §2 et §3) ; sinon
     * termine tout de suite, message inchangé. Une résolution ambiguë (`ChoiceNeeded`) ne termine
     * pas le geste : elle pose `pendingSensCritiqueChoice`, et c'est `chooseSensCritiqueCandidate`
     * qui appelle `finish` ensuite, une fois la feuille répondue.
     *
     * Plafonné à 6 s (important 4 de la revue du 14 septembre 2026) : jusqu'à quatre allers-retours
     * réseau à 10 s chacun (recherche, repli sur l'originalTitle, note, « vu ») tenaient `busy` vrai
     * bien au-delà de ce qu'un geste d'enregistrement doit attendre. Au-delà du délai, la poussée
     * part en file — `SensCritiqueSync` respecte l'annulation (`CancellationException` y est
     * toujours relancée), donc `withTimeoutOrNull` rend bien `null` plutôt que de laisser le réseau
     * continuer en arrière-plan sans que personne ne l'attende.
     */
    private suspend fun syncSensCritique(mediaId: String, film: MatchableFilm, baseMessage: String) {
        val note = rating()
        val quand = date()
        val resultat = filetDeSecurite {
            withTimeoutOrNull(SENSCRITIQUE_SYNC_TIMEOUT_MS) { sensCritique.syncAfterSave(mediaId, film, note, quand) }
                // `syncAfterSave` ne prend plus de 6 s que lorsqu'il a d'abord vérifié `note != null`
                // (sinon il rend `Skipped` sans réseau, bien avant le délai) : ce `!!` porte cet
                // ordre, pas un pari — jumeau de celui plus bas sur `PendingSensCritiqueChoice`.
                ?: sensCritique.abandon(mediaId, film, note!!, quand)
        }
        when (resultat) {
            is GestureSyncResult.ChoiceNeeded -> _ui.update {
                it.copy(
                    busy = false,
                    pendingSensCritiqueChoice = PendingSensCritiqueChoice(mediaId, film, note!!, quand, baseMessage, resultat.candidates),
                )
            }
            else -> finish(baseMessage + suffixFor(resultat))
        }
    }

    private fun suffixFor(resultat: GestureSyncResult): String = when (resultat) {
        GestureSyncResult.Skipped -> ""
        GestureSyncResult.Pushed -> " · SensCritique ✓"
        GestureSyncResult.QueuedForRetry -> " · SensCritique : réessai au prochain lancement"
        GestureSyncResult.ReconnectNeeded -> " · SensCritique : reconnecte-toi"
        is GestureSyncResult.ChoiceNeeded -> "" // n'arrive jamais ici : traité à part dans `syncSensCritique`
    }

    private fun filmOf(result: SearchResult) = MatchableFilm(result.title, result.original_title, result.year)
    private fun filmOf(media: JournalMedia) = MatchableFilm(media.title, null, media.year)

    /**
     * Ce `ViewModel` reste en vie, indexé sur le film ou l'entrée (décision 5) : sans ce retour au
     * brouillon initial, une réouverture ressortirait la note, les réactions, le commentaire — et
     * l'identifiant de média en attente — de l'action qui vient de réussir (correction 1 de la
     * tâche 6).
     */
    private fun finish(message: String) {
        pendingMediaId = null
        _ui.update { initialUi(mode).copy(done = message) }
    }

    private fun date() = _ui.value.date.toString()
    private fun rating() = _ui.value.rating
    // Correctif du 16 septembre 2026 : `null` quand aucune réaction n'est
    // cochée, jamais `[]` — le POST omet alors la clé, comme pour `comment`
    // ci-dessous, au lieu de vider un carnet déjà écrit ce jour-là.
    private fun reactions() = Reactions.ordered(_ui.value.reactions).ifEmpty { null }
    private fun comment() = _ui.value.comment.trim().ifEmpty { null }

    private fun launch(block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, error = null, errorContext = null, pendingSensCritiqueChoice = null) }
        viewModelScope.launch {
            try {
                block()
                _ui.update { it.copy(busy = false) }
            } catch (e: ViewingFailedAfterMediaAdded) {
                _ui.update { it.copy(busy = false, error = e.error, errorContext = VIEWING_FAILED_CONTEXT) }
            } catch (e: ApiError) {
                _ui.update { it.copy(busy = false, error = if (e.isUnauthenticated) null else e) }
                if (e.isUnauthenticated) onUnauthenticated()
            }
        }
    }
}
