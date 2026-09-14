package fr.mediatheque.journal.senscritique

import java.time.LocalDate

sealed interface SyncResolution {
    data class Matched(val productId: Long) : SyncResolution
    data object Ignored : SyncResolution
    data class NeedsChoice(val candidates: List<ExternalCandidate>) : SyncResolution
    data object Failed : SyncResolution
    data object Unauthenticated : SyncResolution
}

sealed interface SyncPushResult {
    data object Success : SyncPushResult
    data object Failed : SyncPushResult
    data object Unauthenticated : SyncPushResult
}

/** Ce que `FormViewModel` fait du résultat, après un `create`/`edit` réussi avec une note non nulle. */
sealed interface GestureSyncResult {
    /** Pas connecté, note nulle, ou décision « Aucun de ceux-là » déjà mémorisée : aucun appel. */
    data object Skipped : GestureSyncResult
    data object Pushed : GestureSyncResult
    data object QueuedForRetry : GestureSyncResult
    data object ReconnectNeeded : GestureSyncResult
    data class ChoiceNeeded(val candidates: List<ExternalCandidate>) : GestureSyncResult
}

/**
 * L'orchestration SensCritique du brief du 14 septembre 2026 : résoudre un film (en relisant
 * d'abord les choix déjà mémorisés), pousser note + date, tenir la file des poussées échouées, la
 * rejouer au lancement. Ne dépend que de `ExternalRatingService` (l'interface générique) et de
 * `SensCritiqueStore` : aucun détail GraphQL ou Firebase ici.
 */
class SensCritiqueSync(
    private val service: ExternalRatingService,
    private val store: SensCritiqueStore,
) {
    suspend fun isConnected(): Boolean = store.readAuth() != null

    /**
     * Le point d'entrée de `FormViewModel`, après un enregistrement réussi. Ne pousse jamais avant
     * l'écriture locale : `FormViewModel` ne l'appelle qu'une fois `POST`/`PATCH` déjà passés.
     */
    suspend fun syncAfterSave(mediaId: String, film: MatchableFilm, rating: Int?, watchedOn: String): GestureSyncResult {
        if (rating == null) return GestureSyncResult.Skipped
        if (!isConnected()) return GestureSyncResult.Skipped
        return when (val resolution = resolve(mediaId, film)) {
            is SyncResolution.Matched -> finishPush(mediaId, resolution.productId, rating, watchedOn, film)
            SyncResolution.Ignored -> GestureSyncResult.Skipped
            is SyncResolution.NeedsChoice -> GestureSyncResult.ChoiceNeeded(resolution.candidates)
            SyncResolution.Failed -> { enqueueUnresolved(mediaId, film, rating, watchedOn); GestureSyncResult.QueuedForRetry }
            SyncResolution.Unauthenticated -> { enqueueUnresolved(mediaId, film, rating, watchedOn); GestureSyncResult.ReconnectNeeded }
        }
    }

    /** Après la feuille : `productId` choisi, ou `null` pour « Aucun de ceux-là ». Le choix est mémorisé, redemandé jamais. */
    suspend fun choose(mediaId: String, film: MatchableFilm, rating: Int, watchedOn: String, productId: Long?): GestureSyncResult {
        recordChoice(mediaId, productId)
        return if (productId == null) GestureSyncResult.Skipped else finishPush(mediaId, productId, rating, watchedOn, film)
    }

    /** Rejoué au lancement, une fois, silencieusement (brief §5) : ce qui redemande un choix est sauté. */
    suspend fun replayQueue() {
        if (!isConnected()) return
        for ((mediaId, queued) in store.readQueue()) {
            val film = MatchableFilm(queued.title, queued.originalTitle, queued.year)
            val productId = queued.productId
            if (productId != null) {
                if (pushAndRecord(mediaId, productId, queued.rating, queued.watchedOn, film) == SyncPushResult.Unauthenticated) return
                continue
            }
            when (val resolution = resolve(mediaId, film)) {
                is SyncResolution.Matched -> pushAndRecord(mediaId, resolution.productId, queued.rating, queued.watchedOn, film)
                SyncResolution.Ignored -> store.writeQueue(store.readQueue() - mediaId)
                is SyncResolution.NeedsChoice -> Unit // sauté : attend une correction manuelle du film
                SyncResolution.Failed -> Unit // reste en file, retentera au prochain lancement
                SyncResolution.Unauthenticated -> return // plus connecté : inutile de continuer la file
            }
        }
    }

    private suspend fun finishPush(mediaId: String, productId: Long, rating: Int, watchedOn: String, film: MatchableFilm): GestureSyncResult =
        when (pushAndRecord(mediaId, productId, rating, watchedOn, film)) {
            SyncPushResult.Success -> GestureSyncResult.Pushed
            SyncPushResult.Failed -> GestureSyncResult.QueuedForRetry
            SyncPushResult.Unauthenticated -> GestureSyncResult.ReconnectNeeded
        }

    private suspend fun resolve(mediaId: String, film: MatchableFilm): SyncResolution {
        val decisions = store.readDecisions()
        if (decisions.containsKey(mediaId)) {
            val productId = decisions.getValue(mediaId)
            return if (productId != null) SyncResolution.Matched(productId) else SyncResolution.Ignored
        }

        val premiere = service.search(film.title)
        if (premiere is ExternalSearchOutcome.Unauthenticated) { store.writeAuth(null); return SyncResolution.Unauthenticated }
        if (premiere is ExternalSearchOutcome.Failed) return SyncResolution.Failed
        premiere as ExternalSearchOutcome.Success

        var appariement = apparierCandidat(film, premiere.candidates)
        var candidatsPourChoix = premiere.candidates
        if (fautRepliOriginalTitle(film, appariement)) {
            when (val repli = service.search(film.originalTitle!!)) {
                is ExternalSearchOutcome.Success -> {
                    appariement = apparierCandidat(film, repli.candidates)
                    candidatsPourChoix = repli.candidates
                }
                ExternalSearchOutcome.Unauthenticated -> { store.writeAuth(null); return SyncResolution.Unauthenticated }
                ExternalSearchOutcome.Failed -> Unit // garde le resultat de la premiere recherche (aucun)
            }
        }

        return when (val a = appariement) {
            is Appariement.Apparie -> SyncResolution.Matched(a.candidat.productId)
            is Appariement.Ambigu -> SyncResolution.NeedsChoice(a.candidats)
            Appariement.Aucun -> SyncResolution.NeedsChoice(candidatsPourChoix)
        }
    }

    private fun recordChoice(mediaId: String, productId: Long?) {
        store.writeDecisions(store.readDecisions() + (mediaId to productId))
    }

    private suspend fun pushAndRecord(mediaId: String, productId: Long, rating: Int, watchedOn: String, film: MatchableFilm): SyncPushResult {
        val outcome = service.push(productId, rating, LocalDate.parse(watchedOn))
        when (outcome) {
            ExternalPushOutcome.Success -> store.writeQueue(store.readQueue() - mediaId)
            ExternalPushOutcome.Failed -> enqueue(mediaId, film, rating, watchedOn, productId)
            ExternalPushOutcome.Unauthenticated -> {
                enqueue(mediaId, film, rating, watchedOn, productId)
                store.writeAuth(null)
            }
        }
        return when (outcome) {
            ExternalPushOutcome.Success -> SyncPushResult.Success
            ExternalPushOutcome.Failed -> SyncPushResult.Failed
            ExternalPushOutcome.Unauthenticated -> SyncPushResult.Unauthenticated
        }
    }

    private fun enqueueUnresolved(mediaId: String, film: MatchableFilm, rating: Int, watchedOn: String) =
        enqueue(mediaId, film, rating, watchedOn, productId = null)

    private fun enqueue(mediaId: String, film: MatchableFilm, rating: Int, watchedOn: String, productId: Long?) {
        val push = QueuedPush(mediaId, film.title, film.originalTitle, film.year, rating, watchedOn, productId)
        store.writeQueue(store.readQueue() + (mediaId to push))
    }
}
