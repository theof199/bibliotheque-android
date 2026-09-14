package fr.mediatheque.journal.senscritique

/** Jumeau de `FakeExternalRatingService`, au niveau GraphQL — pour tester `FirebaseSensCritiqueAuthClient` et `SensCritiqueRatingService` sans réseau. */
class FakeSensCritiqueGraphQLClient(
    var onWhoAmI: suspend (String, String) -> Boolean = { _, _ -> true },
    var onSearch: suspend (String, String) -> ExternalSearchOutcome = { _, _ -> ExternalSearchOutcome.Success(emptyList()) },
    var onRate: suspend (String, Long, Int) -> ExternalPushOutcome = { _, _, _ -> ExternalPushOutcome.Success },
    var onMarkDone: suspend (String, Long) -> ExternalPushOutcome = { _, _ -> ExternalPushOutcome.Success },
) : SensCritiqueGraphQLClient {
    val whoAmICalls = mutableListOf<Pair<String, String>>()
    val searchCalls = mutableListOf<Pair<String, String>>()
    val rateCalls = mutableListOf<Triple<String, Long, Int>>()
    val markDoneCalls = mutableListOf<Pair<String, Long>>()

    override suspend fun search(idToken: String, keywords: String): ExternalSearchOutcome {
        searchCalls += idToken to keywords
        return onSearch(idToken, keywords)
    }

    override suspend fun rate(idToken: String, productId: Long, rating: Int): ExternalPushOutcome {
        rateCalls += Triple(idToken, productId, rating)
        return onRate(idToken, productId, rating)
    }

    override suspend fun markDone(idToken: String, productId: Long): ExternalPushOutcome {
        markDoneCalls += idToken to productId
        return onMarkDone(idToken, productId)
    }

    override suspend fun whoAmI(idToken: String, pseudo: String): Boolean {
        whoAmICalls += idToken to pseudo
        return onWhoAmI(idToken, pseudo)
    }
}
