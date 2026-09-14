package fr.mediatheque.journal.senscritique

import java.time.LocalDate

/** Jumeau de `FakeJournalApi` : un `ExternalRatingService` programmable, qui trace ses appels. */
class FakeExternalRatingService(
    var onSearch: suspend (String) -> ExternalSearchOutcome = { ExternalSearchOutcome.Success(emptyList()) },
    var onPush: suspend (Long, Int, LocalDate) -> ExternalPushOutcome = { _, _, _ -> ExternalPushOutcome.Success },
) : ExternalRatingService {
    override val name: String = "Test"
    val searchCalls = mutableListOf<String>()
    val pushCalls = mutableListOf<Triple<Long, Int, LocalDate>>()

    override suspend fun search(keywords: String): ExternalSearchOutcome {
        searchCalls += keywords
        return onSearch(keywords)
    }

    override suspend fun push(productId: Long, rating: Int, watchedOn: LocalDate): ExternalPushOutcome {
        pushCalls += Triple(productId, rating, watchedOn)
        return onPush(productId, rating, watchedOn)
    }
}
