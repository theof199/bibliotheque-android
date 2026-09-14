package fr.mediatheque.journal.senscritique

/** Jumeau de `FakeExternalRatingService`, au niveau GraphQL — pour tester `SensCritiqueRatingService` sans réseau. */
class FakeSensCritiqueGraphQLClient(
    var onSearch: suspend (String, String) -> ExternalSearchOutcome = { _, _ -> ExternalSearchOutcome.Success(emptyList()) },
    var onRate: suspend (String, Long, Int) -> ExternalPushOutcome = { _, _, _ -> ExternalPushOutcome.Success },
    var onMarkDone: suspend (String, Long) -> ExternalPushOutcome = { _, _ -> ExternalPushOutcome.Success },
    var onSetDate: suspend (String, Long, String) -> ExternalPushOutcome = { _, _, _ -> ExternalPushOutcome.Success },
) : SensCritiqueGraphQLClient {
    val searchCalls = mutableListOf<Pair<String, String>>()
    val rateCalls = mutableListOf<Triple<String, Long, Int>>()
    val markDoneCalls = mutableListOf<Pair<String, Long>>()
    val setDateCalls = mutableListOf<Triple<String, Long, String>>()

    override suspend fun search(cookieRef: String, keywords: String): ExternalSearchOutcome {
        searchCalls += cookieRef to keywords
        return onSearch(cookieRef, keywords)
    }

    override suspend fun rate(cookieRef: String, productId: Long, rating: Int): ExternalPushOutcome {
        rateCalls += Triple(cookieRef, productId, rating)
        return onRate(cookieRef, productId, rating)
    }

    override suspend fun markDone(cookieRef: String, productId: Long): ExternalPushOutcome {
        markDoneCalls += cookieRef to productId
        return onMarkDone(cookieRef, productId)
    }

    override suspend fun setDate(cookieRef: String, productId: Long, date: String): ExternalPushOutcome {
        setDateCalls += Triple(cookieRef, productId, date)
        return onSetDate(cookieRef, productId, date)
    }
}
