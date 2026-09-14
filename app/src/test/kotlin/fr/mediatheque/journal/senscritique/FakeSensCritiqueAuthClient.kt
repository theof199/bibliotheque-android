package fr.mediatheque.journal.senscritique

/** Jumeau de `FakeExternalRatingService`, pour la connexion. */
class FakeSensCritiqueAuthClient(
    var onSignIn: suspend (String, String) -> SignInOutcome = { _, _ -> SignInOutcome.Unreachable },
) : SensCritiqueAuthClient {
    val signInCalls = mutableListOf<Pair<String, String>>()

    override suspend fun signIn(email: String, password: String): SignInOutcome {
        signInCalls += email to password
        return onSignIn(email, password)
    }
}
