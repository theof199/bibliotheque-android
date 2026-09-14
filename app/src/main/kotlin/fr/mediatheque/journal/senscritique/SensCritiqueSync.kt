package fr.mediatheque.journal.senscritique

import kotlinx.coroutines.CancellationException
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
 * `SensCritiqueStore` : aucun détail GraphQL ni de connexion ici.
 *
 * Chaque point d'entrée public attrape toute exception inattendue (revue du 14 septembre 2026,
 * important 5) : ni `FormViewModel` ni `SessionViewModel` ne doivent jamais planter parce que cette
 * couche a levé — une exception s'y lit comme un échec ordinaire, mise en file et message
 * « réessai » compris.
 */
class SensCritiqueSync(
    private val service: ExternalRatingService,
    private val store: SensCritiqueStore,
    private val logger: SensCritiqueLogger = AndroidSensCritiqueLogger,
) {
    suspend fun isConnected(): Boolean = store.readAuth() != null

    /**
     * Le point d'entrée de `FormViewModel`, après un enregistrement réussi. Ne pousse jamais avant
     * l'écriture locale : `FormViewModel` ne l'appelle qu'une fois `POST`/`PATCH` déjà passés.
     */
    suspend fun syncAfterSave(mediaId: String, film: MatchableFilm, rating: Int?, watchedOn: String): GestureSyncResult {
        if (rating == null) return GestureSyncResult.Skipped
        return try {
            if (!isConnected()) {
                GestureSyncResult.Skipped
            } else {
                when (val resolution = resolve(mediaId, film)) {
                    is SyncResolution.Matched -> finishPush(mediaId, resolution.productId, rating, watchedOn, film)
                    SyncResolution.Ignored -> GestureSyncResult.Skipped
                    is SyncResolution.NeedsChoice -> GestureSyncResult.ChoiceNeeded(resolution.candidates)
                    SyncResolution.Failed -> { enqueueUnresolved(mediaId, film, rating, watchedOn); GestureSyncResult.QueuedForRetry }
                    SyncResolution.Unauthenticated -> { enqueueUnresolved(mediaId, film, rating, watchedOn); GestureSyncResult.ReconnectNeeded }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d("syncAfterSave a leve une exception pour $mediaId, mise en file : ${e.message}")
            runCatching { enqueueUnresolved(mediaId, film, rating, watchedOn) }
            GestureSyncResult.QueuedForRetry
        }
    }

    /** Après la feuille : `productId` choisi, ou `null` pour « Aucun de ceux-là ». Le choix est mémorisé, redemandé jamais. */
    suspend fun choose(mediaId: String, film: MatchableFilm, rating: Int, watchedOn: String, productId: Long?): GestureSyncResult =
        try {
            recordChoice(mediaId, productId)
            if (productId == null) GestureSyncResult.Skipped else finishPush(mediaId, productId, rating, watchedOn, film)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d("choose a leve une exception pour $mediaId, mise en file : ${e.message}")
            runCatching { enqueue(mediaId, film, rating, watchedOn, productId) }
            GestureSyncResult.QueuedForRetry
        }

    /**
     * Renonce à résoudre ou pousser tout de suite — la feuille de choix quittée sans réponse
     * (retour système, critique 2 de la revue du 14 septembre 2026) ou le délai de synchronisation
     * dépassé (important 4) : aucun choix n'est mémorisé, la poussée part en file sans `productId`
     * — elle sera redemandée à la prochaine correction du film, exactement comme une résolution
     * restée `NeedsChoice` au rejeu (`replayQueue`, plus bas).
     */
    suspend fun abandon(mediaId: String, film: MatchableFilm, rating: Int, watchedOn: String): GestureSyncResult {
        runCatching { enqueueUnresolved(mediaId, film, rating, watchedOn) }
        return GestureSyncResult.QueuedForRetry
    }

    /**
     * Rejoué au lancement, une fois, silencieusement (brief §5) : ce qui redemande un choix est
     * sauté. Une entrée illisible (important 5 — une date corrompue, par exemple) est retirée de la
     * file plutôt que de bloquer les suivantes.
     */
    suspend fun replayQueue() {
        val connecte = try {
            isConnected()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            logger.d("replayQueue a leve une exception a la lecture de la connexion : ${e.message}")
            false
        }
        if (!connecte) return
        for ((mediaId, queued) in store.readQueue()) {
            try {
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
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                logger.d("entree de file illisible pour $mediaId, retiree : ${e.message}")
                runCatching { store.writeQueue(store.readQueue() - mediaId) }
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

    /**
     * `watchedOn` vient toujours d'un `LocalDate` frais côté `FormViewModel` — sauf ici, relu
     * depuis la file (important 5) : une entrée corrompue (format de date change, blob partiel) ne
     * doit ni planter ni bloquer les autres, elle est retirée.
     */
    private suspend fun pushAndRecord(mediaId: String, productId: Long, rating: Int, watchedOn: String, film: MatchableFilm): SyncPushResult {
        val date = runCatching { LocalDate.parse(watchedOn) }.getOrNull()
        if (date == null) {
            logger.d("date illisible en file pour $mediaId, entree retiree")
            runCatching { store.writeQueue(store.readQueue() - mediaId) }
            return SyncPushResult.Failed
        }
        val outcome = service.push(productId, rating, date)
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
