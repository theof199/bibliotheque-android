package fr.mediatheque.journal.ui.form

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import fr.mediatheque.journal.api.ApiError
import fr.mediatheque.journal.api.JournalApi
import fr.mediatheque.journal.api.dto.JournalCreateBody
import fr.mediatheque.journal.api.dto.JournalItem
import fr.mediatheque.journal.api.dto.SearchResult
import fr.mediatheque.journal.reactions.Reactions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

sealed interface FormMode {
    data class Create(val result: SearchResult) : FormMode
    data class Edit(val item: JournalItem) : FormMode
}

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
 */
class FormViewModel(
    private val api: JournalApi,
    val mode: FormMode,
    private val onUnauthenticated: () -> Unit,
    private val onDone: (message: String) -> Unit,
) : ViewModel() {

    private val _ui = MutableStateFlow(
        when (mode) {
            is FormMode.Create -> FormUi(date = LocalDate.now())
            is FormMode.Edit -> FormUi(
                date = LocalDate.parse(mode.item.entry.finished_at),
                rating = mode.item.entry.rating,
                reactions = mode.item.carnet.reactions.toSet(),
                comment = mode.item.carnet.comment ?: "",
            )
        },
    )
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
            onDone("Supprimé")
        }
    }

    private suspend fun create(mode: FormMode.Create) {
        val mediaId = pendingMediaId ?: api.addMedia(mode.result).media.id.also { pendingMediaId = it }
        try {
            api.addViewing(JournalCreateBody(mediaId, date(), rating(), reactions(), comment()))
        } catch (e: ApiError) {
            if (e.isUnauthenticated) throw e
            throw ViewingFailedAfterMediaAdded(e)
        }
        onDone("Enregistré")
    }

    private suspend fun edit(mode: FormMode.Edit) {
        api.patchViewing(mode.item.entry.id, patchBodyOf(mode.item, _ui.value))
        onDone("Corrigé")
    }

    private fun date() = _ui.value.date.toString()
    private fun rating() = _ui.value.rating
    private fun reactions() = Reactions.ordered(_ui.value.reactions)
    private fun comment() = _ui.value.comment.trim().ifEmpty { null }

    private fun launch(block: suspend () -> Unit) {
        _ui.update { it.copy(busy = true, error = null, errorContext = null) }
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
