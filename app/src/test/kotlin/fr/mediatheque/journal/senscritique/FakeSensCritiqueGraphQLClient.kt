package fr.mediatheque.journal.senscritique

/** Jumeau de `FakeExternalRatingService`, au niveau GraphQL — pour tester `FirebaseSensCritiqueAuthClient` sans réseau. */
class FakeSensCritiqueGraphQLClient(var onWhoAmI: suspend (String, String) -> Boolean = { _, _ -> true }) : SensCritiqueGraphQLClient {
    val whoAmICalls = mutableListOf<Pair<String, String>>()

    override suspend fun search(idToken: String, keywords: String) = ExternalSearchOutcome.Success(emptyList())
    override suspend fun rate(idToken: String, productId: Long, rating: Int) = ExternalPushOutcome.Success
    override suspend fun markDone(idToken: String, productId: Long) = ExternalPushOutcome.Success

    override suspend fun whoAmI(idToken: String, pseudo: String): Boolean {
        whoAmICalls += idToken to pseudo
        return onWhoAmI(idToken, pseudo)
    }
}
